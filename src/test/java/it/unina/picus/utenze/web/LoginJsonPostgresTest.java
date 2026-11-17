package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import it.unina.picus.utenze.web.IntegrazionePostgresBase;

/*Robustezza del login JSON {@code POST /api/auth/login} (#25, #104) con la catena di sicurezza
reale e PostgreSQL reale: qualunque corpo o formato non valido produce lo stesso {@code 401}
generico, mai un {@code 500}, e non autentica la sessione. Completa {@code AccessoLoginPostgresTest},
che copre gli esiti per ruolo, la revoca, il blocco e il CSRF.
Ogni test usa email uniche e azzera il limitatore, così i test non dipendono dall'ordine.
*/
@DisplayName("Robustezza del login JSON su PostgreSQL (#25)")
class LoginJsonPostgresTest extends IntegrazionePostgresBase {

    private static final String PASSWORD = "Password-di-prova-1";
    private static final String ERRORE_GENERICO = "{\"errore\":\"Credenziali non valide\"}";
    private static final String ERRORE_BLOCCO =
            "{\"errore\":\"Accesso temporaneamente bloccato. Riprova tra poco.\"}";
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

    // corpo della richiesta

    @ParameterizedTest(name = "corpo [{0}]: 401 generico")
    @ValueSource(strings = {
            "",
            "{",
            "null",
            "[]",
            "\"testo\"",
            "{}",
            "{\"email\":\"a@picus.test\"}",
            "{\"password\":\"x\"}",
            "{\"email\":null,\"password\":null}"})
    void corpoNonValido(String corpo) throws Exception {
        MvcResult esito = mvc.perform(login().contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();

        assertErroreGenerico(esito);
    }

    // formato della richiesta

    @ParameterizedTest(name = "Content-Type {0} con credenziali valide: 401 generico")
    @ValueSource(strings = {"text/plain", "application/x-www-form-urlencoded", "application/xml"})
    void contentTypeNonJson(String contentType) throws Exception {
        String email = nuovoAccount("OPT", "ATTIVO", PASSWORD);

        MvcResult esito = mvc.perform(login()
                .contentType(MediaType.parseMediaType(contentType))
                .content(credenziali(email, PASSWORD))).andReturn();

        assertErroreGenerico(esito);
    }

    @Test
    @DisplayName("Senza Content-Type, anche con credenziali valide: 401 generico")
    void senzaContentType() throws Exception {
        String email = nuovoAccount("OPT", "ATTIVO", PASSWORD);

        MvcResult esito = mvc.perform(login().content(credenziali(email, PASSWORD))).andReturn();

        assertErroreGenerico(esito);
    }

    @Test
    @DisplayName("Content-Type JSON con charset esplicito: accesso riuscito")
    void contentTypeJsonConCharset() throws Exception {
        String email = nuovoAccount("OPT", "ATTIVO", PASSWORD);

        MvcResult esito = mvc.perform(login()
                .contentType("application/json;charset=UTF-8")
                .content(credenziali(email, PASSWORD))).andReturn();

        assertEquals(204, esito.getResponse().getStatus());
        assertTrue(autenticata(esito));
    }

    // valori delle credenziali

    @Test
    @DisplayName("L'email è normalizzata: spazi e maiuscole non impediscono l'accesso")
    void emailNormalizzata() throws Exception {
        String email = nuovoAccount("OPT", "ATTIVO", PASSWORD);

        MvcResult esito = accedi("  " + email.toUpperCase(Locale.ROOT) + " ", PASSWORD);

        assertEquals(204, esito.getResponse().getStatus());
        assertTrue(autenticata(esito));
    }

    @Test
    @DisplayName("La password distingue le maiuscole: 401 generico")
    void passwordSensibileAlleMaiuscole() throws Exception {
        String email = nuovoAccount("OPT", "ATTIVO", PASSWORD);

        assertErroreGenerico(accedi(email, PASSWORD.toUpperCase(Locale.ROOT)));
    }

    @Test
    @DisplayName("Email più lunga di 254 caratteri: 401 generico, nessun errore del server")
    void emailTroppoLunga() throws Exception {
        assertErroreGenerico(accedi("a".repeat(300) + "@picus.test", PASSWORD));
    }

    @Test
    @DisplayName("Password multibyte di 72 byte accettata, oltre i 72 byte respinta")
    void limiteDi72ByteViaHttp() throws Exception {
        String password = "è".repeat(36); // 72 byte UTF-8
        String email = nuovoAccount("OPT", "ATTIVO", password);

        assertErroreGenerico(accedi(email, password + "x"));
        MvcResult riuscito = accedi(email, password);
        assertEquals(204, riuscito.getResponse().getStatus());
        assertTrue(autenticata(riuscito));
    }

    // blocco e informazioni rivelate

    @Test
    @DisplayName("Il blocco vale anche per un'email inesistente, con lo stesso messaggio di attesa")
    void bloccoAncheSenzaAccount() throws Exception {
        String inesistente = "sconosciuta-" + UUID.randomUUID() + "@picus.test";
        for (int i = 0; i < LimitatoreAccessi.MAX_FALLIMENTI; i++) {
            assertErroreGenerico(accedi(inesistente, "x"));
        }

        MvcResult bloccato = accedi(inesistente, "x");

        assertEquals(429, bloccato.getResponse().getStatus());
        assertEquals(ERRORE_BLOCCO, bloccato.getResponse().getContentAsString());
        assertFalse(autenticata(bloccato));
    }

    // bug noti, da riattivare quando saranno corretti

    @Test
    @Disabled("Bug #121: il parser conserva l'ultima proprietà duplicata e il login viene accettato")
    @DisplayName("Un corpo con la proprietà password duplicata è respinto")
    void passwordDuplicataRespinta() throws Exception {
        String email = nuovoAccount("OPT", "ATTIVO", PASSWORD);
        String corpo = "{\"email\":\"" + email + "\",\"password\":\"errata\",\"password\":\"" + PASSWORD + "\"}";

        MvcResult esito = mvc.perform(login().contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();

        assertErroreGenerico(esito);
    }

    @Test
    @Disabled("Bug #119: l'unicità dell'email in persistenza distingue le maiuscole e il login risponde 500")
    @DisplayName("Due email che differiscono solo per le maiuscole non possono coesistere")
    void emailEquivalentiNonCoesistono() throws Exception {
        String email = nuovoAccount("OPT", "ATTIVO", PASSWORD);

        assertThrows(DataIntegrityViolationException.class,
                () -> inserisci(UUID.randomUUID(), email.toUpperCase(Locale.ROOT), "ATTIVO", PASSWORD));
        assertEquals(204, accedi(email, PASSWORD).getResponse().getStatus());
    }

    // supporto

    private static MockHttpServletRequestBuilder login() {
        return post("/api/auth/login").with(csrf());
    }

    private MvcResult accedi(String email, String password) throws Exception {
        return mvc.perform(login().contentType(MediaType.APPLICATION_JSON).content(credenziali(email, password)))
                .andReturn();
    }

    private void assertErroreGenerico(MvcResult esito) throws Exception {
        assertEquals(401, esito.getResponse().getStatus());
        assertEquals(ERRORE_GENERICO, esito.getResponse().getContentAsString());
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(
                MediaType.parseMediaType(esito.getResponse().getContentType())));
        assertFalse(autenticata(esito), "sessione autenticata dopo un accesso respinto");
    }

    private String nuovoAccount(String ruolo, String stato, String password) {
        UUID id = UUID.randomUUID();
        String email = "t-" + id + "@picus.test";
        inserisci(id, email, stato, password, ruolo);
        return email;
    }

    private void inserisci(UUID id, String email, String stato, String password) {
        inserisci(id, email, stato, password, "OPT");
    }

    private void inserisci(UUID id, String email, String stato, String password, String ruolo) {
        jdbc.update("""
                insert into utenze_account (id, email, password_hash, nome, ruolo, stato)
                values (?, ?, ?, ?, ?, ?)
                """, id, email, BCRYPT_VELOCE.encode(password), "Account di prova", ruolo, stato);
    }

    /** Solo email e password senza virgolette o barre: basta la concatenazione. */
    private static String credenziali(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static boolean autenticata(MvcResult esito) {
        MockHttpSession sessione = (MockHttpSession) esito.getRequest().getSession(false);
        return sessione != null && !sessione.isInvalid()
                && sessione.getAttribute(SPRING_SECURITY_CONTEXT_KEY) != null;
    }
}
