package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.AccessiBlackBoxTest;
import it.unina.picus.shared.Capacita;
import it.unina.picus.shared.ClientHttp;
import it.unina.picus.shared.ClientHttp.Csrf;
import it.unina.picus.shared.ClientHttp.SessioneAnonima;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.shared.ScenarioAccessi.ContattoPreparato;

/**
 * #28 — CA-08 (richiesta protetta senza sessione valida, anche scaduta o revocata: {@code 401}, senza
 * dati né modifiche; le funzioni pubbliche di accesso seguono le proprie regole) e CA-09 lato HTTP
 * (parametri alterati non concedono accessi).
 *
 * <p>Il server è chiamato direttamente con {@link ClientHttp}. I casi sono separati in due gruppi:</p>
 * <ul>
 *   <li>un solo test del <b>filtro di sicurezza generale</b>, su un percorso volutamente non mappato:
 *       dimostra che qualunque {@code /api/**} anonimo riceve {@code 401} senza redirect;</li>
 *   <li>i test sulle <b>risorse reali</b>: prima di attribuire l'esito a una risorsa verificano che
 *       metodo e percorso abbiano un handler, e un diniego {@code 403} deve avere il corpo vuoto.</li>
 * </ul>
 *
 * <p>Su {@code main} sono esposti i contatti di disponibilità ({@link #CONSULTA_CONTATTO},
 * {@link #RISPONDI_CONTATTO}, {@link #CONTATTI_RICHIESTA}) e il dettaglio della richiesta
 * ({@link #CONSULTA_RICHIESTA}): se manca un loro handler il caso fallisce. L'annullamento
 * ({@link #ANNULLA_RICHIESTA}) appartiene a {@link Capacita#RICHIESTE_COMPLETE}: finché la capacità non è
 * consegnata i suoi casi si sospendono; quando sarà consegnata, l'assenza dell'handler per la stessa
 * richiesta JSON usata dal test farà fallire il caso.</p>
 *
 * <p>Tecniche: test negativi di sicurezza su sessione assente, scaduta e revocata e su parametri
 * alterati, con un controllo positivo {@code 200} sulla stessa risorsa prima di ogni scadenza o revoca
 * e una rilettura del Banco per verificare che nulla sia cambiato.</p>
 *
 * <p>Scelte da confermare con il team:</p>
 * <ul>
 *   <li>le POST senza autenticazione portano il token CSRF di una sessione anonima, così il {@code 401}
 *       dipende dall'autenticazione e non da un diniego CSRF;</li>
 *   <li>la scadenza si simula invalidando la sessione autenticata, come fa il server al timeout.</li>
 * </ul>
 */
@DisplayName("#28 CA-08/CA-09 · Sessione e richieste dirette al server")
class SessioneHttpAccessiTest extends AccessiBlackBoxTest {

    /** Consultazione di un contatto di disponibilità ({@code ContattiDisponibilitaController}). */
    static final String CONSULTA_CONTATTO = "/api/contatti-disponibilita/{id}";
    /** Risposta a un contatto di disponibilità ({@code ContattiDisponibilitaController}). */
    static final String RISPONDI_CONTATTO = "/api/contatti-disponibilita/{id}/risposta";
    /** Storico dei contatti di una richiesta, riservato al Banco ({@code ContattiDisponibilitaController}). */
    static final String CONTATTI_RICHIESTA = "/api/richieste/{id}/contatti";
    /** Dettaglio di una richiesta ({@code RichiesteController}). */
    static final String CONSULTA_RICHIESTA = "/api/richieste/{id}";
    /** Annullamento, non ancora esposto: percorso e corpo JSON proposti, da allineare al controller. */
    static final String ANNULLA_RICHIESTA = "/api/richieste/{id}/annullamento";
    /** Percorso volutamente senza controller, per il solo test del filtro di sicurezza generale. */
    static final String NON_MAPPATO = "/api/percorso-non-mappato-28/{id}";

    /** Motivo sintetico degli annullamenti tentati. */
    private static final String MOTIVO = "Tentativo di annullamento #28";
    /** Corpo della risposta a un contatto. */
    private static final String DISPONIBILE = "{\"disponibile\":true}";

    @Autowired
    private WebApplicationContext web;

    private ClientHttp http;

    @BeforeEach
    void preparaClient() {
        http = new ClientHttp(web);
    }

    // ------------------------------------------------------------------ filtro di sicurezza generale

    @Test
    @DisplayName("CA-08 · filtro di sicurezza: qualunque /api/** anonimo → 401 senza redirect (percorso non mappato)")
    void filtroDiSicurezzaSuApiNonMappata() throws Exception {
        UUID id = UUID.randomUUID();
        SessioneAnonima anonima = http.sessioneAnonima();
        MockHttpServletRequestBuilder lettura = get(NON_MAPPATO, id).accept(MediaType.APPLICATION_JSON);
        MockHttpServletRequestBuilder scrittura = post(NON_MAPPATO, id).session(anonima.sessione())
                .header(anonima.csrf().header(), anonima.csrf().token())
                .contentType(MediaType.APPLICATION_JSON).content("{}").accept(MediaType.APPLICATION_JSON);
        assertFalse(http.mappato(lettura), "il percorso deve restare senza controller");
        assertFalse(http.mappato(scrittura), "il percorso deve restare senza controller");

        MockHttpServletResponse esitoLettura = http.esegui(lettura);
        MockHttpServletResponse esitoScrittura = http.esegui(scrittura);

        assertEquals(401, esitoLettura.getStatus());
        assertNull(esitoLettura.getHeader("Location"), "una API non deve rimandare al login");
        assertEquals(401, esitoScrittura.getStatus());
        assertNull(esitoScrittura.getHeader("Location"), "una API non deve rimandare al login");
    }

    // ------------------------------------------------------------------ risorse reali senza sessione

    /** Risorse protette e già consegnate su cui si verifica il {@code 401} anonimo, con un dato reale preparato. */
    enum RisorsaProtetta {
        CONSULTA_CONTATTO(HttpMethod.GET, SessioneHttpAccessiTest.CONSULTA_CONTATTO, true),
        RISPONDI_CONTATTO(HttpMethod.POST, SessioneHttpAccessiTest.RISPONDI_CONTATTO, true),
        CONTATTI_RICHIESTA(HttpMethod.GET, SessioneHttpAccessiTest.CONTATTI_RICHIESTA, false),
        CONSULTA_RICHIESTA(HttpMethod.GET, SessioneHttpAccessiTest.CONSULTA_RICHIESTA, false);

        final HttpMethod metodo;
        final String percorso;
        final boolean suContatto;

        RisorsaProtetta(HttpMethod metodo, String percorso, boolean suContatto) {
            this.metodo = metodo;
            this.percorso = percorso;
            this.suContatto = suContatto;
        }
    }

    @ParameterizedTest(name = "CA-08 · {0} senza sessione → 401 senza redirect, dati né modifiche")
    @EnumSource(RisorsaProtetta.class)
    void risorsaRealeSenzaSessioneRisponde401(RisorsaProtetta risorsa) throws Exception {
        ContattoPreparato contatto = scenario.contattoInAttesa(scenario.optRichiedente(), scenario.optRichiedente());
        UUID id = risorsa.suContatto ? contatto.contattoId() : contatto.richiestaId();
        MockHttpServletRequestBuilder richiesta = senzaIdentita(risorsa.metodo, risorsa.percorso, id);
        http.richiediEndpointConsegnato(richiesta);

        MockHttpServletResponse risposta = http.esegui(richiesta);

        assertEquals(401, risposta.getStatus());
        assertNull(risposta.getHeader("Location"), "una API non deve rimandare al login");
        assertFalse(risposta.getContentAsString().contains(id.toString()), "nessun dato della risorsa");
        assertEquals("IN_ATTESA", esitoPerIlBanco(contatto), "il contatto non deve cambiare");
    }

    @Test
    @DisplayName("CA-08 · l'accesso è raggiungibile senza sessione e apre una sessione valida")
    void loginPubblico() throws Exception {
        Account destinatario = scenario.optRichiedente();
        ContattoPreparato contatto = scenario.contattoInAttesa(scenario.optRichiedente(), destinatario);

        MockHttpSession sessione = http.accedi(destinatario);
        http.richiediEndpointConsegnato(leggiContatto(sessione, contatto));

        assertEquals(200, http.esegui(leggiContatto(sessione, contatto)).getStatus(),
                "dopo l'accesso il destinatario consulta il proprio contatto");
    }

    // ------------------------------------------------------------------ scadenza e revoca su un contatto reale

    @Test
    @DisplayName("CA-08 · su un contatto reale la sessione scaduta riceve 401 in lettura e in risposta; nulla cambia")
    void sessioneScadutaSuContattoReale() throws Exception {
        Account destinatario = scenario.optRichiedente();
        ContattoPreparato contatto = preparaContattoAccessibile(destinatario);
        MockHttpSession sessione = http.accedi(destinatario);
        Csrf csrf = verificaAccessoPrima(sessione, contatto);

        // Scadenza: il server invalida la sessione autenticata come al timeout; il client la ripresenta.
        sessione.invalidate();

        verificaSessioneChiusa(sessione, csrf, contatto);
    }

    @Test
    @DisplayName("CA-08 · su un contatto reale la sessione di un account revocato riceve 401 in lettura e in risposta; nulla cambia")
    void sessioneRevocataSuContattoReale() throws Exception {
        Account destinatario = scenario.optRichiedente();
        ContattoPreparato contatto = preparaContattoAccessibile(destinatario);
        MockHttpSession sessione = http.accedi(destinatario);
        Csrf csrf = verificaAccessoPrima(sessione, contatto);

        scenario.revoca(destinatario);

        verificaSessioneChiusa(sessione, csrf, contatto);
    }

    // ------------------------------------------------------------------ richieste

    @Test
    @DisplayName("CA-08 · una sessione autenticata e poi scaduta riceve 401 e la pratica resta invariata")
    void sessioneScadutaSullaRichiesta() throws Exception {
        http.richiediEndpointConsegnato(leggiRichiesta(new MockHttpSession(), UUID.randomUUID()));
        Account titolare = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);
        MockHttpSession sessione = http.accedi(titolare);
        assertEquals(200, http.esegui(leggiRichiesta(sessione, id)).getStatus(), "prima della scadenza");
        var prima = scenario.istantanea(id);

        sessione.invalidate();
        MockHttpServletResponse risposta = http.esegui(leggiRichiesta(sessione, id));

        assertEquals(401, risposta.getStatus());
        assertNull(risposta.getHeader("Location"), "una API non deve rimandare al login");
        assertFalse(risposta.getContentAsString().contains(id.toString()), "nessun dato della richiesta");
        assertEquals(prima, scenario.istantanea(id));
    }

    @Test
    @DisplayName("CA-08 · dopo la revoca dell'account la sessione aperta riceve 401 sulla richiesta senza dati")
    void sessioneRevocataSullaRichiesta() throws Exception {
        http.richiediEndpointConsegnato(leggiRichiesta(new MockHttpSession(), UUID.randomUUID()));
        Account titolare = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);
        MockHttpSession sessione = http.accedi(titolare);
        assertEquals(200, http.esegui(leggiRichiesta(sessione, id)).getStatus(), "prima della revoca");
        var prima = scenario.istantanea(id);

        scenario.revoca(titolare);
        MockHttpServletResponse risposta = http.esegui(leggiRichiesta(sessione, id));

        assertEquals(401, risposta.getStatus());
        assertFalse(risposta.getContentAsString().contains(id.toString()), "nessun dato della richiesta");
        assertEquals(prima, scenario.istantanea(id));
    }

    @Test
    @DisplayName("CA-08 · un annullamento senza sessione su una pratica reale riceve 401 e nulla cambia")
    void annullamentoSenzaSessioneNonModifica() throws Exception {
        http.richiediEndpointDellaCapacita(Capacita.RICHIESTE_COMPLETE, annulla(UUID.randomUUID(), MOTIVO));
        UUID id = scenario.richiestaCreata(scenario.optRichiedente());
        var prima = scenario.istantanea(id);

        SessioneAnonima anonima = http.sessioneAnonima();
        MockHttpServletResponse risposta = http.esegui(annulla(id, MOTIVO).session(anonima.sessione())
                .header(anonima.csrf().header(), anonima.csrf().token()));

        assertEquals(401, risposta.getStatus());
        assertFalse(risposta.getContentAsString().contains(id.toString()), "nessun dato della richiesta");
        assertEquals(prima, scenario.istantanea(id), "stato e dati invariati");
    }

    @Test
    @DisplayName("CA-08 · dopo la revoca un annullamento con la sessione aperta riceve 401 e nulla cambia")
    void annullamentoDopoRevocaNonModifica() throws Exception {
        http.richiediEndpointDellaCapacita(Capacita.RICHIESTE_COMPLETE, annulla(UUID.randomUUID(), MOTIVO));
        Account titolare = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);
        MockHttpSession sessione = http.accedi(titolare);
        Csrf csrf = http.csrf(sessione);
        var prima = scenario.istantanea(id);

        scenario.revoca(titolare);
        MockHttpServletResponse risposta = http.esegui(annulla(id, MOTIVO).session(sessione)
                .header(csrf.header(), csrf.token()));

        assertEquals(401, risposta.getStatus());
        assertEquals(prima, scenario.istantanea(id), "stato e dati invariati");
    }

    @Test
    @DisplayName("CA-09 · ruolo, titolare, organizzazione e assegnazione alterati non aprono la pratica altrui")
    void parametriAlteratiNonConcedonoAccessi() throws Exception {
        http.richiediEndpointConsegnato(leggiRichiesta(new MockHttpSession(), UUID.randomUUID()));
        Account titolare = scenario.optRichiedente();
        Account estranea = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);
        MockHttpSession sessione = http.accedi(estranea);

        MockHttpServletResponse risposta = http.esegui(leggiRichiesta(sessione, id)
                .param("ruolo", "BANCO").param("titolareId", titolare.id().toString())
                .param("accountId", titolare.id().toString())
                .param("optId", titolare.id().toString()).param("organizzazioneId", titolare.id().toString())
                .param("erogatoreId", estranea.id().toString()).param("assegnatarioId", estranea.id().toString())
                .header("X-Ruolo", "BANCO"));

        ClientHttp.assertDiniegoSenzaCorpo(risposta);
    }

    @Test
    @DisplayName("CA-09 · organizzazione e assegnazione alterate non permettono di annullare la pratica altrui")
    void assegnazioneAlterataNonConcedeAnnullamento() throws Exception {
        http.richiediEndpointDellaCapacita(Capacita.RICHIESTE_COMPLETE, annulla(UUID.randomUUID(), MOTIVO));
        Account titolare = scenario.optRichiedente();
        Account estranea = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);
        MockHttpSession sessione = http.accedi(estranea);
        Csrf csrf = http.csrf(sessione);
        var prima = scenario.istantanea(id);

        MockHttpServletResponse risposta = http.esegui(annulla(id, MOTIVO).session(sessione)
                .header(csrf.header(), csrf.token())
                .param("ruolo", "BANCO").param("optId", titolare.id().toString())
                .param("organizzazioneId", titolare.id().toString()).param("titolareId", estranea.id().toString())
                .param("erogatoreId", estranea.id().toString()).param("assegnatarioId", estranea.id().toString()));

        ClientHttp.assertDiniegoSenzaCorpo(risposta);
        assertEquals(prima, scenario.istantanea(id), "stato e dati invariati");
    }

    // ------------------------------------------------------------------ supporto

    /** Contatto in attesa intestato al destinatario, dopo aver verificato che le rotte usate siano esposte. */
    private ContattoPreparato preparaContattoAccessibile(Account destinatario) throws Exception {
        ContattoPreparato contatto = scenario.contattoInAttesa(scenario.optRichiedente(), destinatario);
        MockHttpSession qualunque = new MockHttpSession();
        http.richiediEndpointConsegnato(leggiContatto(qualunque, contatto));
        http.richiediEndpointConsegnato(rispondi(contatto));
        http.richiediEndpointConsegnato(get(CONTATTI_RICHIESTA, contatto.richiestaId()).accept(MediaType.APPLICATION_JSON));
        return contatto;
    }

    /** Controllo positivo: con la sessione valida il destinatario legge il contatto ({@code 200}, dati minimi). */
    private Csrf verificaAccessoPrima(MockHttpSession sessione, ContattoPreparato contatto) throws Exception {
        MockHttpServletResponse prima = http.esegui(leggiContatto(sessione, contatto));
        assertEquals(200, prima.getStatus(), "prima della scadenza o revoca la sessione è valida");
        Map<String, Object> corpo = JsonPath.read(prima.getContentAsString(), "$");
        assertEquals(contatto.contattoId().toString(), JsonPath.read(prima.getContentAsString(), "$.contatto.id"));
        assertEquals(Set.of("contatto", "misuraId", "nomeMisura", "tipoDestinatario", "comuneCodice"),
                corpo.keySet(), "solo i dati minimi del contatto");
        return http.csrf(sessione);
    }

    /** Dopo scadenza o revoca: GET e POST danno 401 senza redirect né dati e il Banco rilegge IN_ATTESA. */
    private void verificaSessioneChiusa(MockHttpSession sessione, Csrf csrf, ContattoPreparato contatto)
            throws Exception {
        MockHttpServletResponse lettura = http.esegui(leggiContatto(sessione, contatto));
        MockHttpServletResponse risposta = http.esegui(rispondi(contatto).session(sessione)
                .header(csrf.header(), csrf.token()));

        for (MockHttpServletResponse esito : List.of(lettura, risposta)) {
            assertEquals(401, esito.getStatus());
            assertNull(esito.getHeader("Location"), "una API non deve rimandare al login");
            assertFalse(esito.getContentAsString().contains(contatto.contattoId().toString()), "nessun dato");
        }
        assertEquals("IN_ATTESA", esitoPerIlBanco(contatto), "il tentativo non deve registrare la risposta");
    }

    /** Esito del contatto riletto dal Banco tramite lo storico dei contatti. */
    private String esitoPerIlBanco(ContattoPreparato contatto) throws Exception {
        MockHttpSession banco = http.accedi(scenario.banco());
        MockHttpServletResponse storico = http.esegui(get(CONTATTI_RICHIESTA, contatto.richiestaId()).session(banco)
                .accept(MediaType.APPLICATION_JSON));
        assertEquals(200, storico.getStatus(), "il Banco consulta lo storico dei contatti");
        List<String> esiti = JsonPath.read(storico.getContentAsString(),
                "$[?(@.id == '" + contatto.contattoId() + "')].esito");
        assertEquals(1, esiti.size(), "il contatto deve comparire nello storico");
        return esiti.getFirst();
    }

    /** POST anonime con token CSRF di una sessione anonima, così il {@code 401} non dipende dal CSRF. */
    private MockHttpServletRequestBuilder senzaIdentita(HttpMethod metodo, String percorso, UUID id)
            throws Exception {
        if (metodo == HttpMethod.POST) {
            SessioneAnonima anonima = http.sessioneAnonima();
            return post(percorso, id).session(anonima.sessione())
                    .header(anonima.csrf().header(), anonima.csrf().token())
                    .contentType(MediaType.APPLICATION_JSON).content(DISPONIBILE).accept(MediaType.APPLICATION_JSON);
        }
        return get(percorso, id).accept(MediaType.APPLICATION_JSON);
    }

    private static MockHttpServletRequestBuilder leggiContatto(MockHttpSession sessione, ContattoPreparato contatto) {
        return get(CONSULTA_CONTATTO, contatto.contattoId()).session(sessione).accept(MediaType.APPLICATION_JSON);
    }

    private static MockHttpServletRequestBuilder leggiRichiesta(MockHttpSession sessione, UUID id) {
        return get(CONSULTA_RICHIESTA, id).session(sessione).accept(MediaType.APPLICATION_JSON);
    }

    /** Risposta positiva al contatto, nel formato JSON del controller. */
    private static MockHttpServletRequestBuilder rispondi(ContattoPreparato contatto) {
        return post(RISPONDI_CONTATTO, contatto.contattoId())
                .contentType(MediaType.APPLICATION_JSON).content(DISPONIBILE).accept(MediaType.APPLICATION_JSON);
    }

    /**
     * Annullamento con motivo nel corpo JSON, come gli altri comandi delle API. La verifica del mapping usa
     * la stessa richiesta, così il caso si attiva quando il controller esporrà questo formato.
     */
    private static MockHttpServletRequestBuilder annulla(UUID id, String motivo) {
        return post(ANNULLA_RICHIESTA, id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"" + motivo + "\"}").accept(MediaType.APPLICATION_JSON);
    }
}
