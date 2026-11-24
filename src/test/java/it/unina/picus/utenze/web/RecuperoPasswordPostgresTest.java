package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.ModelAndView;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.interno.applicazione.RichiestaRecuperoPassword;

/*
Recupero autonomo della password (US #33) e nuovo accesso, verificati dal task #162 con la catena di Spring
Security reale e PostgreSQL reale. Ogni test parte da righe di utenze_account e percorre le stesse chiamate del
browser: POST /api/auth/recupera, pagina /recupera?token=..., POST /api/auth/recupera/conferma e login JSON.

Il contesto ha due sostituti, registrati qui e non con @MockitoBean perché servono a tutte le classi del gruppo:
- PostaFinta registra le email al posto dell'SMTP: è l'unico modo per leggere il token in chiaro, che nel database
  non c'è. Può anche rifiutare l'invio per un indirizzo, come un server di posta in errore.
- OrologioFinto fissa l'istante: la scadenza dei 15 minuti si verifica al millisecondo senza attese.
Con questi sostituti il contesto è diverso da quello degli altri test di integrazione e Spring ne avvia uno in più,
sullo stesso container.

La richiesta del link lavora in secondo piano su un esecutore proprio. Qui ha un solo thread, quindi le richieste
sono elaborate in ordine: per sapere che tutte quelle precedenti sono finite (email, conferma o scarto del link) si
accoda una richiesta «sentinella» per un account attivo e si aspetta la sua email (attendiElaborazioni).

La durata di 15 minuti è scritta qui come valore della storia e non letta dal codice di produzione: se cambiasse,
questi test devono fallire.
*/
@DisplayName("Recupero autonomo della password e nuovo accesso su PostgreSQL (#33, #162)")
@Import(RecuperoPasswordPostgresTest.PostaEOrologioDiProva.class)
@TestPropertySource(properties = "picus.utenze.recupero.esecutore.thread=1")
class RecuperoPasswordPostgresTest extends IntegrazionePostgresBase {

    private static final String PASSWORD_VECCHIA = "Password-vecchia-1";
    private static final String PASSWORD_NUOVA = "Password-nuova-2026";

    /** Validità del link fissata dalla US #33 (AC1). */
    private static final Duration VALIDITA_DELLA_STORIA = Duration.ofMinutes(15);
    private static final Instant T0 = Instant.parse("2026-11-24T09:00:00Z");
    private static final Duration UN_MS = Duration.ofMillis(1);

    /** Hash iniziali a costo 4 per velocità; la nuova password usa il PasswordEncoder di produzione. */
    private static final BCryptPasswordEncoder BCRYPT_VELOCE = new BCryptPasswordEncoder(4);

    private static final String INVITO_NUOVA_RICHIESTA = "Richiedine uno nuovo";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private PostaFinta posta;
    @Autowired
    private OrologioFinto orologio;
    @Autowired
    private LimitatoreAccessi limitatoreAccessi;
    @Autowired
    private RichiestaRecuperoPassword richiestaRecupero;
    @Autowired
    private UtenzeApi utenze;

    private record Account(UUID id, String email, String nome) { }

    @BeforeEach
    void prepara() {
        orologio.imposta(T0);
        posta.azzera();
        limitatoreAccessi.azzeraTutto();
    }

    // AC1, AC3, AC4: percorso completo per i tre ruoli

    @ParameterizedTest(name = "{0}: link via email, nuova password, accesso con la nuova e non con la vecchia")
    @ValueSource(strings = {"BANCO", "OPT", "OPERATORE"})
    void recuperoCompletoPerRuolo(String ruolo) throws Exception {
        Account account = nuovoAccount(ruolo, "ATTIVO");
        String hashPrima = hashDi(account);

        // AC1: conferma a video e un solo link inviato all'indirizzo dell'account, con il suo nome
        MvcResult richiesta = richiediRecupero(account.email());
        assertEquals(200, richiesta.getResponse().getStatus());
        assertEquals(RecuperoPasswordController.MESSAGGIO_CONFERMA, messaggio(richiesta));
        attendiElaborazioni();
        List<PostaFinta.Email> ricevute = posta.inviateA(account.email());
        assertEquals(1, ricevute.size());
        assertEquals(account.nome(), ricevute.get(0).nome());
        String token = ricevute.get(0).token();

        // Il link apre il modulo di cambio password
        ModelAndView pagina = paginaRecupero(token);
        assertEquals(Boolean.TRUE, pagina.getModel().get("cambioPassword"));
        assertEquals(Boolean.FALSE, pagina.getModel().get("linkNonValido"));

        // AC3: password aggiornata e conferma del cambiamento
        MvcResult cambio = conferma(token, PASSWORD_NUOVA);
        assertEquals(200, cambio.getResponse().getStatus());
        assertEquals(RecuperoPasswordController.MESSAGGIO_PASSWORD_CAMBIATA, messaggio(cambio));
        assertNotEquals(hashPrima, hashDi(account));

        // AC4: accesso con la nuova password, negato con la precedente
        assertEquals(204, statoLogin(account.email(), PASSWORD_NUOVA));
        assertEquals(401, statoLogin(account.email(), PASSWORD_VECCHIA));

        // AC3: il link non è più utilizzabile, né per cambiare password né per aprire il modulo
        MvcResult riuso = conferma(token, "Un-altra-password-99");
        assertEquals(410, riuso.getResponse().getStatus());
        assertTrue(messaggio(riuso).contains(INVITO_NUOVA_RICHIESTA));
        assertEquals(Boolean.TRUE, paginaRecupero(token).getModel().get("linkNonValido"));
        assertEquals(204, statoLogin(account.email(), PASSWORD_NUOVA));
    }

    @Test
    @DisplayName("Il link vale 15 minuti dall'emissione e nel database c'è solo l'impronta del token")
    void linkSalvatoConScadenzaEImpronta() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");

        String token = richiediLinkValido(account);

        assertEquals(1, jdbc.queryForObject(
                "select count(*) from utenze_token_recupero_password where account_id = ?", Integer.class, account.id()));
        Timestamp scadenza = jdbc.queryForObject(
                "select scadenza from utenze_token_recupero_password where account_id = ?", Timestamp.class, account.id());
        assertEquals(T0.plus(VALIDITA_DELLA_STORIA), scadenza.toInstant());
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from utenze_token_recupero_password where token_hash = ?", Integer.class, token));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from utenze_token_recupero_password where token_hash = ?", Integer.class, sha256(token)));
    }

    @Test
    @DisplayName("L'email scritta con maiuscole e spazi raggiunge comunque l'indirizzo dell'account")
    void emailNormalizzata() throws Exception {
        Account account = nuovoAccount("OPERATORE", "ATTIVO");

        MvcResult esito = richiediRecupero("  " + account.email().toUpperCase(Locale.ROOT) + " ");
        attendiElaborazioni();

        assertEquals(200, esito.getResponse().getStatus());
        assertEquals(1, posta.inviateA(account.email()).size());
    }

    // AC2: stessa conferma e nessun link per gli account non idonei

    @Test
    @DisplayName("Email sconosciuta, revocata o in attesa: stessa risposta dell'account attivo e nessun link inviato")
    void stessaConfermaPerOgniIndirizzo() throws Exception {
        Account attivo = nuovoAccount("OPT", "ATTIVO");
        Account revocato = nuovoAccount("OPT", "REVOCATO");
        Account inAttesa = nuovoAccount("OPERATORE", "IN_ATTESA_DI_ATTIVAZIONE");
        String sconosciuta = "sconosciuta-" + UUID.randomUUID() + "@picus.test";

        Map<String, MvcResult> casi = new LinkedHashMap<>();
        casi.put("account attivo", richiediRecupero(attivo.email()));
        casi.put("email non registrata", richiediRecupero(sconosciuta));
        casi.put("account revocato", richiediRecupero(revocato.email()));
        casi.put("account in attesa di attivazione", richiediRecupero(inAttesa.email()));
        attendiElaborazioni();

        String rispostaAttivo = casi.get("account attivo").getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (Map.Entry<String, MvcResult> caso : casi.entrySet()) {
            assertEquals(200, caso.getValue().getResponse().getStatus(), caso.getKey());
            assertEquals(rispostaAttivo, caso.getValue().getResponse().getContentAsString(StandardCharsets.UTF_8),
                    caso.getKey());
        }
        assertEquals(1, posta.inviateA(attivo.email()).size());
        for (String indirizzo : List.of(sconosciuta, revocato.email(), inAttesa.email())) {
            assertTrue(posta.inviateA(indirizzo).isEmpty(), "email inviata a " + indirizzo);
            assertTrue(posta.tentateA(indirizzo).isEmpty(), "invio tentato verso " + indirizzo);
        }
        for (Account nonIdoneo : List.of(revocato, inAttesa)) {
            assertEquals(0, linkDi(nonIdoneo), "link creato per " + nonIdoneo.email());
        }
    }

    // AC5: password non conforme alla regola concordata

    static Stream<Arguments> passwordNonConformi() {
        return Stream.of(
                Arguments.of("vuota", "\"\"", "obbligatoria"),
                Arguments.of("solo spazi", "\"            \"", "obbligatoria"),
                Arguments.of("11 caratteri", "\"Abcdefghi-1\"", "almeno 12 caratteri"),
                Arguments.of("6 simboli fuori dal piano base (12 unità UTF-16)", "\"😀😀😀😀😀😀\"", "almeno 12 caratteri"),
                Arguments.of("73 byte ASCII", "\"" + "a".repeat(73) + "\"", "72 byte"),
                Arguments.of("37 caratteri da 2 byte (74 byte)", "\"" + "è".repeat(37) + "\"", "72 byte"),
                Arguments.of("numero invece di testo", "123456789012345", "Inserisci la nuova password"),
                Arguments.of("campo assente", "null", "Inserisci la nuova password"));
    }

    @ParameterizedTest(name = "{0}: 400 sul campo, password invariata e link ancora utilizzabile")
    @MethodSource("passwordNonConformi")
    void passwordNonConformeRifiutata(String caso, String nuovaPasswordJson, String cosaCorreggere) throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        String hashPrima = hashDi(account);
        String token = richiediLinkValido(account);

        MvcResult esito = mvc.perform(post("/api/auth/recupera/conferma").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"nuovaPassword\":" + nuovaPasswordJson + "}"))
                .andReturn();

        assertEquals(400, esito.getResponse().getStatus(), caso);
        String corpo = esito.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String suCampo = JsonPath.read(corpo, "$.campi.nuovaPassword");
        assertTrue(suCampo.contains(cosaCorreggere), caso + ": " + suCampo);
        assertEquals(hashPrima, hashDi(account), caso);
        assertEquals(204, statoLogin(account.email(), PASSWORD_VECCHIA), caso);

        // Corretta la password, lo stesso link funziona
        assertEquals(Boolean.TRUE, paginaRecupero(token).getModel().get("cambioPassword"), caso);
        assertEquals(200, conferma(token, PASSWORD_NUOVA).getResponse().getStatus(), caso);
        assertEquals(204, statoLogin(account.email(), PASSWORD_NUOVA), caso);
    }

    @ParameterizedTest(name = "password al limite della regola accettata: {0}")
    @ValueSource(strings = {
            "Dodici-car12", // esattamente 12 caratteri ASCII
            "èèèèèèèèèèèèèèèèèèèèèèèèèèèèèèèèèèèè", // 36 caratteri da 2 byte = 72 byte
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}) // 72 byte ASCII
    void passwordAlLimiteAccettata(String nuovaPassword) throws Exception {
        assertTrue(nuovaPassword.codePointCount(0, nuovaPassword.length()) >= 12);
        assertTrue(nuovaPassword.getBytes(StandardCharsets.UTF_8).length <= 72);
        Account account = nuovoAccount("BANCO", "ATTIVO");
        String token = richiediLinkValido(account);

        assertEquals(200, conferma(token, nuovaPassword).getResponse().getStatus());
        assertEquals(204, statoLogin(account.email(), nuovaPassword));
    }

    // AC6 e AC1: link scaduto, già usato, inventato

    @Test
    @DisplayName("A 15 minuti meno 1 ms il link funziona ancora e cambia la password")
    void linkValidoFinoAllaScadenza() throws Exception {
        Account account = nuovoAccount("OPERATORE", "ATTIVO");
        String token = richiediLinkValido(account);

        orologio.imposta(T0.plus(VALIDITA_DELLA_STORIA).minus(UN_MS));

        assertEquals(Boolean.TRUE, paginaRecupero(token).getModel().get("cambioPassword"));
        assertEquals(200, conferma(token, PASSWORD_NUOVA).getResponse().getStatus());
        assertEquals(204, statoLogin(account.email(), PASSWORD_NUOVA));
    }

    @Test
    @DisplayName("A 15 minuti esatti il link è scaduto: password invariata e invito a una nuova richiesta")
    void linkScadutoNonCambiaLaPassword() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        String hashPrima = hashDi(account);
        String token = richiediLinkValido(account);

        orologio.imposta(T0.plus(VALIDITA_DELLA_STORIA));

        MvcResult esito = conferma(token, PASSWORD_NUOVA);
        assertEquals(410, esito.getResponse().getStatus());
        assertTrue(messaggio(esito).contains(INVITO_NUOVA_RICHIESTA));
        assertEquals(hashPrima, hashDi(account));
        assertEquals(204, statoLogin(account.email(), PASSWORD_VECCHIA));

        // La pagina del link propone una nuova richiesta e non riporta il token
        ModelAndView pagina = paginaRecupero(token);
        assertEquals(Boolean.TRUE, pagina.getModel().get("linkNonValido"));
        assertEquals(Boolean.FALSE, pagina.getModel().get("cambioPassword"));
        assertNull(pagina.getModel().get("token"));

        // Si può chiedere subito un nuovo link, che funziona
        String nuovo = richiediLinkValido(account);
        assertEquals(200, conferma(nuovo, PASSWORD_NUOVA).getResponse().getStatus());
        assertEquals(204, statoLogin(account.email(), PASSWORD_NUOVA));
    }

    @Test
    @DisplayName("Link inventato o vuoto: 410 con invito a ripetere, nessuna password cambiata")
    void linkInventato() throws Exception {
        for (String token : List.of("token-inventato", "", "   ")) {
            MvcResult esito = conferma(token, PASSWORD_NUOVA);
            assertEquals(410, esito.getResponse().getStatus(), "token «" + token + "»");
            assertTrue(messaggio(esito).contains(INVITO_NUOVA_RICHIESTA));
        }
        assertEquals(Boolean.TRUE, paginaRecupero("token-inventato").getModel().get("linkNonValido"));
    }

    @Test
    @DisplayName("Token che non è un testo: 400 per formato non valido")
    void tokenDiTipoSbagliato() throws Exception {
        MvcResult esito = mvc.perform(post("/api/auth/recupera/conferma").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":12345,\"nuovaPassword\":\"" + PASSWORD_NUOVA + "\"}"))
                .andReturn();

        assertEquals(400, esito.getResponse().getStatus());
        assertEquals(RecuperoPasswordController.MESSAGGIO_FORMATO_NON_VALIDO, messaggio(esito));
    }

    // un solo link valido per volta

    @Test
    @DisplayName("Un nuovo link rende inutilizzabile il precedente; dopo il cambio nessun link resta aperto")
    void nuovoLinkSostituisceIlPrecedente() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        String primo = richiediLinkValido(account);
        String secondo = richiediLinkValido(account);
        assertNotEquals(primo, secondo);

        assertEquals(Boolean.TRUE, paginaRecupero(primo).getModel().get("linkNonValido"));
        assertEquals(410, conferma(primo, PASSWORD_NUOVA).getResponse().getStatus());
        assertEquals(204, statoLogin(account.email(), PASSWORD_VECCHIA));

        assertEquals(200, conferma(secondo, PASSWORD_NUOVA).getResponse().getStatus());
        assertEquals(0, linkAperti(account));
    }

    @Test
    @DisplayName("Se la posta rifiuta l'email, la risposta non cambia e resta valido il link già ricevuto")
    void invioFallitoConservaIlLinkPrecedente() throws Exception {
        Account account = nuovoAccount("BANCO", "ATTIVO");
        String ricevuto = richiediLinkValido(account);

        posta.rifiuta(account.email());
        MvcResult esito = richiediRecupero(account.email());
        attendiElaborazioni();

        assertEquals(200, esito.getResponse().getStatus());
        assertEquals(RecuperoPasswordController.MESSAGGIO_CONFERMA, messaggio(esito));
        List<PostaFinta.Email> tentate = posta.tentateA(account.email());
        assertEquals(1, tentate.size());
        String nonConsegnato = tentate.get(0).token();

        assertEquals(410, conferma(nonConsegnato, PASSWORD_NUOVA).getResponse().getStatus());
        assertEquals(200, conferma(ricevuto, PASSWORD_NUOVA).getResponse().getStatus());
        assertEquals(204, statoLogin(account.email(), PASSWORD_NUOVA));
    }

    @Test
    @DisplayName("La revoca dell'account rende inutilizzabile il link già inviato")
    void revocaInvalidaIlLink() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        String hashPrima = hashDi(account);
        String token = richiediLinkValido(account);

        utenze.revocaUtenzaOpt(account.id());

        assertEquals(410, conferma(token, PASSWORD_NUOVA).getResponse().getStatus());
        assertEquals(Boolean.TRUE, paginaRecupero(token).getModel().get("linkNonValido"));
        assertEquals(hashPrima, hashDi(account));
        assertEquals(401, statoLogin(account.email(), PASSWORD_NUOVA));
        assertEquals(0, linkAperti(account));
    }

    @Test
    @DisplayName("Otto conferme in parallelo con lo stesso link: una sola riesce e vale solo la sua password")
    void confermeConcorrentiConLoStessoLink() throws Exception {
        Account account = nuovoAccount("OPERATORE", "ATTIVO");
        String token = richiediLinkValido(account);
        int tentativi = 8;
        ExecutorService pool = Executors.newFixedThreadPool(tentativi);
        try {
            CountDownLatch pronti = new CountDownLatch(tentativi);
            CountDownLatch via = new CountDownLatch(1);
            List<Future<Integer>> esiti = new ArrayList<>();
            for (int i = 0; i < tentativi; i++) {
                String password = PASSWORD_NUOVA + "-" + i;
                esiti.add(pool.submit(() -> {
                    pronti.countDown();
                    via.await();
                    return conferma(token, password).getResponse().getStatus();
                }));
            }
            assertTrue(pronti.await(10, TimeUnit.SECONDS));
            via.countDown();

            List<Integer> riusciti = new ArrayList<>();
            for (int i = 0; i < tentativi; i++) {
                int stato = esiti.get(i).get(2, TimeUnit.MINUTES);
                if (stato == 200) {
                    riusciti.add(i);
                } else {
                    assertEquals(410, stato, "tentativo " + i);
                }
            }
            assertEquals(1, riusciti.size(), "conferme riuscite: " + riusciti);
            String hash = hashDi(account);
            for (int i = 0; i < tentativi; i++) {
                assertEquals(riusciti.contains(i), encoder.matches(PASSWORD_NUOVA + "-" + i, hash), "password " + i);
            }
            assertEquals(0, linkAperti(account));
        } finally {
            pool.shutdownNow();
        }
    }

    // richiesta del link: forma, limite e CSRF

    @Test
    @DisplayName("Email malformata, di tipo sbagliato o assente: 400 sul campo e nessun invio")
    void richiestaConEmailNonValida() throws Exception {
        for (String corpo : List.of("{\"email\":\"non-una-email\"}", "{\"email\":12345}", "{\"email\":true}",
                "{\"email\":[\"a@picus.test\"]}", "{}")) {
            MvcResult esito = mvc.perform(post("/api/auth/recupera").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
            assertEquals(400, esito.getResponse().getStatus(), corpo);
            String risposta = esito.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertNotNull(JsonPath.read(risposta, "$.campi.email"), corpo);
        }
        attendiElaborazioni();
        assertEquals(List.of(), posta.inviateTranneSentinelle());
    }

    @Test
    @DisplayName("Quarta richiesta in 5 minuti per lo stesso indirizzo: 429, anche se l'indirizzo non esiste")
    void limiteDiRichiestePerIndirizzo() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        String sconosciuta = "sconosciuta-" + UUID.randomUUID() + "@picus.test";
        // Soglia proposta in regole-accessi.md (3 richieste ogni 5 minuti), ancora da validare con il PO.
        for (String indirizzo : List.of(account.email(), sconosciuta)) {
            for (int i = 0; i < 3; i++) {
                assertEquals(200, richiediRecupero(indirizzo).getResponse().getStatus(), indirizzo + " richiesta " + (i + 1));
            }
            MvcResult quarta = richiediRecupero(indirizzo);
            assertEquals(429, quarta.getResponse().getStatus(), indirizzo);
            assertEquals(RecuperoPasswordController.MESSAGGIO_TROPPE_RICHIESTE, messaggio(quarta));
        }
        attendiElaborazioni();
        assertEquals(3, posta.inviateA(account.email()).size());

        orologio.imposta(T0.plus(Duration.ofMinutes(5)).plus(UN_MS));
        assertEquals(200, richiediRecupero(account.email()).getResponse().getStatus());
    }

    @Test
    @DisplayName("Senza token CSRF richiesta e conferma sono rifiutate con 401 e non cambiano nulla")
    void senzaCsrf() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        String token = richiediLinkValido(account);
        String hashPrima = hashDi(account);

        assertEquals(401, mvc.perform(post("/api/auth/recupera").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + account.email() + "\"}")).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(post("/api/auth/recupera/conferma").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"nuovaPassword\":\"" + PASSWORD_NUOVA + "\"}"))
                .andReturn().getResponse().getStatus());
        attendiElaborazioni();

        assertEquals(1, posta.inviateA(account.email()).size());
        assertEquals(hashPrima, hashDi(account));
        assertEquals(Boolean.TRUE, paginaRecupero(token).getModel().get("cambioPassword"));
    }

    // messaggi visibili nella pagina /recupera

    @Test
    @DisplayName("Pagina senza link: modulo di richiesta con la durata di 15 minuti")
    void paginaDiRichiesta() throws Exception {
        String html = htmlRecupero(null);

        assertTrue(html.contains("id=\"richiesta-recupero\""));
        assertFalse(html.contains("id=\"cambio-password\""));
        assertTrue(html.contains("Il link vale 15 minuti."));
    }

    @Test
    @DisplayName("Pagina con link valido: modulo di cambio password con il token nascosto e la regola dei 12 caratteri")
    void paginaConLinkValido() throws Exception {
        String token = richiediLinkValido(nuovoAccount("OPT", "ATTIVO"));

        String html = htmlRecupero(token);

        assertTrue(html.contains("id=\"cambio-password\""));
        assertFalse(html.contains("id=\"richiesta-recupero\""));
        assertTrue(html.contains("type=\"hidden\" name=\"token\" value=\"" + token + "\""));
        assertTrue(html.contains("Usa almeno 12 caratteri."));
    }

    @Test
    @DisplayName("Pagina con link usato: avviso di link non valido, modulo di nuova richiesta e nessun token")
    void paginaConLinkUsato() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        String token = richiediLinkValido(account);
        assertEquals(200, conferma(token, PASSWORD_NUOVA).getResponse().getStatus());

        String html = htmlRecupero(token);

        assertTrue(html.contains("Il link di recupero non è valido, è scaduto oppure è già stato utilizzato."));
        assertTrue(html.contains("id=\"richiesta-recupero\""));
        assertFalse(html.contains("id=\"cambio-password\""));
        assertFalse(html.contains(token));
    }

    // helper

    private Account nuovoAccount(String ruolo, String stato) {
        UUID id = UUID.randomUUID();
        String email = "rec-" + id + "@picus.test";
        String nome = "Utente di prova " + ruolo;
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, ?, ?, ?, ?)
                """, id, email, BCRYPT_VELOCE.encode(PASSWORD_VECCHIA), nome, ruolo, stato);
        if ("OPT".equals(ruolo)) {
            String codiceStruttura = "R-" + id.toString().replace("-", "").substring(0, 26);
            jdbc.update("insert into utenze_profili_opt (account_id, codice_struttura, comune_codice) values (?, ?, 'napoli')",
                    id, codiceStruttura);
        }
        return new Account(id, email, nome);
    }

    private MvcResult richiediRecupero(String email) throws Exception {
        return mvc.perform(post("/api/auth/recupera").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andReturn();
    }

    /** Chiede il link per un account attivo e restituisce il token dell'ultima email ricevuta. */
    private String richiediLinkValido(Account account) throws Exception {
        assertEquals(200, richiediRecupero(account.email()).getResponse().getStatus());
        attendiElaborazioni();
        List<PostaFinta.Email> ricevute = posta.inviateA(account.email());
        assertFalse(ricevute.isEmpty(), "nessuna email per " + account.email());
        return ricevute.get(ricevute.size() - 1).token();
    }

    /**
     * Aspetta che siano finite tutte le richieste di recupero accodate finora: con un solo thread la sentinella è
     * elaborata dopo di loro.
     */
    private void attendiElaborazioni() throws InterruptedException {
        Account sentinella = nuovoAccount("BANCO", "ATTIVO");
        posta.segnaSentinella(sentinella.email());
        assertTrue(richiestaRecupero.richiedi(sentinella.email()), "coda del recupero piena");
        posta.attendi(sentinella.email(), Duration.ofSeconds(20));
    }

    private MvcResult conferma(String token, String nuovaPassword) throws Exception {
        return mvc.perform(post("/api/auth/recupera/conferma").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"nuovaPassword\":\"" + nuovaPassword + "\"}"))
                .andReturn();
    }

    private ModelAndView paginaRecupero(String token) throws Exception {
        MvcResult esito = mvc.perform(get("/recupera").param("token", token)).andReturn();
        assertEquals(200, esito.getResponse().getStatus());
        return esito.getModelAndView();
    }

    private String htmlRecupero(String token) throws Exception {
        var richiesta = get("/recupera").accept(MediaType.TEXT_HTML);
        if (token != null) {
            richiesta.param("token", token);
        }
        MvcResult esito = mvc.perform(richiesta).andReturn();
        assertEquals(200, esito.getResponse().getStatus());
        return esito.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private int statoLogin(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private static String messaggio(MvcResult esito) throws Exception {
        return JsonPath.read(esito.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.messaggio");
    }

    private String hashDi(Account account) {
        return jdbc.queryForObject("select password_hash from utenze_account where id = ?", String.class, account.id());
    }

    private int linkDi(Account account) {
        return jdbc.queryForObject("select count(*) from utenze_token_recupero_password where account_id = ?",
                Integer.class, account.id());
    }

    private int linkAperti(Account account) {
        return jdbc.queryForObject("select count(*) from utenze_token_recupero_password where account_id = ? and usato_il is null",
                Integer.class, account.id());
    }

    private static String sha256(String testo) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(testo.getBytes(StandardCharsets.UTF_8)));
    }

    // sostituti del contesto

    @TestConfiguration(proxyBeanMethods = false)
    static class PostaEOrologioDiProva {

        @Bean
        @Primary
        PostaFinta postaFinta() {
            return new PostaFinta();
        }

        /** Prevale sull'orologio di {@code ConfigurazioneTempo}. */
        @Bean
        @Primary
        OrologioFinto orologioFinto() {
            return new OrologioFinto(T0);
        }
    }

    /** Fake manuale della posta: registra le email consegnate e quelle rifiutate, con il token in chiaro. */
    static final class PostaFinta implements InvioEmail {

        record Email(String destinatario, String nome, String token) { }

        private final List<Email> inviate = new CopyOnWriteArrayList<>();
        private final List<Email> tentate = new CopyOnWriteArrayList<>();
        private final Set<String> rifiutate = ConcurrentHashMap.newKeySet();
        private final Set<String> sentinelle = ConcurrentHashMap.newKeySet();

        @Override
        public void inviaLinkAttivazione(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
            // non usato dal recupero
        }

        @Override
        public void inviaLinkResetPassword(String emailDestinatario, String nomeDestinatario, String tokenInChiaro) {
            Email email = new Email(emailDestinatario, nomeDestinatario, tokenInChiaro);
            if (rifiutate.contains(emailDestinatario)) {
                tentate.add(email);
                throw new IllegalStateException("Server di posta non disponibile (prova)");
            }
            synchronized (this) {
                inviate.add(email);
                notifyAll();
            }
        }

        void azzera() {
            inviate.clear();
            tentate.clear();
            rifiutate.clear();
            sentinelle.clear();
        }

        void rifiuta(String indirizzo) {
            rifiutate.add(indirizzo);
        }

        void segnaSentinella(String indirizzo) {
            sentinelle.add(indirizzo);
        }

        List<Email> inviateA(String indirizzo) {
            return inviate.stream().filter(e -> e.destinatario().equals(indirizzo)).toList();
        }

        List<Email> tentateA(String indirizzo) {
            return tentate.stream().filter(e -> e.destinatario().equals(indirizzo)).toList();
        }

        List<Email> inviateTranneSentinelle() {
            return inviate.stream().filter(e -> !sentinelle.contains(e.destinatario())).toList();
        }

        synchronized void attendi(String indirizzo, Duration massimo) throws InterruptedException {
            long limite = System.nanoTime() + massimo.toNanos();
            while (inviateA(indirizzo).isEmpty()) {
                long resta = limite - System.nanoTime();
                if (resta <= 0) {
                    throw new AssertionError("Nessuna email ricevuta da " + indirizzo + " entro " + massimo);
                }
                TimeUnit.NANOSECONDS.timedWait(this, resta);
            }
        }
    }

    /** Fake manuale dell'orologio: l'istante cambia solo quando il test lo decide. */
    static final class OrologioFinto extends Clock {
        private volatile Instant istante;

        OrologioFinto(Instant iniziale) {
            this.istante = iniziale;
        }

        void imposta(Instant nuovo) {
            this.istante = nuovo;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return istante;
        }
    }
}