package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import it.unina.picus.utenze.web.IntegrazionePostgresBase;
import java.net.URI;

import jakarta.servlet.http.Cookie;

/*Login dal form HTML ({@code POST /login}), logout e risposte senza sessione delle pagine HTML
(#25, #104), con la catena di sicurezza reale e PostgreSQL reale.
Ogni test usa email uniche e azzera il limitatore, così i test non dipendono dall'ordine.
*/
@DisplayName("Login dal form, logout e pagine HTML su PostgreSQL (#25)")
class LoginFormELogoutPostgresTest extends IntegrazionePostgresBase {

    private static final String PASSWORD = "Password-di-prova-1";
    private static final BCryptPasswordEncoder BCRYPT_VELOCE = new BCryptPasswordEncoder(4);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private LimitatoreAccessi limitatore;

    @BeforeEach
    void prepara() {
        limitatore.azzeraTutto();
    }

    // login dal form

    @Test
    @DisplayName("Credenziali corrette dal form: redirect a / e poi all'area del ruolo")
    void loginDalFormApreLaSessione() throws Exception {
        String email = nuovoAccount("OPT", "ATTIVO");
        MockHttpSession sessione = new MockHttpSession();

        MvcResult esito = mvc.perform(dalForm(email, PASSWORD, sessione)).andReturn();

        assertEquals(302, esito.getResponse().getStatus());
        assertEquals("/", esito.getResponse().getRedirectedUrl());
        assertTrue(autenticata(sessione));
        MvcResult home = mvc.perform(get("/").session(sessione)).andReturn();
        assertEquals("/opt/dashboard", home.getResponse().getRedirectedUrl());
    }

    @Test
    @DisplayName("Il login dal form ruota l'identificativo della sessione preesistente")
    void loginDalFormRuotaLaSessione() throws Exception {
        String email = nuovoAccount("OPT", "ATTIVO");
        MockHttpSession preesistente = new MockHttpSession();
        String idPrima = preesistente.getId();

        // Come il cookie del browser: la richiesta dichiara l'ID della sessione anonima.
        MvcResult esito = mvc.perform(dalForm(email, PASSWORD, preesistente)
                        .with(richiesta -> {
                            richiesta.setRequestedSessionId(idPrima);
                            return richiesta;
                        }))
                .andReturn();

        assertEquals(302, esito.getResponse().getStatus());
        assertEquals("/", esito.getResponse().getRedirectedUrl());
        MockHttpSession dopo = (MockHttpSession) esito.getRequest().getSession(false);
        assertNotNull(dopo);
        assertTrue(autenticata(dopo));
        assertNotEquals(idPrima, dopo.getId());
    }

    @Test
    @DisplayName("Dal form, credenziali errate e account non attivi danno tutti /login?error e nessuna sessione")
    void stessoEsitoPubblicoDalForm() throws Exception {
        String attivo = nuovoAccount("OPT", "ATTIVO");
        Map<String, String[]> casi = new LinkedHashMap<>();
        casi.put("email sconosciuta", new String[] {"sconosciuta-" + UUID.randomUUID() + "@picus.test", PASSWORD});
        casi.put("password errata", new String[] {attivo, "sbagliata"});
        casi.put("account revocato", new String[] {nuovoAccount("OPT", "REVOCATO"), PASSWORD});
        casi.put("account in attesa di attivazione", new String[] {nuovoAccount("OPT", "IN_ATTESA_DI_ATTIVAZIONE"), PASSWORD});

        for (Map.Entry<String, String[]> caso : casi.entrySet()) {
            MockHttpSession sessione = new MockHttpSession();
            MvcResult esito = mvc.perform(dalForm(caso.getValue()[0], caso.getValue()[1], sessione)).andReturn();

            assertEquals("/login?error", esito.getResponse().getRedirectedUrl(), caso.getKey());
            assertFalse(autenticata(sessione), "sessione autenticata dopo: " + caso.getKey());
        }
    }

    @Test
    @DisplayName("Dal form, dopo cinque tentativi falliti il sesto rimanda a /login?attesa anche con la password giusta")
    void bloccoDalForm() throws Exception {
        String email = nuovoAccount("OPT", "ATTIVO");
        for (int i = 0; i < LimitatoreAccessi.MAX_FALLIMENTI; i++) {
            assertEquals("/login?error",
                    mvc.perform(dalForm(email, "sbagliata", null)).andReturn().getResponse().getRedirectedUrl());
        }
        MockHttpSession sessione = new MockHttpSession();

        MvcResult bloccato = mvc.perform(dalForm(email, PASSWORD, sessione)).andReturn();

        assertEquals("/login?attesa", bloccato.getResponse().getRedirectedUrl());
        assertFalse(autenticata(sessione));
    }

    @Test
    @DisplayName("Il limite è unico per JSON e form: i fallimenti via API bloccano anche il form")
    void limiteCondivisoTraJsonEForm() throws Exception {
        String email = nuovoAccount("OPT", "ATTIVO");
        for (int i = 0; i < LimitatoreAccessi.MAX_FALLIMENTI; i++) {
            MvcResult fallito = mvc.perform(post("/api/auth/login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"sbagliata\"}")).andReturn();
            assertEquals(401, fallito.getResponse().getStatus());
        }

        MvcResult bloccato = mvc.perform(dalForm(email, PASSWORD, null)).andReturn();

        assertEquals("/login?attesa", bloccato.getResponse().getRedirectedUrl());
    }

    @Test
    @DisplayName("Una pagina HTML richiesta senza sessione viene riaperta dopo il login dal form")
    void ritornoAllaPaginaRichiesta() throws Exception {
        String email = nuovoAccount("OPT", "ATTIVO");
        MockHttpSession sessione = new MockHttpSession();
        MvcResult anonimo = mvc.perform(get("/opt/dashboard").accept(MediaType.TEXT_HTML).session(sessione)).andReturn();
        assertEquals(302, anonimo.getResponse().getStatus());

        MvcResult esito = mvc.perform(dalForm(email, PASSWORD, sessione)).andReturn();

        String destinazione = esito.getResponse().getRedirectedUrl();
        assertNotNull(destinazione);
        assertEquals("/opt/dashboard", URI.create(destinazione).getPath(), "destinazione dopo il login: " + destinazione);    }

    // risposte senza sessione

    @Test
    @DisplayName("Senza sessione una pagina HTML rimanda al login (302), una richiesta JSON o XHR riceve 401 senza redirect")
    void senzaSessionePaginaEApi() throws Exception {
        MvcResult pagina = mvc.perform(get("/opt/dashboard").accept(MediaType.TEXT_HTML)).andReturn();
        assertEquals(302, pagina.getResponse().getStatus());
        assertNotNull(pagina.getResponse().getRedirectedUrl());
        assertTrue(pagina.getResponse().getRedirectedUrl().contains("/login"));

        MvcResult json = mvc.perform(get("/opt/dashboard").accept(MediaType.APPLICATION_JSON)).andReturn();
        assertEquals(401, json.getResponse().getStatus());
        assertNull(json.getResponse().getHeader("Location"));

        MvcResult xhr = mvc.perform(get("/opt/dashboard").header("X-Requested-With", "XMLHttpRequest")).andReturn();
        assertEquals(401, xhr.getResponse().getStatus());
        assertNull(xhr.getResponse().getHeader("Location"));
    }

    // logout

    @Test
    @DisplayName("Logout dal form: redirect a /login?logout, sessione chiusa e cookie di sessione cancellato")
    void logoutDalForm() throws Exception {
        MockHttpSession sessione = accediDalForm(nuovoAccount("OPT", "ATTIVO"));

        MvcResult esito = mvc.perform(post("/logout").with(csrf()).session(sessione)).andReturn();

        assertEquals("/login?logout", esito.getResponse().getRedirectedUrl());
        assertTrue(sessione.isInvalid());
        assertCookieDiSessioneCancellato(esito);
        assertEquals(401, mvc.perform(get("/api/richieste/mie").session(sessione)).andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("Logout via API: 204 e cookie di sessione cancellato")
    void logoutApiCancellaIlCookie() throws Exception {
        MockHttpSession sessione = accediDalForm(nuovoAccount("OPT", "ATTIVO"));

        MvcResult esito = mvc.perform(post("/api/auth/logout").with(csrf()).session(sessione)).andReturn();

        assertEquals(204, esito.getResponse().getStatus());
        assertTrue(sessione.isInvalid());
        assertCookieDiSessioneCancellato(esito);
    }

    @Test
    @DisplayName("Logout dal form senza token CSRF: 403 e la sessione resta valida")
    void logoutDalFormSenzaToken() throws Exception {
        MockHttpSession sessione = accediDalForm(nuovoAccount("OPT", "ATTIVO"));

        MvcResult esito = mvc.perform(post("/logout").accept(MediaType.TEXT_HTML).session(sessione)).andReturn();

        assertEquals(403, esito.getResponse().getStatus());
        assertFalse(sessione.isInvalid());
        assertTrue(autenticata(sessione));
    }

    // supporto

    private MockHttpServletRequestBuilder dalForm(String email, String password, MockHttpSession sessione) {
        MockHttpServletRequestBuilder richiesta = post("/login").with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", email)
                .param("password", password);
        if (sessione != null) {
            richiesta.session(sessione);
        }
        return richiesta;
    }

    /** Login dal form con credenziali valide, redirect a / atteso. */
    private MockHttpSession accediDalForm(String email) throws Exception {
        MockHttpSession sessione = new MockHttpSession();
        MvcResult esito = mvc.perform(dalForm(email, PASSWORD, sessione)).andReturn();
        assertEquals("/", esito.getResponse().getRedirectedUrl());
        assertTrue(autenticata(sessione));
        return sessione;
    }

    private String nuovoAccount(String ruolo, String stato) {
        UUID id = UUID.randomUUID();
        String email = "t-" + id + "@picus.test";
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, ?, ?, ?, ?)
                """, id, email, BCRYPT_VELOCE.encode(PASSWORD), "Account di prova", ruolo, stato);
        return email;
    }

    private static void assertCookieDiSessioneCancellato(MvcResult esito) {
        Cookie cookie = esito.getResponse().getCookie("JSESSIONID");
        assertNotNull(cookie, "il logout deve inviare la cancellazione del cookie JSESSIONID");
        assertEquals(0, cookie.getMaxAge());
    }

    private static boolean autenticata(MockHttpSession sessione) {
        return sessione != null && !sessione.isInvalid()
                && sessione.getAttribute(SPRING_SECURITY_CONTEXT_KEY) != null;
    }
}