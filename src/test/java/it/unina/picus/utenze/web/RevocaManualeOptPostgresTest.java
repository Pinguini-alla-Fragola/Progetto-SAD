package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.utenze.api.UtenzeApi.AccountAmministrativo;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService;
import it.unina.picus.utenze.interno.applicazione.RecuperoPasswordService;
import it.unina.picus.utenze.interno.applicazione.RecuperoPasswordService.DatiInvioRecupero;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.ComandoRegistrazioneOpt;
import it.unina.picus.utenze.interno.applicazione.RegistrazioneOptService.EsitoRegistrazioneOpt;

/*
Revoca manuale dell'utenza di una OPT (#26) con la catena di Spring Security reale e PostgreSQL reale.
La revoca passa sempre dall'endpoint del Banco POST /api/opt/{id}/revoca, non da un update diretto sul
database: si verifica il caso d'uso completo, dal permesso di ruolo fino all'invalidazione di sessioni
e link.

Le OPT nascono con il percorso di produzione: registrazione (RegistrazioneOptService) e, per quelle
attive, attivazione con il token ricevuto. I link di recupero si emettono con RecuperoPasswordService,
come farebbe l'elaborazione asincrona di /api/auth/recupera, così il test non dipende dai tempi
dell'esecutore.

Un link "invalidato" si controlla sulla colonna usato e non solo con isTokenValido/isLinkValido:
quei metodi rifiutano già qualunque account non nello stato atteso, quindi da soli non dimostrerebbero
che la revoca ha chiuso davvero i link.

Ogni test usa email e codici struttura unici e azzera il limitatore dei login, così i test non
dipendono dall'ordine. La classe non aggiunge @MockitoBean: riusa il contesto condiviso della suite.
*/
@DisplayName("Revoca manuale dell'utenza di una OPT su PostgreSQL (#26)")
class RevocaManualeOptPostgresTest extends IntegrazionePostgresBase {

    private static final String PASSWORD = "Password-di-prova-26";
    private static final String NUOVA_PASSWORD = "Password-nuova-di-prova-26";
    private static final String ERRORE_GENERICO = "{\"errore\":\"Credenziali non valide\"}";

    /** Una richiesta che una OPT con profilo può davvero salvare (201): misura e comune del catalogo iniziale. */
    private static final String RICHIESTA_VALIDA =
            "{\"misuraId\":1,\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"napoli\",\"description\":\"Richiesta prima della revoca\"}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private LimitatoreAccessi limitatore;
    @Autowired
    private RegistrazioneOptService registrazione;
    @Autowired
    private AttivazioneUtenzaService attivazione;
    @Autowired
    private RecuperoPasswordService recupero;

    private MockHttpSession sessioneBanco;

    /* OPT di prova: identificativo, email di login e token di attivazione emesso alla registrazione. */
    private record Opt(UUID id, String email, String tokenAttivazione) { }

    @BeforeEach
    void prepara() throws Exception {
        limitatore.azzeraTutto();
        sessioneBanco = accedi(nuovoAccountDiretto("BANCO").email());
    }

    //AC1 – revoca di un account attivo o in attesa di attivazione

    @Test
    @DisplayName("OPT attiva: la revoca la porta a REVOCATO e chiude tutte le sue sessioni aperte")
    void revocaOptAttiva_chiudeLeSessioniAperte() throws Exception {
        Opt opt = optAttiva();
        MockHttpSession primaSessione = accedi(opt.email());
        MockHttpSession secondaSessione = accedi(opt.email());
        assertEquals(200, statoDi(get("/api/richieste/mie"), primaSessione));
        assertEquals(200, statoDi(get("/api/richieste/mie"), secondaSessione));

        revocaDalBanco(opt.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(opt.id().toString()))
                .andExpect(jsonPath("$.stato").value("REVOCATO"));

        assertEquals("REVOCATO", statoAccount(opt.id()));
        // API: 401 senza dati e sessione invalidata.
        mvc.perform(get("/api/richieste/mie").session(primaSessione))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
        assertTrue(primaSessione.isInvalid());
        // Pagina HTML: rimando al login e sessione invalidata.
        mvc.perform(get("/opt/dashboard").session(secondaSessione))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"));
        assertTrue(secondaSessione.isInvalid());
    }

    @Test
    @DisplayName("OPT attiva: la revoca invalida il link di recupero password ancora valido")
    void revocaOptAttiva_invalidaIlLinkDiRecupero() throws Exception {
        Opt opt = optAttiva();
        DatiInvioRecupero link = linkDiRecuperoConsegnato(opt.email());
        assertTrue(recupero.isLinkValido(link.tokenInChiaro()));
        assertEquals(1, linkDiRecuperoAperti(opt.id()));

        revocaDalBanco(opt.id()).andExpect(status().isOk());

        assertNotNull(jdbc.queryForObject(
                "select usato_il from utenze_token_recupero_password where id = ?", Object.class, link.idLink()));
        assertEquals(0, linkDiRecuperoAperti(opt.id()));
        assertFalse(recupero.isLinkValido(link.tokenInChiaro()));
    }

    @Test
    @DisplayName("OPT in attesa di attivazione: la revoca la porta a REVOCATO e invalida i link di attivazione")
    void revocaOptInAttesa_invalidaILinkDiAttivazione() throws Exception {
        Opt opt = optInAttesa();
        assertEquals("IN_ATTESA_DI_ATTIVAZIONE", statoAccount(opt.id()));
        assertTrue(attivazione.isTokenValido(opt.tokenAttivazione()));
        assertEquals(1, linkDiAttivazioneAperti(opt.id()));

        revocaDalBanco(opt.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("REVOCATO"));

        assertEquals("REVOCATO", statoAccount(opt.id()));
        assertEquals(0, linkDiAttivazioneAperti(opt.id()));
        assertFalse(attivazione.isTokenValido(opt.tokenAttivazione()));
    }

    //AC2 – nessun accesso e nessuna riattivazione dopo la revoca

    @Test
    @DisplayName("Dopo la revoca il login è negato con lo stesso esito di credenziali errate e senza sessione autenticata")
    void dopoLaRevoca_loginNegato() throws Exception {
        Opt opt = optAttiva();
        revocaDalBanco(opt.id()).andExpect(status().isOk());

        MvcResult esito = login(opt.email(), PASSWORD);

        assertEquals(401, esito.getResponse().getStatus());
        assertEquals(ERRORE_GENERICO, esito.getResponse().getContentAsString());
        assertFalse(autenticata((MockHttpSession) esito.getRequest().getSession(false)));
    }

    @Test
    @DisplayName("Dopo la revoca una sessione aperta prima non è riutilizzabile, con o senza token CSRF, e non salva richieste")
    void dopoLaRevoca_sessionePrecedenteNonRiutilizzabile() throws Exception {
        Opt opt = optAttiva();
        MockHttpSession conToken = accedi(opt.email());
        MockHttpSession senzaToken = accedi(opt.email());
        int richiestePrima = richiesteDi(opt.id());

        revocaDalBanco(opt.id()).andExpect(status().isOk());

        mvc.perform(post("/api/richieste").with(csrf()).session(conToken)
                        .contentType(MediaType.APPLICATION_JSON).content(RICHIESTA_VALIDA))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/richieste").session(senzaToken)
                        .contentType(MediaType.APPLICATION_JSON).content(RICHIESTA_VALIDA))
                .andExpect(status().isUnauthorized());
        assertEquals(richiestePrima, richiesteDi(opt.id()));
        // Anche il secondo tentativo con la stessa sessione resta negato.
        assertEquals(401, statoDi(get("/api/richieste/mie"), conToken));
    }

    @Test
    @DisplayName("Dopo la revoca il vecchio link di attivazione non riattiva l'account né imposta una password")
    void dopoLaRevoca_linkDiAttivazioneNonRiattiva() throws Exception {
        Opt opt = optInAttesa();
        revocaDalBanco(opt.id()).andExpect(status().isOk());

        mvc.perform(get("/attiva").param("token", opt.tokenAttivazione()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("valido", false));
        mvc.perform(post("/api/auth/attiva/conferma").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoConToken(opt.tokenAttivazione(), NUOVA_PASSWORD)))
                .andExpect(status().isBadRequest());

        assertEquals("REVOCATO", statoAccount(opt.id()));
        assertNull(jdbc.queryForObject(
                "select password_hash from utenze_account where id = ?", String.class, opt.id()));
        assertEquals(401, login(opt.email(), NUOVA_PASSWORD).getResponse().getStatus());
    }

    @Test
    @DisplayName("Dopo la revoca il Banco non può reinviare l'invito: nessun nuovo link di attivazione")
    void dopoLaRevoca_reinvioInvitoRifiutato() throws Exception {
        Opt opt = optInAttesa();
        revocaDalBanco(opt.id()).andExpect(status().isOk());
        int tokenPrima = linkDiAttivazioneTotali(opt.id());

        mvc.perform(post("/api/opt/" + opt.id() + "/reinvia-invito").session(sessioneBanco).with(csrf()))
                .andExpect(status().isConflict());

        assertEquals(tokenPrima, linkDiAttivazioneTotali(opt.id()));
        assertEquals(0, linkDiAttivazioneAperti(opt.id()));
        assertEquals("REVOCATO", statoAccount(opt.id()));
    }

    @Test
    @DisplayName("Dopo la revoca il vecchio link di recupero non cambia la password e non se ne possono emettere di nuovi")
    void dopoLaRevoca_linkDiRecuperoNonRiattiva() throws Exception {
        Opt opt = optAttiva();
        DatiInvioRecupero link = linkDiRecuperoConsegnato(opt.email());
        String hashPrima = hashPassword(opt.id());
        revocaDalBanco(opt.id()).andExpect(status().isOk());

        mvc.perform(get("/recupera").param("token", link.tokenInChiaro()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("linkNonValido", true))
                .andExpect(model().attribute("cambioPassword", false));
        mvc.perform(post("/api/auth/recupera/conferma").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoConToken(link.tokenInChiaro(), NUOVA_PASSWORD)))
                .andExpect(status().isGone());

        assertEquals(hashPrima, hashPassword(opt.id()));
        assertEquals("REVOCATO", statoAccount(opt.id()));
        assertEquals(401, login(opt.email(), NUOVA_PASSWORD).getResponse().getStatus());

        // Una nuova richiesta di recupero per l'account revocato non produce alcun link.
        int linkPrima = linkDiRecuperoTotali(opt.id());
        assertTrue(recupero.preparaLink(opt.email()).isEmpty());
        assertEquals(linkPrima, linkDiRecuperoTotali(opt.id()));
    }

    //AC3 – dati e richieste restano invariati e consultabili

    @Test
    @DisplayName("Richieste, account e profilo della OPT restano invariati, tranne lo stato dell'account")
    void datiERichiesteRestanoInvariati() throws Exception {
        Opt opt = optAttiva();
        MockHttpSession sessioneOpt = accedi(opt.email());
        UUID richiestaId = inviaRichiesta(sessioneOpt);

        Map<String, Object> richiestaPrima = riga("select * from richieste where id = ?", richiestaId);
        Map<String, Object> profiloPrima = riga("select * from utenze_profili_opt where account_id = ?", opt.id());
        Map<String, Object> accountPrima = riga("select * from utenze_account where id = ?", opt.id());

        revocaDalBanco(opt.id()).andExpect(status().isOk());

        assertEquals(richiestaPrima, riga("select * from richieste where id = ?", richiestaId));
        assertEquals(profiloPrima, riga("select * from utenze_profili_opt where account_id = ?", opt.id()));
        Map<String, Object> accountDopo = riga("select * from utenze_account where id = ?", opt.id());
        assertEquals("REVOCATO", accountDopo.get("stato"));
        accountPrima.remove("stato");
        accountDopo.remove("stato");
        assertEquals(accountPrima, accountDopo);
    }

    @Test
    @DisplayName("Il Banco consulta ancora le richieste della OPT revocata: coda e dettaglio con titolare e descrizione")
    void richiesteConsultabiliDalBancoDopoLaRevoca() throws Exception {
        Opt opt = optAttiva();
        UUID richiestaId = inviaRichiesta(accedi(opt.email()));

        revocaDalBanco(opt.id()).andExpect(status().isOk());

        mvc.perform(get("/api/richieste/" + richiestaId).session(sessioneBanco))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.richiesta.id").value(richiestaId.toString()))
                .andExpect(jsonPath("$.titolareId").value(opt.id().toString()))
                .andExpect(jsonPath("$.description").value("Richiesta prima della revoca"));
        String coda = mvc.perform(get("/api/richieste/coda").session(sessioneBanco))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> idInCoda = JsonPath.read(coda, "$[*].id");
        assertTrue(idInCoda.contains(richiestaId.toString()), "richiesta della OPT revocata assente dalla coda");
    }

    @Test
    @DisplayName("La OPT revocata resta nell'elenco Account del Banco, con il suo identificativo e stato REVOCATO")
    void optRevocataResta_nellElencoAccountDelBanco() throws Exception {
        Opt opt = optAttiva();
        revocaDalBanco(opt.id()).andExpect(status().isOk());

        MvcResult pagina = mvc.perform(get("/banco/dashboard").session(sessioneBanco))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        List<AccountAmministrativo> elenco =
                (List<AccountAmministrativo>) pagina.getModelAndView().getModel().get("optElenco");
        AccountAmministrativo voce = elenco.stream()
                .filter(a -> a.id().equals(opt.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("OPT revocata assente dall'elenco Account"));
        assertEquals("REVOCATO", voce.stato().name());
        assertEquals(opt.email(), voce.email());
    }

    //AC4 – revoca negata senza permesso Banco, senza modifiche

    @Test
    @DisplayName("Senza permesso Banco la revoca è bloccata dal server (401/403) e non cambia nulla")
    void revocaSenzaPermessoBanco_bloccataSenzaModifiche() throws Exception {
        Opt bersaglio = optAttiva();
        MockHttpSession sessioneBersaglio = accedi(bersaglio.email());
        DatiInvioRecupero link = linkDiRecuperoConsegnato(bersaglio.email());
        Opt inAttesa = optInAttesa();

        Map<String, MockHttpServletRequestBuilder> tentativi = new LinkedHashMap<>();
        tentativi.put("senza sessione", post("/api/opt/" + bersaglio.id() + "/revoca").with(csrf()));
        tentativi.put("OPT stessa", post("/api/opt/" + bersaglio.id() + "/revoca")
                .session(accedi(bersaglio.email())).with(csrf()));
        tentativi.put("altra OPT", post("/api/opt/" + bersaglio.id() + "/revoca")
                .session(accedi(optAttiva().email())).with(csrf()));
        tentativi.put("Operatore", post("/api/opt/" + bersaglio.id() + "/revoca")
                .session(accedi(nuovoAccountDiretto("OPERATORE").email())).with(csrf()));
        tentativi.put("Banco senza token CSRF", post("/api/opt/" + bersaglio.id() + "/revoca")
                .session(sessioneBanco));
        tentativi.put("OPT su account in attesa", post("/api/opt/" + inAttesa.id() + "/revoca")
                .session(accedi(optAttiva().email())).with(csrf()));

        Map<String, Integer> attesi = Map.of(
                "senza sessione", 401,
                "OPT stessa", 403,
                "altra OPT", 403,
                "Operatore", 403,
                "Banco senza token CSRF", 403,
                "OPT su account in attesa", 403);

        for (Map.Entry<String, MockHttpServletRequestBuilder> tentativo : tentativi.entrySet()) {
            int stato = mvc.perform(tentativo.getValue()).andReturn().getResponse().getStatus();
            assertEquals(attesi.get(tentativo.getKey()), stato, tentativo.getKey());
        }

        // Nessuna modifica: stati invariati, sessione ancora valida, link ancora utilizzabili.
        assertEquals("ATTIVO", statoAccount(bersaglio.id()));
        assertEquals("IN_ATTESA_DI_ATTIVAZIONE", statoAccount(inAttesa.id()));
        assertEquals(200, statoDi(get("/api/richieste/mie"), sessioneBersaglio));
        assertFalse(sessioneBersaglio.isInvalid());
        assertTrue(recupero.isLinkValido(link.tokenInChiaro()));
        assertTrue(attivazione.isTokenValido(inAttesa.tokenAttivazione()));
        assertEquals(204, login(bersaglio.email(), PASSWORD).getResponse().getStatus());
    }

    @Test
    @DisplayName("Il Banco non può revocare come OPT un account di altro ruolo o inesistente: 409/404/400 senza modifiche")
    void revocaDelBancoSuBersaglioNonValido_rifiutataSenzaModifiche() throws Exception {
        Account operatore = nuovoAccountDiretto("OPERATORE");
        Account banco = nuovoAccountDiretto("BANCO");

        revocaDalBanco(operatore.id()).andExpect(status().isConflict());
        revocaDalBanco(banco.id()).andExpect(status().isConflict());
        revocaDalBanco(UUID.randomUUID()).andExpect(status().isNotFound());
        mvc.perform(post("/api/opt/non-un-uuid/revoca").session(sessioneBanco).with(csrf()))
                .andExpect(status().isBadRequest());

        assertEquals("ATTIVO", statoAccount(operatore.id()));
        assertEquals("ATTIVO", statoAccount(banco.id()));
        assertEquals(204, login(operatore.email(), PASSWORD).getResponse().getStatus());
    }

    //helper

    private record Account(UUID id, String email) { }

    /** OPT registrata come in produzione e ancora in attesa di attivazione. */
    private Opt optInAttesa() {
        String suffisso = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        String email = "rev26-" + UUID.randomUUID() + "@picus.test";
        EsitoRegistrazioneOpt esito = registrazione.registraOpt(new ComandoRegistrazioneOpt(
                "OPT26-" + suffisso, "OPT Revoca " + suffisso, "napoli", "12345678906", email));
        return new Opt(esito.accountId(), email, esito.datiInvio().tokenInChiaro());
    }

    /** OPT registrata e attivata con il proprio link, con password {@link #PASSWORD}. */
    private Opt optAttiva() {
        Opt opt = optInAttesa();
        attivazione.attivaAccount(opt.tokenAttivazione(), PASSWORD);
        assertEquals("ATTIVO", statoAccount(opt.id()));
        return opt;
    }

    /** Account attivo creato direttamente sul database, per i ruoli che non hanno un flusso di registrazione qui. */
    private Account nuovoAccountDiretto(String ruolo) {
        UUID id = UUID.randomUUID();
        String email = ruolo.toLowerCase() + "-rev26-" + id + "@picus.test";
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, ?, ?, ?, 'ATTIVO')
                """, id, email, encoder.encode(PASSWORD), "Utente " + ruolo, ruolo);
        return new Account(id, email);
    }

    /** Emette un link di recupero come l'elaborazione di /api/auth/recupera e ne conferma la consegna. */
    private DatiInvioRecupero linkDiRecuperoConsegnato(String email) {
        DatiInvioRecupero link = recupero.preparaLink(email).orElseThrow();
        recupero.confermaConsegna(link.idLink());
        return link;
    }

    private ResultActions revocaDalBanco(UUID optId) throws Exception {
        return mvc.perform(post("/api/opt/" + optId + "/revoca").session(sessioneBanco).with(csrf()));
    }

    private UUID inviaRichiesta(MockHttpSession sessioneOpt) throws Exception {
        String corpo = mvc.perform(post("/api/richieste").with(csrf()).session(sessioneOpt)
                        .contentType(MediaType.APPLICATION_JSON).content(RICHIESTA_VALIDA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(corpo, "$.id"));
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    /** Login con {@link #PASSWORD}, con esito 204 atteso. */
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

    private int richiesteDi(UUID optId) {
        return jdbc.queryForObject("select count(*) from richieste where titolare_id = ?", Integer.class, optId);
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
}