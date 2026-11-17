package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.IntegrazionePostgresBase;

import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

/**
 * Backend del login (#25) e casi di sicurezza collegati, con la catena di Spring Security reale e
 * PostgreSQL reale. Gli account sono righe di {@code utenze_account} con hash BCrypt, quindi il
 * percorso è lo stesso della produzione: filtro JSON, {@code AutenticazionePicus},
 * {@code UtenzeService}, {@code VerificaSessioneFilter}, CSRF e permessi di ruolo.
 *
 * Ogni test usa email uniche e azzera il limitatore, così i test non dipendono dall'ordine.
 */
@SpringBootTest(properties = "logging.level.org.springframework.security=DEBUG")
@AutoConfigureMockMvc
@DisplayName("Login, sessione, revoca e CSRF su PostgreSQL (#25)")
class AccessoLoginPostgresTest extends IntegrazionePostgresBase {

    private static final String PASSWORD = "Password-di-prova-1";
    private static final String ERRORE_GENERICO = "{\"errore\":\"Credenziali non valide\"}";
    private static final String ERRORE_BLOCCO =
            "{\"errore\":\"Accesso temporaneamente bloccato. Riprova tra poco.\"}";
    private static final Map<String, String> AREE = Map.of(
            "BANCO", "/banco/dashboard", "OPT", "/opt/dashboard", "OPERATORE", "/operatore/dashboard");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private LimitatoreAccessi limitatore;

    private String hash;

    private record Account(UUID id, String email) { }

    @BeforeEach
    void prepara() {
        limitatore.azzeraTutto();
        hash = encoder.encode(PASSWORD);
    }

    //infrastruttura

    @Test
    @DisplayName("La suite gira su PostgreSQL con tutte le migrazioni Flyway riuscite")
    void databaseDellaSuiteEPostgres() throws Exception {
        try (Connection connessione = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connessione.getMetaData().getDatabaseProductName());
        }
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from flyway_schema_history where not success", Integer.class));
        assertTrue(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class) > 0);
    }

    //login per ruolo e aree

    @ParameterizedTest(name = "{0}: login e reindirizzamento alla propria area")
    @CsvSource({"BANCO,/banco/dashboard", "OPT,/opt/dashboard", "OPERATORE,/operatore/dashboard"})
    void loginApreLAreaDelProprioRuolo(String ruolo, String area) throws Exception {
        Account account = nuovoAccount(ruolo, "ATTIVO");

        MvcResult esito = accedi(account.email(), PASSWORD, null);

        assertEquals(204, esito.getResponse().getStatus());
        MockHttpSession sessione = sessione(esito);
        assertTrue(autenticata(sessione));
        mvc.perform(get("/").session(sessione))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(area));
    }

    @ParameterizedTest(name = "{0}: le aree degli altri ruoli sono negate")
    @ValueSource(strings = {"BANCO", "OPT", "OPERATORE"})
    void areeRiservateAgliAltriRuoli(String ruolo) throws Exception {
        MockHttpSession sessione = accedi(nuovoAccount(ruolo, "ATTIVO"));

        for (Map.Entry<String, String> area : AREE.entrySet()) {
            if (area.getKey().equals(ruolo)) {
                MvcResult propria = mvc.perform(get(area.getValue()).session(sessione)).andReturn();
                int stato = propria.getResponse().getStatus();
                assertNotEquals(403, stato, "area propria negata: " + area.getValue());
                assertNotEquals(401, stato, "area propria non autenticata: " + area.getValue());
                String redirect = propria.getResponse().getRedirectedUrl();
                assertTrue(redirect == null || !redirect.startsWith("/login"), "rimando al login: " + redirect);
            } else {
                mvc.perform(get(area.getValue()).session(sessione))
                        .andExpect(status().isForbidden());
                mvc.perform(get(area.getValue()).session(sessione).accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isForbidden())
                        .andExpect(content().string(""));
            }
        }
    }

    @ParameterizedTest(name = "{0}: /api/richieste/mie={1}, /api/richieste/coda={2}")
    @CsvSource({"BANCO,403,200", "OPT,200,403", "OPERATORE,403,403"})
    void apiDellaCodaERiservataPerRuolo(String ruolo, int mie, int coda) throws Exception {
        MockHttpSession sessione = accedi(nuovoAccount(ruolo, "ATTIVO"));

        assertEquals(mie, statoDi(get("/api/richieste/mie"), sessione));
        assertEquals(coda, statoDi(get("/api/richieste/coda"), sessione));
    }

    //credenziali errate e revoca

    @Test
    @DisplayName("Credenziali errate e account non attivi: stesso esito pubblico e nessuna sessione autenticata")
    void stessoEsitoPubblicoPerCredenzialiErrateERevocati() throws Exception {
        Account attivo = nuovoAccount("OPT", "ATTIVO");
        Account revocato = nuovoAccount("OPT", "REVOCATO");
        Account inAttesa = nuovoAccount("OPT", "IN_ATTESA_DI_ATTIVAZIONE");

        Map<String, MvcResult> casi = new LinkedHashMap<>();
        casi.put("email sconosciuta", accedi("sconosciuta-" + UUID.randomUUID() + "@picus.test", PASSWORD, null));
        casi.put("password errata", accedi(attivo.email(), "sbagliata", null));
        casi.put("password vuota", accedi(attivo.email(), "", null));
        casi.put("account revocato", accedi(revocato.email(), PASSWORD, null));
        casi.put("account in attesa di attivazione", accedi(inAttesa.email(), PASSWORD, null));

        for (Map.Entry<String, MvcResult> caso : casi.entrySet()) {
            MvcResult esito = caso.getValue();
            assertEquals(401, esito.getResponse().getStatus(), caso.getKey());
            assertEquals(ERRORE_GENERICO, esito.getResponse().getContentAsString(), caso.getKey());
            MockHttpSession sessione = (MockHttpSession) esito.getRequest().getSession(false);
            assertFalse(autenticata(sessione), "sessione autenticata dopo: " + caso.getKey());
            assertEquals(401, statoDi(get("/api/richieste/mie"), sessione), caso.getKey());
        }
    }

    //blocco temporaneo

    @Test
    @DisplayName("Dopo cinque tentativi falliti il sesto è bloccato con 429, anche con la password giusta")
    void bloccoTemporaneoDopoCinqueFallimenti() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        for (int i = 0; i < LimitatoreAccessi.MAX_FALLIMENTI; i++) {
            assertEquals(401, accedi(account.email(), "sbagliata", null).getResponse().getStatus());
        }

        // Stessa email scritta in maiuscolo: la chiave del limitatore è normalizzata.
        MvcResult bloccato = accedi(account.email().toUpperCase(Locale.ROOT), PASSWORD, null);

        assertEquals(429, bloccato.getResponse().getStatus());
        assertEquals(ERRORE_BLOCCO, bloccato.getResponse().getContentAsString());
        assertFalse(autenticata((MockHttpSession) bloccato.getRequest().getSession(false)));

        // Il blocco riguarda solo quell'email.
        assertEquals(204, accedi(nuovoAccount("OPT", "ATTIVO").email(), PASSWORD, null).getResponse().getStatus());
    }

    @Test
    @DisplayName("Un accesso riuscito azzera i tentativi falliti")
    void accessoRiuscitoAzzeraIlContatore() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        for (int i = 0; i < LimitatoreAccessi.MAX_FALLIMENTI - 1; i++) {
            assertEquals(401, accedi(account.email(), "sbagliata", null).getResponse().getStatus());
        }
        assertEquals(204, accedi(account.email(), PASSWORD, null).getResponse().getStatus());

        for (int i = 0; i < LimitatoreAccessi.MAX_FALLIMENTI; i++) {
            assertEquals(401, accedi(account.email(), "sbagliata", null).getResponse().getStatus(),
                    "tentativo " + (i + 1) + " dopo l'azzeramento");
        }
        assertEquals(429, accedi(account.email(), "sbagliata", null).getResponse().getStatus());
    }

    @Test
    @DisplayName("Venti tentativi concorrenti sulla stessa email: cinque verificati (401), quindici bloccati (429)")
    void tentativiConcorrenti() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        int tentativi = 20;
        ExecutorService pool = Executors.newFixedThreadPool(tentativi);
        try {
            CountDownLatch pronti = new CountDownLatch(tentativi);
            CountDownLatch via = new CountDownLatch(1);
            List<Future<Integer>> esiti = new ArrayList<>();
            for (int i = 0; i < tentativi; i++) {
                esiti.add(pool.submit(() -> {
                    pronti.countDown();
                    via.await();
                    return accedi(account.email(), "sbagliata", null).getResponse().getStatus();
                }));
            }
            assertTrue(pronti.await(10, TimeUnit.SECONDS));
            via.countDown();

            int nonAutorizzati = 0;
            int bloccati = 0;
            for (Future<Integer> esito : esiti) {
                int stato = esito.get(2, TimeUnit.MINUTES);
                if (stato == 401) {
                    nonAutorizzati++;
                } else if (stato == 429) {
                    bloccati++;
                } else {
                    throw new AssertionError("Esito inatteso: " + stato);
                }
            }
            assertEquals(LimitatoreAccessi.MAX_FALLIMENTI, nonAutorizzati);
            assertEquals(tentativi - LimitatoreAccessi.MAX_FALLIMENTI, bloccati);
        } finally {
            pool.shutdownNow();
        }
    }

    //sessione e logout

    @Test
    @DisplayName("Il login ruota l'identificativo della sessione preesistente")
    void loginRuotaLaSessione() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        MockHttpSession preesistente = new MockHttpSession();
        String idPrima = preesistente.getId();
        assertEquals(401, statoDi(get("/api/richieste/mie"), preesistente));

        MvcResult esito = accedi(account.email(), PASSWORD, preesistente);

        assertEquals(204, esito.getResponse().getStatus());
        MockHttpSession dopo = sessione(esito);
        assertNotNull(dopo);
        assertNotEquals(idPrima, dopo.getId());
        assertTrue(autenticata(dopo));
        assertEquals(200, statoDi(get("/api/richieste/mie"), dopo));
    }

    @Test
    @DisplayName("Il logout termina la sessione e le richieste successive sono negate")
    void logoutTerminaLaSessione() throws Exception {
        MockHttpSession sessione = accedi(nuovoAccount("OPT", "ATTIVO"));
        assertEquals(200, statoDi(get("/api/richieste/mie"), sessione));

        mvc.perform(post("/api/auth/logout").with(csrf()).session(sessione))
                .andExpect(status().isNoContent());

        assertTrue(sessione.isInvalid());
        assertEquals(401, statoDi(get("/api/richieste/mie"), sessione));
    }

    //revoca con sessione aperta

    @Test
    @DisplayName("Revoca: la richiesta successiva riceve 401 senza dati, la sessione si chiude e il nuovo login è negato")
    void revocaChiudeLaSessioneSuApi() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        MockHttpSession sessione = accedi(account);
        assertEquals(200, statoDi(get("/api/richieste/mie"), sessione));

        revoca(account);

        mvc.perform(get("/api/richieste/mie").session(sessione))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
        assertTrue(sessione.isInvalid());
        assertEquals(401, statoDi(get("/api/richieste/mie"), sessione));
        assertEquals(401, accedi(account.email(), PASSWORD, null).getResponse().getStatus());
    }

    @Test
    @DisplayName("Revoca: una pagina HTML rimanda a /login?error e chiude la sessione")
    void revocaChiudeLaSessioneSuPaginaHtml() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        MockHttpSession sessione = accedi(account);
        revoca(account);

        mvc.perform(get("/opt/dashboard").session(sessione))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"));
        assertTrue(sessione.isInvalid());
    }

    @Test
    @DisplayName("Revoca: stesso esito qualunque metodo, con o senza token CSRF")
    void revocaPrimaDiCsrfEPermessi() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        MockHttpSession conToken = accedi(account);
        MockHttpSession senzaToken = accedi(account);
        revoca(account);

        mvc.perform(post("/api/richieste").with(csrf()).session(conToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/richieste").session(senzaToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from richieste where titolare_id = ?", Integer.class, account.id()));
    }

    @Test
    @DisplayName("Revoca: l'esito non dipende dall'area, nemmeno se il ruolo non basterebbe")
    void revocaPrimaDeiPermessiDiRuolo() throws Exception {
        Account account = nuovoAccount("OPERATORE", "ATTIVO");
        MockHttpSession sessione = accedi(account);
        revoca(account);

        mvc.perform(get("/banco/dashboard").session(sessione))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"));
    }

    //CSRF

    @Test
    @DisplayName("Con sessione valida, un POST senza token CSRF riceve 403 e non modifica nulla")
    void postSenzaTokenConSessioneValida() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        MockHttpSession sessione = accedi(account);

        mvc.perform(post("/api/richieste").session(sessione)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"misuraId\":1,\"tipoDestinatario\":\"PERSONA\",\"comuneCodice\":\"napoli\"}"))
                .andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from richieste where titolare_id = ?", Integer.class, account.id()));

        mvc.perform(post("/api/auth/logout").session(sessione)).andExpect(status().isForbidden());
        assertFalse(sessione.isInvalid());
        assertEquals(200, statoDi(get("/api/richieste/mie"), sessione));
    }

    @Test
    @DisplayName("Senza sessione e senza token CSRF: 401 per le API, 403 per le pagine HTML")
    void postSenzaSessioneESenzaToken() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@picus.test\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/richieste").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/login").accept(MediaType.TEXT_HTML)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("username=a%40picus.test&password=x"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Il token ottenuto da /api/auth/csrf è accettato nella stessa sessione, uno falso no")
    void tokenDelServerEAccettato() throws Exception {
        Account account = nuovoAccount("OPT", "ATTIVO");
        MockHttpSession sessione = new MockHttpSession();
        String corpo = mvc.perform(get("/api/auth/csrf").session(sessione))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(corpo, "$.token");
        String intestazione = JsonPath.read(corpo, "$.headerName");

        mvc.perform(post("/api/auth/login").session(sessione).header(intestazione, "token-falso")
                        .contentType(MediaType.APPLICATION_JSON).content(credenziali(account.email(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").session(sessione).header(intestazione, token)
                        .contentType(MediaType.APPLICATION_JSON).content(credenziali(account.email(), PASSWORD)))
                .andExpect(status().isNoContent());
    }

    //helper

    private Account nuovoAccount(String ruolo, String stato) {
        UUID id = UUID.randomUUID();
        String email = "t-" + id + "@picus.test";
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, ?, ?, ?, ?)
                """, id, email, hash, "Account di prova", ruolo, stato);
        return new Account(id, email);
    }

    private void revoca(Account account) {
        jdbc.update("update utenze_account set stato = 'REVOCATO' where id = ?", account.id());
    }

    private MvcResult accedi(String email, String password, MockHttpSession preesistente) throws Exception {
        MockHttpServletRequestBuilder richiesta = post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(credenziali(email, password));
        if (preesistente != null) {
            richiesta.session(preesistente);
        }
        return mvc.perform(richiesta).andReturn();
    }

    /** Login con le credenziali valide di un account di prova, con esito 204 atteso. */
    private MockHttpSession accedi(Account account) throws Exception {
        MvcResult esito = accedi(account.email(), PASSWORD, null);
        assertEquals(204, esito.getResponse().getStatus());
        return sessione(esito);
    }

    private int statoDi(MockHttpServletRequestBuilder richiesta, MockHttpSession sessione) throws Exception {
        if (sessione != null) {
            richiesta.session(sessione);
        }
        return mvc.perform(richiesta).andReturn().getResponse().getStatus();
    }

    private static String credenziali(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static MockHttpSession sessione(MvcResult esito) {
        return (MockHttpSession) esito.getRequest().getSession(false);
    }

    private static boolean autenticata(MockHttpSession sessione) {
        return sessione != null && !sessione.isInvalid()
                && sessione.getAttribute(SPRING_SECURITY_CONTEXT_KEY) != null;
    }
}

