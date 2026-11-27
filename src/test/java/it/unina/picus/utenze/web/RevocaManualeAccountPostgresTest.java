package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.api.UtenzeApi.AccountAmministrativo;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService;
import it.unina.picus.utenze.interno.applicazione.RecuperoPasswordService;
import it.unina.picus.utenze.interno.applicazione.RecuperoPasswordService.DatiInvioRecupero;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.ComandoRegistrazioneOpt;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.EsitoRegistrazioneOpt;
import it.unina.picus.utenze.interno.dominio.CodiceFiscale;

/*
Revoca manuale dell'account personale di un Operatore (#30, task #159) con la catena di Spring Security
reale e PostgreSQL reale. La revoca passa sempre dall'endpoint del Banco POST /api/operatori/{id}/revoca,
non da un update diretto sul database.

Gli Operatori nascono con il percorso di produzione: il Banco li crea con POST /api/utenze/operatori,
nelle due tipologie INTERNO_BANCO ed ESTERNO_PAGATO_BANCO, e il token di attivazione si legge dall'email
consegnata alla posta finta (il token non esce mai nella risposta HTTP). Gli Operatori attivi
scelgono la password con quel token. I link di recupero si emettono con RecuperoPasswordService, come
farebbe l'elaborazione asincrona di /api/auth/recupera.

PostaDiProva sostituisce InvioEmail e serve anche a dimostrare che dopo la revoca non parte nessuna email. È un
fake scritto a mano e non un mock di Mockito: registrare e verificare invocazioni sullo stesso mock da più thread
non è affidabile, mentre qui le email arrivano anche dagli esecutori in secondo piano e i test possono girare in
parallelo. Per lo stesso motivo la posta non viene mai azzerata: ogni verifica riguarda solo l'indirizzo, unico,
del proprio account.

Le richieste pubbliche di recupero e di nuovo link lavorano in secondo piano, ciascuna su un esecutore proprio.
Qui entrambi gli esecutori hanno un solo thread, quindi le richieste sono elaborate nell'ordine in cui arrivano:
quando arriva l'email della richiesta di controllo, accodata dopo quella per l'account revocato, anche
quest'ultima è finita e le verifiche sull'assenza di email non hanno bisogno di attese fisse. Con la posta finta
e questa proprietà il contesto è diverso da quello degli altri test di integrazione e Spring ne avvia uno in
più, sullo stesso container.

Gli attori dei dinieghi sono utenti registrati come in produzione: le OPT con RegistrazioneOptService, quindi
con profilo e codice fiscale, e gli Operatori con la creazione del Banco. Solo il Banco nasce direttamente sul
database, perché non ha un flusso di registrazione.

Un link "invalidato" si controlla sulla colonna usato_il e non solo con isTokenValido/isLinkValido, che
rifiutano già qualunque account non nello stato atteso.

Le schede trimestrali si inviano con l'API della #58 dalla sessione dell'Operatore e si leggono con le API
del Banco della #57, prima e dopo la revoca.

Ogni test usa email e codici fiscali unici e azzera il limitatore dei login: i test non dipendono
dall'ordine di esecuzione.
*/
@DisplayName("Revoca manuale dell'account di un Operatore su PostgreSQL (#30)")
@Import(RevocaManualeAccountPostgresTest.PostaDiProvaConfig.class)
@TestPropertySource(properties = {
        "picus.utenze.recupero.esecutore.thread=1",
        "picus.utenze.attivazione.reinvio.esecutore.thread=1"})
class RevocaManualeAccountPostgresTest extends IntegrazionePostgresBase {

    private static final String PASSWORD = "Password-di-prova-30";
    private static final String NUOVA_PASSWORD = "Password-nuova-di-prova-30";
    private static final String ERRORE_GENERICO = "{\"errore\":\"Credenziali non valide\"}";
    private static final String SCHEDE = "/api/monitoraggio/schede-operatore";
    private static final String ANNO = "2024";
    private static final String TRIMESTRE = "4";
    private static final Duration ATTESA_MASSIMA = Duration.ofSeconds(20);

    /** Scheda TM-OP-01 sintetica: i campi obbligatori del tracciato più qualche dato facoltativo. */
    private static final String SCHEDA_VALIDA = """
            {"nome":"Nome sintetico","cognome":"Cognome sintetico","qualifica":"Educatore",
             "ore":12.5,"costoOrarioLordo":20,"conteggi":{"minori":3,"famiglie":0}}
            """;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private LimitatoreAccessi limitatore;
    @Autowired
    private AttivazioneUtenzaService attivazione;
    @Autowired
    private RecuperoPasswordService recupero;
    @Autowired
    private RegistrazioneOptService registrazioneOpt;

    @Autowired
    private PostaDiProva posta;

    private MockHttpSession sessioneBanco;

    /* Operatore di prova: identificativo, email di login e token di attivazione ricevuto alla creazione. */
    private record Operatore(UUID id, String email, String tokenAttivazione) { }

    private record Account(UUID id, String email) { }

    @BeforeEach
    void prepara() throws Exception {
        limitatore.azzeraTutto();
        sessioneBanco = accedi(nuovoBanco().email());
    }

    //AC1 – revoca di un account attivo o in attesa di attivazione

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    @DisplayName("Operatore attivo: la revoca lo porta a REVOCATO e chiude tutte le sue sessioni aperte")
    void revocaOperatoreAttivo_chiudeLeSessioniAperte(String tipologia) throws Exception {
        Operatore operatore = operatoreAttivo(tipologia);
        MockHttpSession primaSessione = accedi(operatore.email());
        MockHttpSession secondaSessione = accedi(operatore.email());
        assertEquals(200, statoDi(get(SCHEDE + "/mie"), primaSessione));
        assertEquals(200, statoDi(get("/operatore/dashboard"), secondaSessione));

        revocaDalBanco(operatore.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(operatore.id().toString()))
                .andExpect(jsonPath("$.stato").value("REVOCATO"));

        assertEquals("REVOCATO", statoAccount(operatore.id()));
        // API: 401 senza dati e sessione invalidata.
        mvc.perform(get(SCHEDE + "/mie").session(primaSessione))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
        assertTrue(primaSessione.isInvalid());
        // Pagina HTML: rimando al login e sessione invalidata.
        mvc.perform(get("/operatore/dashboard").session(secondaSessione))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"));
        assertTrue(secondaSessione.isInvalid());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    @DisplayName("Operatore attivo: la revoca invalida il link di recupero password ancora valido")
    void revocaOperatoreAttivo_invalidaIlLinkDiRecupero(String tipologia) throws Exception {
        Operatore operatore = operatoreAttivo(tipologia);
        DatiInvioRecupero link = linkDiRecuperoConsegnato(operatore.email());
        assertTrue(recupero.isLinkValido(link.tokenInChiaro()));
        assertEquals(1, linkDiRecuperoAperti(operatore.id()));

        revocaDalBanco(operatore.id()).andExpect(status().isOk());

        assertNotNull(jdbc.queryForObject(
                "select usato_il from utenze_token_recupero_password where id = ?", Object.class, link.idLink()));
        assertEquals(0, linkDiRecuperoAperti(operatore.id()));
        assertFalse(recupero.isLinkValido(link.tokenInChiaro()));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    @DisplayName("Operatore in attesa di attivazione: la revoca lo porta a REVOCATO e invalida il link di attivazione")
    void revocaOperatoreInAttesa_invalidaIlLinkDiAttivazione(String tipologia) throws Exception {
        Operatore operatore = operatoreInAttesa(tipologia);
        assertEquals("IN_ATTESA_DI_ATTIVAZIONE", statoAccount(operatore.id()));
        assertTrue(attivazione.isTokenValido(operatore.tokenAttivazione()));
        assertEquals(1, linkDiAttivazioneAperti(operatore.id()));

        revocaDalBanco(operatore.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("REVOCATO"));

        assertEquals("REVOCATO", statoAccount(operatore.id()));
        assertEquals(0, linkDiAttivazioneAperti(operatore.id()));
        assertFalse(attivazione.isTokenValido(operatore.tokenAttivazione()));
    }

    //AC2 – accesso negato dopo la revoca

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    @DisplayName("Dopo la revoca il login è negato con lo stesso esito di credenziali errate e senza sessione autenticata")
    void dopoLaRevoca_loginNegato(String tipologia) throws Exception {
        Operatore operatore = operatoreAttivo(tipologia);
        revocaDalBanco(operatore.id()).andExpect(status().isOk());

        MvcResult esito = login(operatore.email(), PASSWORD);

        assertEquals(401, esito.getResponse().getStatus());
        assertEquals(ERRORE_GENERICO, esito.getResponse().getContentAsString());
        assertFalse(autenticata((MockHttpSession) esito.getRequest().getSession(false)));
    }

    @Test
    @DisplayName("Dopo la revoca una sessione aperta prima non è riutilizzabile, con o senza token CSRF, e non invia schede")
    void dopoLaRevoca_sessionePrecedenteNonRiutilizzabile() throws Exception {
        Operatore operatore = operatoreAttivo("ESTERNO_PAGATO_BANCO");
        MockHttpSession conToken = accedi(operatore.email());
        MockHttpSession senzaToken = accedi(operatore.email());

        revocaDalBanco(operatore.id()).andExpect(status().isOk());

        mvc.perform(invioScheda().with(csrf()).session(conToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(invioScheda().session(senzaToken))
                .andExpect(status().isUnauthorized());
        assertEquals(0, schedeDi(operatore.id()));
        // Anche il secondo tentativo con la stessa sessione resta negato.
        assertEquals(401, statoDi(get(SCHEDE + "/mie"), conToken));
    }

    @Test
    @DisplayName("Dopo la revoca il vecchio link di attivazione non riattiva l'account né imposta una password")
    void dopoLaRevoca_linkDiAttivazioneNonRiattiva() throws Exception {
        Operatore operatore = operatoreInAttesa("INTERNO_BANCO");
        // L'Operatore nasce con una password casuale che nessuno conosce: il link non deve sostituirla.
        String hashPrima = hashPassword(operatore.id());
        revocaDalBanco(operatore.id()).andExpect(status().isOk());

        mvc.perform(get("/attiva").param("token", operatore.tokenAttivazione()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("valido", false));
        mvc.perform(post("/api/auth/attiva/conferma").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoConToken(operatore.tokenAttivazione(), NUOVA_PASSWORD)))
                .andExpect(status().isBadRequest());

        assertEquals("REVOCATO", statoAccount(operatore.id()));
        assertEquals(hashPrima, hashPassword(operatore.id()));
        assertEquals(401, login(operatore.email(), NUOVA_PASSWORD).getResponse().getStatus());
    }

    @Test
    @DisplayName("Dopo la revoca il vecchio link di recupero non cambia la password e non dà accesso")
    void dopoLaRevoca_linkDiRecuperoNonCambiaLaPassword() throws Exception {
        Operatore operatore = operatoreAttivo("INTERNO_BANCO");
        DatiInvioRecupero link = linkDiRecuperoConsegnato(operatore.email());
        String hashPrima = hashPassword(operatore.id());
        revocaDalBanco(operatore.id()).andExpect(status().isOk());

        mvc.perform(get("/recupera").param("token", link.tokenInChiaro()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("linkNonValido", true))
                .andExpect(model().attribute("cambioPassword", false));
        mvc.perform(post("/api/auth/recupera/conferma").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoConToken(link.tokenInChiaro(), NUOVA_PASSWORD)))
                .andExpect(status().isGone());

        assertEquals(hashPrima, hashPassword(operatore.id()));
        assertEquals("REVOCATO", statoAccount(operatore.id()));
        assertEquals(401, login(operatore.email(), NUOVA_PASSWORD).getResponse().getStatus());
    }

    //AC3 – nessun nuovo link e nessuna email dopo la revoca

    @Test
    @DisplayName("Dopo la revoca il Banco non può reinviare l'invito: 409, nessun nuovo link e nessuna email")
    void dopoLaRevoca_reinvioDelBancoRifiutatoSenzaEmail() throws Exception {
        Operatore operatore = operatoreInAttesa("ESTERNO_PAGATO_BANCO");
        revocaDalBanco(operatore.id()).andExpect(status().isOk());
        int tokenPrima = linkDiAttivazioneTotali(operatore.id());

        mvc.perform(post("/api/operatori/" + operatore.id() + "/reinvia-invito").session(sessioneBanco).with(csrf()))
                .andExpect(status().isConflict());

        assertEquals(tokenPrima, linkDiAttivazioneTotali(operatore.id()));
        assertEquals(0, linkDiAttivazioneAperti(operatore.id()));
        assertEquals("REVOCATO", statoAccount(operatore.id()));
        // L'unica email di attivazione resta quella della creazione.
        assertEquals(1, posta.inviateA(operatore.email(), Tipo.ATTIVAZIONE).size());
    }

    @ParameterizedTest(name = "revocato da {0}")
    @ValueSource(strings = {"ATTIVO", "IN_ATTESA_DI_ATTIVAZIONE"})
    @DisplayName("Dopo la revoca le richieste pubbliche di recupero e di nuovo link non inviano nulla")
    void dopoLaRevoca_richiestePubblicheNonInvianoLink(String statoPrimaDellaRevoca) throws Exception {
        Operatore revocato = "ATTIVO".equals(statoPrimaDellaRevoca)
                ? operatoreAttivo("INTERNO_BANCO")
                : operatoreInAttesa("ESTERNO_PAGATO_BANCO");
        // Account di controllo: le loro richieste, accodate dopo quelle per l'account revocato, segnalano che
        // l'esecutore a un solo thread ha già finito di elaborare anche le precedenti.
        Operatore controlloAttivo = operatoreAttivo("INTERNO_BANCO");
        Operatore controlloInAttesa = operatoreInAttesa("INTERNO_BANCO");
        revocaDalBanco(revocato.id()).andExpect(status().isOk());
        int attivazionePrima = linkDiAttivazioneTotali(revocato.id());
        int recuperoPrima = linkDiRecuperoTotali(revocato.id());
        // Il controllo in attesa ha già l'email della creazione: si aspetta quella successiva.
        int recuperoControllo = posta.inviateA(controlloAttivo.email(), Tipo.RECUPERO).size();
        int attivazioneControllo = posta.inviateA(controlloInAttesa.email(), Tipo.ATTIVAZIONE).size();

        // La risposta pubblica è la stessa per l'account revocato e per quelli idonei. L'ordine conta: prima la
        // richiesta per l'account revocato, poi quella di controllo sullo stesso esecutore.
        String rispostaRecupero = richiestaPubblica("/api/auth/recupera", revocato.email());
        assertEquals(rispostaRecupero, richiestaPubblica("/api/auth/recupera", controlloAttivo.email()));
        String rispostaReinvio = richiestaPubblica("/api/auth/attiva/reinvio", revocato.email());
        assertEquals(rispostaReinvio, richiestaPubblica("/api/auth/attiva/reinvio", controlloInAttesa.email()));

        posta.attendi(controlloAttivo.email(), Tipo.RECUPERO, recuperoControllo + 1, ATTESA_MASSIMA);
        posta.attendi(controlloInAttesa.email(), Tipo.ATTIVAZIONE, attivazioneControllo + 1, ATTESA_MASSIMA);
        assertEquals(List.of(), posta.inviateA(revocato.email(), Tipo.RECUPERO));
        // L'unica email di attivazione resta quella della creazione.
        assertEquals(1, posta.inviateA(revocato.email(), Tipo.ATTIVAZIONE).size());

        // Nessun link creato, neanche chiedendolo direttamente ai servizi che quelle richieste usano.
        assertTrue(recupero.preparaLink(revocato.email()).isEmpty());
        assertTrue(attivazione.preparaTokenPerEmail(revocato.email()).isEmpty());
        assertEquals(attivazionePrima, linkDiAttivazioneTotali(revocato.id()));
        assertEquals(recuperoPrima, linkDiRecuperoTotali(revocato.id()));
        assertEquals("REVOCATO", statoAccount(revocato.id()));
    }

    //AC4 – schede inviate prima della revoca disponibili e invariate per il Banco

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    @DisplayName("La scheda inviata prima della revoca resta consultabile dal Banco e invariata")
    void schedaInviataPrimaDellaRevoca_restaConsultabileEInvariata(String tipologia) throws Exception {
        Operatore operatore = operatoreAttivo(tipologia);
        UUID schedaId = inviaScheda(accedi(operatore.email()));
        String dettaglioPrima = dettaglioSchedaPerIlBanco(schedaId);
        Map<String, Object> rigaPrima = riga("select * from monitoraggio_schede_operatore where id = ?", schedaId);
        List<Map<String, Object>> conteggiPrima = conteggiDellaScheda(schedaId);

        revocaDalBanco(operatore.id()).andExpect(status().isOk());

        // Dettaglio (#57): stessa risposta di prima della revoca.
        assertEquals(dettaglioPrima, dettaglioSchedaPerIlBanco(schedaId));
        assertEquals(operatore.id().toString(), JsonPath.read(dettaglioPrima, "$.sintesi.operatoreId"));
        // Elenco e riepilogo del trimestre la includono ancora.
        String ricevute = mvc.perform(get(SCHEDE + "/ricevute").param("anno", ANNO).param("trimestre", TRIMESTRE)
                        .session(sessioneBanco))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> idRicevute = JsonPath.read(ricevute, "$[*].id");
        assertTrue(idRicevute.contains(schedaId.toString()), "scheda dell'Operatore revocato assente dalle ricevute");
        String riepilogo = mvc.perform(get(SCHEDE + "/ricevute/riepilogo").param("anno", ANNO)
                        .param("trimestre", TRIMESTRE).session(sessioneBanco))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> idRiepilogo = JsonPath.read(riepilogo, "$.schede[*].id");
        assertTrue(idRiepilogo.contains(schedaId.toString()), "scheda dell'Operatore revocato assente dal riepilogo");
        // Database: scheda e conteggi non cambiano.
        assertEquals(rigaPrima, riga("select * from monitoraggio_schede_operatore where id = ?", schedaId));
        assertEquals(conteggiPrima, conteggiDellaScheda(schedaId));
    }

    @Test
    @DisplayName("Profilo e account dell'Operatore restano invariati, tranne lo stato; resta nell'elenco Account del Banco")
    void profiloEAccountInvariati_eOperatoreNellElencoDelBanco() throws Exception {
        Operatore operatore = operatoreAttivo("ESTERNO_PAGATO_BANCO");
        Map<String, Object> profiloPrima =
                riga("select * from utenze_profili_operatore where account_id = ?", operatore.id());
        Map<String, Object> accountPrima = riga("select * from utenze_account where id = ?", operatore.id());

        revocaDalBanco(operatore.id()).andExpect(status().isOk());

        assertEquals(profiloPrima, riga("select * from utenze_profili_operatore where account_id = ?", operatore.id()));
        Map<String, Object> accountDopo = riga("select * from utenze_account where id = ?", operatore.id());
        assertEquals("REVOCATO", accountDopo.get("stato"));
        accountPrima.remove("stato");
        accountDopo.remove("stato");
        assertEquals(accountPrima, accountDopo);

        MvcResult pagina = mvc.perform(get("/banco/dashboard").session(sessioneBanco))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        List<AccountAmministrativo> elenco =
                (List<AccountAmministrativo>) pagina.getModelAndView().getModel().get("operatoriElenco");
        AccountAmministrativo voce = elenco.stream()
                .filter(a -> a.id().equals(operatore.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Operatore revocato assente dall'elenco Account"));
        assertEquals("REVOCATO", voce.stato().name());
        assertEquals(operatore.email(), voce.email());
        assertEquals("ESTERNO_PAGATO_BANCO", voce.riferimento());
        assertEquals("napoli", voce.comuneCodice());
    }

    //AC5 – revoca negata a OPT e Operatori, senza modifiche

    @Test
    @DisplayName("OPT e Operatori non possono revocare un Operatore: 403 (401 senza sessione) e nessuna modifica")
    void revocaSenzaPermessoBanco_bloccataSenzaModifiche() throws Exception {
        Operatore bersaglio = operatoreAttivo("INTERNO_BANCO");
        MockHttpSession sessioneBersaglio = accedi(bersaglio.email());
        DatiInvioRecupero link = linkDiRecuperoConsegnato(bersaglio.email());
        Operatore inAttesa = operatoreInAttesa("ESTERNO_PAGATO_BANCO");
        String revoca = "/api/operatori/" + bersaglio.id() + "/revoca";

        Map<String, MockHttpServletRequestBuilder> tentativi = new LinkedHashMap<>();
        tentativi.put("senza sessione", post(revoca).with(csrf()));
        tentativi.put("OPT", post(revoca).session(accedi(optAttiva().email())).with(csrf()));
        tentativi.put("Operatore stesso", post(revoca).session(accedi(bersaglio.email())).with(csrf()));
        tentativi.put("altro Operatore",
                post(revoca).session(accedi(operatoreAttivo("ESTERNO_PAGATO_BANCO").email())).with(csrf()));
        tentativi.put("Banco senza token CSRF", post(revoca).session(sessioneBanco));
        tentativi.put("OPT su Operatore in attesa", post("/api/operatori/" + inAttesa.id() + "/revoca")
                .session(accedi(optAttiva().email())).with(csrf()));
        tentativi.put("Operatore su Operatore in attesa", post("/api/operatori/" + inAttesa.id() + "/revoca")
                .session(accedi(operatoreAttivo("INTERNO_BANCO").email())).with(csrf()));

        Map<String, Integer> attesi = Map.of(
                "senza sessione", 401,
                "OPT", 403,
                "Operatore stesso", 403,
                "altro Operatore", 403,
                "Banco senza token CSRF", 403,
                "OPT su Operatore in attesa", 403,
                "Operatore su Operatore in attesa", 403);

        for (Map.Entry<String, MockHttpServletRequestBuilder> tentativo : tentativi.entrySet()) {
            int stato = mvc.perform(tentativo.getValue()).andReturn().getResponse().getStatus();
            assertEquals(attesi.get(tentativo.getKey()), stato, tentativo.getKey());
        }

        // Nessuna modifica: stati invariati, sessione ancora valida, link ancora utilizzabili.
        assertEquals("ATTIVO", statoAccount(bersaglio.id()));
        assertEquals("IN_ATTESA_DI_ATTIVAZIONE", statoAccount(inAttesa.id()));
        assertEquals(200, statoDi(get(SCHEDE + "/mie"), sessioneBersaglio));
        assertFalse(sessioneBersaglio.isInvalid());
        assertTrue(recupero.isLinkValido(link.tokenInChiaro()));
        assertTrue(attivazione.isTokenValido(inAttesa.tokenAttivazione()));
        assertEquals(204, login(bersaglio.email(), PASSWORD).getResponse().getStatus());
    }

    @Test
    @DisplayName("Il Banco non può revocare come Operatore un account di altro ruolo, inesistente o già revocato")
    void revocaDelBancoSuBersaglioNonValido_rifiutataSenzaModifiche() throws Exception {
        Account opt = optAttiva();
        Account banco = nuovoBanco();
        Operatore giaRevocato = operatoreAttivo("INTERNO_BANCO");
        revocaDalBanco(giaRevocato.id()).andExpect(status().isOk());

        revocaDalBanco(opt.id()).andExpect(status().isConflict());
        revocaDalBanco(banco.id()).andExpect(status().isConflict());
        revocaDalBanco(giaRevocato.id()).andExpect(status().isConflict());
        revocaDalBanco(UUID.randomUUID()).andExpect(status().isNotFound());
        mvc.perform(post("/api/operatori/non-un-uuid/revoca").session(sessioneBanco).with(csrf()))
                .andExpect(status().isBadRequest());

        assertEquals("ATTIVO", statoAccount(opt.id()));
        assertEquals("ATTIVO", statoAccount(banco.id()));
        assertEquals("REVOCATO", statoAccount(giaRevocato.id()));
        assertEquals(204, login(opt.email(), PASSWORD).getResponse().getStatus());
    }

    //helper

    /** Operatore creato dal Banco con l'endpoint di produzione, ancora in attesa di attivazione. */
    private Operatore operatoreInAttesa(String tipologia) throws Exception {
        String email = "op30-" + UUID.randomUUID() + "@picus.test";
        String corpo = """
                {"nome":"Operatore","cognome":"Revoca","codiceFiscale":"%s","email":"%s",
                 "telefono":"+39 081 0000000","comuneCodice":"napoli","tipologia":"%s",
                 "misure":[%d],"centroPicus":false}
                """.formatted(codiceFiscaleOperatore(), email, tipologia, misuraAttiva());
        String risposta = mvc.perform(post("/api/utenze/operatori").session(sessioneBanco).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stato").value("IN_ATTESA_DI_ATTIVAZIONE"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(risposta, "$.id"));

        // La creazione consegna il link prima di rispondere: l'email è già nella posta.
        List<PostaDiProva.Email> inviate = posta.inviateA(email, Tipo.ATTIVAZIONE);
        assertEquals(1, inviate.size(), "email di attivazione per " + email);
        return new Operatore(id, email, inviate.get(0).token());
    }

    /* Operatore creato dal Banco e attivato con il proprio link, con password {@link #PASSWORD}. */
    private Operatore operatoreAttivo(String tipologia) throws Exception {
        Operatore operatore = operatoreInAttesa(tipologia);
        attivazione.attivaAccount(operatore.tokenAttivazione(), PASSWORD);
        assertEquals("ATTIVO", statoAccount(operatore.id()));
        return operatore;
    }

    /* OPT registrata come in produzione, con profilo e codice fiscale, e attivata con password {@link #PASSWORD}. */
    private Account optAttiva() {
        String suffisso = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        String email = "opt-rev30-" + UUID.randomUUID() + "@picus.test";
        EsitoRegistrazioneOpt esito = registrazioneOpt.registraOpt(new ComandoRegistrazioneOpt(
                "OPT30-" + suffisso, "OPT Revoca " + suffisso, "napoli", codiceFiscaleDiProva(), email));
        attivazione.attivaAccount(esito.datiInvio().tokenInChiaro(), PASSWORD);
        assertEquals("ATTIVO", statoAccount(esito.accountId()));
        return new Account(esito.accountId(), email);
    }

    /* Account Banco attivo creato direttamente sul database: il Banco non ha un flusso di registrazione. */
    private Account nuovoBanco() {
        UUID id = UUID.randomUUID();
        String email = "banco-rev30-" + id + "@picus.test";
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, ?, ?, 'BANCO', 'ATTIVO')
                """, id, email, encoder.encode(PASSWORD), "Utente Banco");
        return new Account(id, email);
    }

    /* Una misura attiva del catalogo, letta al momento: altri test possono disattivarne alcune. */
    private long misuraAttiva() {
        return jdbc.queryForObject("select min(id) from catalogo_misure where attiva", Long.class);
    }

    /*
     Codice fiscale sintetico casuale: il profilo Operatore lo richiede valido e unico. Il carattere di controllo
     è quello che {@link CodiceFiscale} accetta, così il test non ricopia l'algoritmo.
    */
    private static String codiceFiscaleOperatore() {
        ThreadLocalRandom caso = ThreadLocalRandom.current();
        StringBuilder codice = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            codice.append((char) ('A' + caso.nextInt(26)));
        }
        codice.append(String.format(Locale.ROOT, "%02d", caso.nextInt(100)));
        codice.append("ABCDEHLMPRST".charAt(caso.nextInt(12)));
        codice.append(String.format(Locale.ROOT, "%02d", 1 + caso.nextInt(28)));
        codice.append((char) ('A' + caso.nextInt(26)));
        codice.append(String.format(Locale.ROOT, "%03d", caso.nextInt(1000)));
        for (char controllo = 'A'; controllo <= 'Z'; controllo++) {
            String candidato = codice.toString() + controllo;
            if (CodiceFiscale.valido(candidato)) {
                return candidato;
            }
        }
        throw new IllegalStateException("Nessun carattere di controllo valido per " + codice);
    }

    /* Emette un link di recupero come l'elaborazione di /api/auth/recupera e ne conferma la consegna. */
    private DatiInvioRecupero linkDiRecuperoConsegnato(String email) {
        DatiInvioRecupero link = recupero.preparaLink(email).orElseThrow();
        recupero.confermaConsegna(link.idLink());
        return link;
    }

    private ResultActions revocaDalBanco(UUID operatoreId) throws Exception {
        return mvc.perform(post("/api/operatori/" + operatoreId + "/revoca").session(sessioneBanco).with(csrf()));
    }

    private MockHttpServletRequestBuilder invioScheda() {
        return post(SCHEDE).param("anno", ANNO).param("trimestre", TRIMESTRE)
                .contentType(MediaType.APPLICATION_JSON).content(SCHEDA_VALIDA);
    }

    /* Primo invio della scheda (#58) dalla sessione dell'Operatore, con esito 201 atteso. */
    private UUID inviaScheda(MockHttpSession sessioneOperatore) throws Exception {
        String corpo = mvc.perform(invioScheda().with(csrf()).session(sessioneOperatore))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(corpo, "$.id"));
    }

    /* Dettaglio della scheda letto dal Banco (#57), con esito 200 atteso. */
    private String dettaglioSchedaPerIlBanco(UUID schedaId) throws Exception {
        return mvc.perform(get(SCHEDE + "/" + schedaId).session(sessioneBanco))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /* Richiesta pubblica senza sessione che accoda il lavoro e risponde 200 per ogni indirizzo. */
    private String richiestaPubblica(String percorso, String email) throws Exception {
        return mvc.perform(post(percorso).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    /* Login con {@link #PASSWORD}, con esito 204 atteso. */
    private MockHttpSession accedi(String email) throws Exception {
        MvcResult esito = login(email, PASSWORD);
        assertEquals(204, esito.getResponse().getStatus(), "login di " + email);
        MockHttpSession sessione = (MockHttpSession) esito.getRequest().getSession(false);
        assertNotNull(sessione);
        return sessione;
    }

    private int statoDi(MockHttpServletRequestBuilder richiesta, MockHttpSession sessione) throws Exception {
        return mvc.perform(richiesta.session(sessione)).andReturn().getResponse().getStatus();
    }

    private String statoAccount(UUID id) {
        return jdbc.queryForObject("select stato from utenze_account where id = ?", String.class, id);
    }

    private String hashPassword(UUID id) {
        return jdbc.queryForObject("select password_hash from utenze_account where id = ?", String.class, id);
    }

    private int schedeDi(UUID operatoreId) {
        return jdbc.queryForObject(
                "select count(*) from monitoraggio_schede_operatore where operatore_id = ?", Integer.class, operatoreId);
    }

    private List<Map<String, Object>> conteggiDellaScheda(UUID schedaId) {
        return jdbc.queryForList(
                "select categoria, valore from monitoraggio_conteggi_scheda where scheda_id = ? order by categoria",
                schedaId);
    }

    private int linkDiAttivazioneAperti(UUID accountId) {
        return jdbc.queryForObject(
                "select count(*) from utenze_token_attivazione where account_id = ? and usato_il is null",
                Integer.class, accountId);
    }

    private int linkDiAttivazioneTotali(UUID accountId) {
        return jdbc.queryForObject(
                "select count(*) from utenze_token_attivazione where account_id = ?", Integer.class, accountId);
    }

    private int linkDiRecuperoAperti(UUID accountId) {
        return jdbc.queryForObject(
                "select count(*) from utenze_token_recupero_password where account_id = ? and usato_il is null",
                Integer.class, accountId);
    }

    private int linkDiRecuperoTotali(UUID accountId) {
        return jdbc.queryForObject(
                "select count(*) from utenze_token_recupero_password where account_id = ?", Integer.class, accountId);
    }

    private Map<String, Object> riga(String sql, UUID id) {
        return new LinkedHashMap<>(jdbc.queryForMap(sql, id));
    }

    private static String corpoConToken(String token, String password) {
        return "{\"token\":\"" + token + "\",\"nuovaPassword\":\"" + password + "\"}";
    }

    private static boolean autenticata(MockHttpSession sessione) {
        return sessione != null && !sessione.isInvalid()
                && sessione.getAttribute(SPRING_SECURITY_CONTEXT_KEY) != null;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PostaDiProvaConfig {

        /* Prevale sull'adattatore di InvioEmail scelto da ConfigurazioneInvioEmail. */
        @Bean
        @Primary
        PostaDiProva postaDiProva() {
            return new PostaDiProva();
        }
    }

    enum Tipo { ATTIVAZIONE, RECUPERO }

    /* Fake manuale e thread-safe della posta: registra le email consegnate, con il token in chiaro. */
    static final class PostaDiProva implements InvioEmail {

        record Email(Tipo tipo, String destinatario, String token) { }

        private final List<Email> inviate = new CopyOnWriteArrayList<>();

        @Override
        public void inviaLinkAttivazione(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
            registra(new Email(Tipo.ATTIVAZIONE, emailDestinatario, tokenInChiaro));
        }

        @Override
        public void inviaLinkResetPassword(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
            registra(new Email(Tipo.RECUPERO, emailDestinatario, tokenInChiaro));
        }

        private synchronized void registra(Email email) {
            inviate.add(email);
            notifyAll();
        }

        List<Email> inviateA(String indirizzo, Tipo tipo) {
            return inviate.stream().filter(e -> e.tipo() == tipo && e.destinatario().equals(indirizzo)).toList();
        }

        /* Aspetta che l'indirizzo abbia ricevuto almeno {@code quante} email del tipo indicato, senza attese fisse. */
        synchronized void attendi(String indirizzo, Tipo tipo, int quante, Duration massimo)
                throws InterruptedException {
            long limite = System.nanoTime() + massimo.toNanos();
            while (inviateA(indirizzo, tipo).size() < quante) {
                long resta = limite - System.nanoTime();
                if (resta <= 0) {
                    throw new AssertionError("Meno di " + quante + " email " + tipo + " per " + indirizzo
                            + " entro " + massimo);
                }
                TimeUnit.NANOSECONDS.timedWait(this, resta);
            }
        }
    }
}