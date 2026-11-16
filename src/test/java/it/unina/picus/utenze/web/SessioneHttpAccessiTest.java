package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import it.unina.picus.shared.AccessiBlackBoxTest;
import it.unina.picus.shared.ScenarioAccessi;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.utenze.api.UtenzeApi;

import jakarta.servlet.Filter;

/**
 * #28 — CA-08 (richiesta protetta senza autenticazione valida o con sessione scaduta: {@code 401},
 * senza dati né modifiche) e CA-09 lato HTTP (parametri alterati non concedono accessi).
 *
 * <p>Il server è chiamato direttamente, come nell'approccio di verifica di QS-01A, con le regole di
 * {@code SecurityConfig}: API sotto {@code /api/}, {@code 401} senza redirect, accesso pubblico con
 * email e password in JSON su {@link #LOGIN} e token CSRF letto da {@link #TOKEN_CSRF}. I contatti di
 * disponibilità usano i percorsi di {@code ContattiDisponibilitaController}; i percorsi delle richieste
 * ({@link #CONSULTA_RICHIESTA}, {@link #ANNULLA_RICHIESTA}) sono ancora proposti e vanno allineati al
 * controller di Richieste quando sarà integrato.</p>
 *
 * <p>Tecniche: test negativi di sicurezza su sessione assente, scaduta e revocata, e su parametri e
 * header alterati, con un controllo positivo prima di ogni scadenza o revoca.</p>
 *
 * <p>Scelte da confermare con il team:</p>
 * <ul>
 *   <li>i test dei parametri alterati si sospendono se il percorso risponde {@code 404};</li>
 *   <li>le POST senza autenticazione portano il token CSRF di una sessione anonima, così il {@code 401}
 *       dipende dall'autenticazione e non da un diniego CSRF;</li>
 *   <li>la sessione scaduta e la revoca si verificano anche su {@link #CONSULTA_CONTATTO}, già esposto,
 *       senza attendere il controller di Richieste;</li>
 *   <li>la scadenza si simula invalidando la sessione autenticata, come fa il server al timeout.</li>
 * </ul>
 */
@DisplayName("#28 CA-08/CA-09 · Sessione e richieste dirette al server")
class SessioneHttpAccessiTest extends AccessiBlackBoxTest {

    /** Percorso proposto per il dettaglio di una richiesta; da allineare al controller di Richieste. */
    static final String CONSULTA_RICHIESTA = "/api/richieste/{id}";
    /** Percorso proposto per l'annullamento; da allineare al controller di Richieste. */
    static final String ANNULLA_RICHIESTA = "/api/richieste/{id}/annullamento";
    /** Consultazione di un contatto di disponibilità, esposta da {@code ContattiDisponibilitaController}. */
    static final String CONSULTA_CONTATTO = "/api/contatti-disponibilita/{id}";
    /** Risposta a un contatto di disponibilità, esposta da {@code ContattiDisponibilitaController}. */
    static final String RISPONDI_CONTATTO = "/api/contatti-disponibilita/{id}/risposta";
    /** Token CSRF della sessione, pubblico ({@code SecurityConfig}). */
    static final String TOKEN_CSRF = "/api/auth/csrf";
    /** Accesso con email e password in JSON, pubblico ({@code LoginJsonConfigurer}). */
    static final String LOGIN = "/api/auth/login";

    private static final Pattern CAMPO_HEADER = Pattern.compile("\"headerName\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CAMPO_TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private WebApplicationContext web;

    private MockMvc mvc;

    @BeforeEach
    void preparaClient() {
        scenario.modulo(UtenzeApi.class); // senza modulo Utenze non esiste ancora l'autenticazione PICUS
        assumeTrue(web.containsBean("springSecurityFilterChain"), "catena di Spring Security assente: test sospeso");
        Filter sicurezza = web.getBean("springSecurityFilterChain", Filter.class);
        mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(sicurezza).build();
    }

    static Stream<Arguments> richiesteProtette() {
        UUID id = UUID.randomUUID();
        return Stream.of(
                Arguments.of("GET", "/api/richieste"),
                Arguments.of("POST", "/api/richieste"),
                Arguments.of("GET", "/api/richieste/" + id),
                Arguments.of("POST", "/api/richieste/" + id + "/annullamento"),
                Arguments.of("GET", "/api/contatti-disponibilita/" + id),
                Arguments.of("POST", "/api/contatti-disponibilita/" + id + "/risposta"),
                Arguments.of("GET", "/api/richieste/" + id + "/contatti"),
                Arguments.of("GET", "/api/schede/" + id),
                Arguments.of("GET", "/api/comunicazioni/" + id));
    }

    private static MockHttpServletRequestBuilder richiesta(String metodo, String percorso) {
        return ("POST".equals(metodo) ? post(percorso) : get(percorso)).accept(MediaType.APPLICATION_JSON);
    }

    /** Token CSRF restituito da {@link #TOKEN_CSRF}, con l'header in cui inviarlo. */
    private record Csrf(String header, String token) {}

    /** Sessione anonima con il proprio token CSRF: nessuna identità autenticata. */
    private record SessioneAnonima(MockHttpSession sessione, Csrf csrf) {}

    /** Legge il token CSRF della sessione indicata, oppure di una sessione nuova se {@code null}. */
    private MvcResult leggiCsrf(MockHttpSession sessione) throws Exception {
        MockHttpServletRequestBuilder builder = get(TOKEN_CSRF).accept(MediaType.APPLICATION_JSON);
        if (sessione != null) {
            builder.session(sessione);
        }
        return mvc.perform(builder).andReturn();
    }

    private static Csrf estraiCsrf(MvcResult esito) throws Exception {
        String corpo = esito.getResponse().getContentAsString();
        Matcher header = CAMPO_HEADER.matcher(corpo);
        Matcher token = CAMPO_TOKEN.matcher(corpo);
        assertEquals(200, esito.getResponse().getStatus(), TOKEN_CSRF + " deve essere pubblico");
        assertTrue(header.find() && token.find(), "risposta di " + TOKEN_CSRF + " senza headerName o token");
        return new Csrf(header.group(1), token.group(1));
    }

    private SessioneAnonima sessioneAnonima() throws Exception {
        MvcResult esito = leggiCsrf(null);
        Csrf csrf = estraiCsrf(esito);
        MockHttpSession sessione = (MockHttpSession) esito.getRequest().getSession(false);
        assertNotNull(sessione, "il token CSRF deve essere legato a una sessione");
        return new SessioneAnonima(sessione, csrf);
    }

    /**
     * Le POST portano un token CSRF valido di una sessione anonima: così il {@code 401} atteso dipende
     * dall'assenza di autenticazione e non da un diniego CSRF ({@code 403}).
     */
    private MockHttpServletRequestBuilder senzaIdentita(String metodo, String percorso) throws Exception {
        MockHttpServletRequestBuilder builder = richiesta(metodo, percorso);
        if ("POST".equals(metodo)) {
            SessioneAnonima anonima = sessioneAnonima();
            builder.session(anonima.sessione()).header(anonima.csrf().header(), anonima.csrf().token());
        }
        return builder;
    }

    @ParameterizedTest(name = "CA-08 · {0} {1} senza sessione → 401 senza redirect né dati")
    @MethodSource("richiesteProtette")
    void senzaAutenticazioneRisponde401(String metodo, String percorso) throws Exception {
        MockHttpServletResponse risposta = mvc.perform(senzaIdentita(metodo, percorso)).andReturn().getResponse();

        assertEquals(401, risposta.getStatus());
        assertNull(risposta.getHeader("Location"), "una API non deve rimandare al login");
        String identificativo = percorso.replaceAll("^/api/[a-z-]+/?", "").replaceAll("/.*$", "");
        if (!identificativo.isEmpty()) {
            assertFalse(risposta.getContentAsString().contains(identificativo), "nessun dato della risorsa");
        }
    }

    @Test
    @DisplayName("CA-08 · l'accesso resta un'operazione pubblica e segue le proprie regole")
    void loginPubblico() throws Exception {
        Account opt = scenario.optRichiedente();

        // credenziali errate: 401 con messaggio generico, senza redirect
        SessioneAnonima anonima = sessioneAnonima();
        MockHttpServletResponse errata = mvc.perform(login(anonima, opt.email(), "Password-errata-28!"))
                .andReturn().getResponse();
        assertEquals(401, errata.getStatus());
        assertNull(errata.getHeader("Location"), "il login JSON non rimanda ad altre pagine");

        // credenziali corrette senza sessione autenticata: accesso consentito
        MockHttpSession sessione = accedi(opt);
        assertNotEquals(401, mvc.perform(get(CONSULTA_CONTATTO, UUID.randomUUID()).session(sessione)
                .accept(MediaType.APPLICATION_JSON)).andReturn().getResponse().getStatus(),
                "dopo l'accesso la sessione è autenticata");
    }

    @Test
    @DisplayName("CA-08 · su un'API esposta una sessione autenticata e poi scaduta riceve 401 senza redirect")
    void sessioneScadutaSuApiEsposta() throws Exception {
        MockHttpSession sessione = accedi(scenario.optRichiedente());
        UUID contatto = UUID.randomUUID();
        assertNotEquals(401, mvc.perform(get(CONSULTA_CONTATTO, contatto).session(sessione)
                .accept(MediaType.APPLICATION_JSON)).andReturn().getResponse().getStatus(),
                "prima della scadenza la sessione è valida");

        sessione.invalidate();
        MockHttpServletResponse risposta = mvc.perform(get(CONSULTA_CONTATTO, contatto).session(sessione)
                .accept(MediaType.APPLICATION_JSON)).andReturn().getResponse();

        assertEquals(401, risposta.getStatus());
        assertNull(risposta.getHeader("Location"), "una API non deve rimandare al login");
    }

    @Test
    @DisplayName("CA-08 · su un'API esposta la sessione di un account revocato riceve 401 in lettura e in scrittura")
    void sessioneRevocataSuApiEsposta() throws Exception {
        Account opt = scenario.optRichiedente();
        MockHttpSession sessione = accedi(opt);
        Csrf csrf = estraiCsrf(leggiCsrf(sessione));
        UUID contatto = UUID.randomUUID();
        assertNotEquals(401, mvc.perform(get(CONSULTA_CONTATTO, contatto).session(sessione)
                .accept(MediaType.APPLICATION_JSON)).andReturn().getResponse().getStatus(),
                "prima della revoca la sessione è valida");

        scenario.revoca(opt);
        MockHttpServletResponse scrittura = mvc.perform(post(RISPONDI_CONTATTO, contatto).session(sessione)
                .header(csrf.header(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"disponibile\":true}").accept(MediaType.APPLICATION_JSON)).andReturn().getResponse();
        MockHttpServletResponse lettura = mvc.perform(get(CONSULTA_CONTATTO, contatto).session(sessione)
                .accept(MediaType.APPLICATION_JSON)).andReturn().getResponse();

        assertEquals(401, scrittura.getStatus(), "la revoca chiude la sessione alla richiesta successiva");
        assertNull(scrittura.getHeader("Location"), "una API non deve rimandare al login");
        assertEquals(401, lettura.getStatus(), "la sessione revocata non torna valida");
    }

    @Test
    @DisplayName("CA-08 · una sessione autenticata e poi scaduta riceve 401 e la pratica resta invariata")
    void sessioneScaduta() throws Exception {
        Account titolare = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);
        MockHttpSession sessione = accedi(titolare);
        assertNotEquals(401, mvc.perform(get(CONSULTA_RICHIESTA, id).session(sessione)
                .accept(MediaType.APPLICATION_JSON)).andReturn().getResponse().getStatus(),
                "prima della scadenza la sessione è valida");
        var prima = scenario.istantanea(id);

        // Scadenza: il server invalida la sessione autenticata come al timeout; il client la ripresenta.
        sessione.invalidate();
        MockHttpServletResponse risposta = mvc.perform(get(CONSULTA_RICHIESTA, id)
                .session(sessione).accept(MediaType.APPLICATION_JSON)).andReturn().getResponse();

        assertEquals(401, risposta.getStatus());
        assertNull(risposta.getHeader("Location"), "una API non deve rimandare al login");
        assertFalse(risposta.getContentAsString().contains(id.toString()), "nessun dato della richiesta");
        assertEquals(prima, scenario.istantanea(id));
    }

    @Test
    @DisplayName("CA-08 · un annullamento senza sessione su una pratica reale riceve 401 e nulla cambia")
    void annullamentoSenzaSessioneNonModifica() throws Exception {
        UUID id = scenario.richiestaCreata(scenario.optRichiedente());
        var prima = scenario.istantanea(id);
        SessioneAnonima anonima = sessioneAnonima();

        MockHttpServletResponse risposta = mvc.perform(post(ANNULLA_RICHIESTA, id).session(anonima.sessione())
                .header(anonima.csrf().header(), anonima.csrf().token()).param("motivo", "Tentativo senza sessione")
                .accept(MediaType.APPLICATION_JSON)).andReturn().getResponse();

        assertEquals(401, risposta.getStatus());
        assertFalse(risposta.getContentAsString().contains(id.toString()), "nessun dato della richiesta");
        assertEquals(prima, scenario.istantanea(id), "stato e dati invariati");
    }

    @Test
    @DisplayName("CA-08 · dopo la revoca un annullamento con la sessione aperta riceve 401 e nulla cambia")
    void annullamentoDopoRevocaNonModifica() throws Exception {
        Account titolare = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);
        MockHttpSession sessione = accedi(titolare);
        String csrf = csrfDellaSessione(sessione);
        var prima = scenario.istantanea(id);

        scenario.revoca(titolare);
        MockHttpServletResponse risposta = mvc.perform(post(ANNULLA_RICHIESTA, id).session(sessione)
                .param("_csrf", csrf).accept(MediaType.APPLICATION_JSON)).andReturn().getResponse();

        assertEquals(401, risposta.getStatus());
        assertEquals(prima, scenario.istantanea(id), "stato e dati invariati");
    }

    @Test
    @DisplayName("CA-08 · dopo la revoca dell'account la sessione aperta riceve 401 senza dati")
    void sessioneDiAccountRevocato() throws Exception {
        Account titolare = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);
        MockHttpSession sessione = accedi(titolare);
        assertNotEquals(401, mvc.perform(get(CONSULTA_RICHIESTA, id).session(sessione)
                .accept(MediaType.APPLICATION_JSON)).andReturn().getResponse().getStatus(),
                "prima della revoca la sessione è valida");

        scenario.revoca(titolare);
        MockHttpServletResponse risposta = mvc.perform(get(CONSULTA_RICHIESTA, id).session(sessione)
                .accept(MediaType.APPLICATION_JSON)).andReturn().getResponse();

        assertEquals(401, risposta.getStatus());
        assertFalse(risposta.getContentAsString().contains(id.toString()), "nessun dato della richiesta");
        assertEquals(StatoRichiesta.CREATA, scenario.istantanea(id).richiesta().stato());
    }

    @Test
    @DisplayName("CA-09 · ruolo e titolare alterati nella richiesta non aprono la pratica altrui")
    void parametriAlteratiNonConcedonoAccessi() throws Exception {
        Account titolare = scenario.optRichiedente();
        Account estranea = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);
        MockHttpSession sessione = accedi(estranea);

        MockHttpServletResponse risposta = mvc.perform(get(CONSULTA_RICHIESTA, id).session(sessione)
                .param("ruolo", "BANCO").param("titolareId", titolare.id().toString())
                .param("accountId", titolare.id().toString())
                .param("optId", titolare.id().toString()).param("organizzazioneId", titolare.id().toString())
                .param("erogatoreId", estranea.id().toString()).param("assegnatarioId", estranea.id().toString())
                .header("X-Ruolo", "BANCO").accept(MediaType.APPLICATION_JSON)).andReturn().getResponse();

        assumeTrue(risposta.getStatus() != 404,
                () -> CONSULTA_RICHIESTA + " non è ancora esposto: allineare il percorso al controller");
        assertEquals(403, risposta.getStatus());
        String corpo = risposta.getContentAsString();
        assertFalse(corpo.contains(ScenarioAccessi.DESCRIZIONE), "nessun dato della richiesta altrui");
        assertFalse(corpo.contains(titolare.id().toString()), "nessun dato della richiesta altrui");
    }

    @Test
    @DisplayName("CA-09 · organizzazione e assegnazione alterate non permettono di annullare la pratica altrui")
    void assegnazioneAlterataNonConcedeAnnullamento() throws Exception {
        Account titolare = scenario.optRichiedente();
        Account estranea = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);
        MockHttpSession sessione = accedi(estranea);
        String csrf = csrfDellaSessione(sessione);
        var prima = scenario.istantanea(id);

        MockHttpServletResponse risposta = mvc.perform(post(ANNULLA_RICHIESTA, id).session(sessione)
                .param("_csrf", csrf).param("motivo", "Tentativo con parametri alterati")
                .param("ruolo", "BANCO").param("optId", titolare.id().toString())
                .param("organizzazioneId", titolare.id().toString()).param("titolareId", estranea.id().toString())
                .param("erogatoreId", estranea.id().toString()).param("assegnatarioId", estranea.id().toString())
                .accept(MediaType.APPLICATION_JSON)).andReturn().getResponse();

        assumeTrue(risposta.getStatus() != 404,
                () -> ANNULLA_RICHIESTA + " non è ancora esposto: allineare il percorso al controller");
        assertEquals(403, risposta.getStatus());
        assertEquals(prima, scenario.istantanea(id), "stato e dati invariati");
    }

    /**
     * Parametro {@code _csrf} valido per la sessione autenticata. Il token viene riletto dopo l'accesso,
     * perché Spring Security lo rinnova quando la sessione si autentica.
     */
    private String csrfDellaSessione(MockHttpSession sessione) throws Exception {
        return estraiCsrf(leggiCsrf(sessione)).token();
    }

    /** Richiesta di accesso JSON con il token CSRF della sessione anonima. */
    private static MockHttpServletRequestBuilder login(SessioneAnonima anonima, String email, String password) {
        return post(LOGIN).session(anonima.sessione())
                .header(anonima.csrf().header(), anonima.csrf().token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .accept(MediaType.APPLICATION_JSON);
    }

    /** Apre una sessione con l'accesso JSON di {@code /api/auth/login}, come il client. */
    private MockHttpSession accedi(Account account) throws Exception {
        SessioneAnonima anonima = sessioneAnonima();

        MockHttpServletResponse esito = mvc.perform(login(anonima, account.email(), ScenarioAccessi.PASSWORD))
                .andReturn().getResponse();

        assertEquals(204, esito.getStatus(), "credenziali di prova rifiutate");
        return anonima.sessione(); // MockHttpSession conserva l'oggetto anche dopo il cambio di identificativo
    }
}
