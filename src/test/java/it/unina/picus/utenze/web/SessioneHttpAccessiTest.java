package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * <p>Il server è chiamato direttamente, come nell'approccio di verifica di QS-01A. Le API seguono la
 * convenzione già adottata da MVP-Login: percorsi sotto {@code /api/}, {@code 401} senza redirect.
 * I percorsi delle singole risorse sono quelli proposti in {@link #CONSULTA_RICHIESTA}: allinearli ai
 * controller quando saranno integrati.</p>
 *
 * <p>Tecniche: test negativi di sicurezza su sessione assente, scaduta e revocata, e su parametri e
 * header alterati, con un controllo positivo prima di ogni scadenza o revoca.</p>
 *
 * <p>Scelte da confermare con il team:</p>
 * <ul>
 *   <li>i test dei parametri alterati si sospendono se il percorso risponde {@code 404};</li>
 *   <li>le POST senza autenticazione portano il token CSRF di una sessione anonima, così il {@code 401}
 *       dipende dall'autenticazione e non da un diniego CSRF;</li>
 *   <li>la scadenza si simula invalidando la sessione autenticata, come fa il server al timeout.</li>
 * </ul>
 */
@DisplayName("#28 CA-08/CA-09 · Sessione e richieste dirette al server")
class SessioneHttpAccessiTest extends AccessiBlackBoxTest {

    /** Percorso proposto per il dettaglio di una richiesta; da allineare al controller di Richieste. */
    static final String CONSULTA_RICHIESTA = "/api/richieste/{id}";
    /** Percorso proposto per l'annullamento; da allineare al controller di Richieste. */
    static final String ANNULLA_RICHIESTA = "/api/richieste/{id}/annullamento";

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

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

    /** Sessione anonima con il token CSRF della pagina di accesso: nessuna identità autenticata. */
    private record SessioneAnonima(MockHttpSession sessione, String csrf) {}

    private SessioneAnonima sessioneAnonima() throws Exception {
        MvcResult pagina = mvc.perform(get("/login")).andReturn();
        MockHttpSession sessione = (MockHttpSession) pagina.getRequest().getSession(false);
        Matcher csrf = CSRF.matcher(pagina.getResponse().getContentAsString());
        assumeTrue(sessione != null && csrf.find(), "pagina di accesso con token CSRF non disponibile");
        return new SessioneAnonima(sessione, csrf.group(1));
    }

    /**
     * Le POST portano un token CSRF valido di una sessione anonima: così il {@code 401} atteso dipende
     * dall'assenza di autenticazione e non da un diniego CSRF ({@code 403}).
     */
    private MockHttpServletRequestBuilder senzaIdentita(String metodo, String percorso) throws Exception {
        MockHttpServletRequestBuilder builder = richiesta(metodo, percorso);
        if ("POST".equals(metodo)) {
            SessioneAnonima anonima = sessioneAnonima();
            builder.session(anonima.sessione()).param("_csrf", anonima.csrf());
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
    @DisplayName("CA-08 · l'accesso resta un'operazione pubblica")
    void loginPubblico() throws Exception {
        assertEquals(200, mvc.perform(get("/login")).andReturn().getResponse().getStatus());
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
                .param("_csrf", anonima.csrf()).param("motivo", "Tentativo senza sessione")
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
     * Token CSRF valido per la sessione autenticata, letto da una pagina della stessa sessione.
     * Si usa la pagina di accesso perché è l'unica pagina con form garantita in questa fase.
     */
    private String csrfDellaSessione(MockHttpSession sessione) throws Exception {
        String pagina = mvc.perform(get("/login").session(sessione)).andReturn().getResponse().getContentAsString();
        Matcher csrf = CSRF.matcher(pagina);
        assumeTrue(csrf.find(), "token CSRF della sessione autenticata non disponibile");
        return csrf.group(1);
    }

    /** Apre una sessione con il form di accesso, come l'utente. */
    private MockHttpSession accedi(Account account) throws Exception {
        SessioneAnonima anonima = sessioneAnonima();
        MockHttpSession sessione = anonima.sessione();

        MvcResult esito = mvc.perform(post("/login").session(sessione)
                .param("username", account.email())
                .param("password", ScenarioAccessi.PASSWORD)
                .param("_csrf", anonima.csrf())).andReturn();
        assertNotNull(esito.getResponse().getRedirectedUrl(), "accesso riuscito con redirect");
        assertFalse(esito.getResponse().getRedirectedUrl().contains("error"), "credenziali di prova rifiutate");
        return sessione; // MockHttpSession conserva l'oggetto anche dopo il cambio di identificativo
    }
}
