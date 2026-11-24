package it.unina.picus.monitoraggio.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

import it.unina.picus.shared.BlackBoxPostgresTest;
import it.unina.picus.shared.Capacita;
import it.unina.picus.shared.ClientHttp;
import it.unina.picus.shared.ClientHttp.Csrf;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * #57 Riepilogo delle schede per la rendicontazione: test black box (#183) sulle rotte HTTP reali.
 *
 * <p>Nei nomi {@code CA-nn} indica l'n-esimo criterio di accettazione nell'ordine della issue: CA-01 schede
 * ricevute nel periodo, CA-02 totali per voce, CA-03 consultazione, CA-04 categorie sovrapposte, CA-05 periodo
 * vuoto, CA-06 account non autorizzati. L'ultimo gruppo copre la scheda inviata prima della revoca (#30).</p>
 *
 * <p>Il database si usa solo per preparare gli account: schede e revoca passano dalle API. Ogni caso, riga
 * parametrica compresa, usa periodi conclusi riservati solo a lui negli anni 1950–1979, quindi gli elenchi del
 * Banco contengono soltanto le schede del caso, anche se le altre suite girano in parallelo.</p>
 *
 * <p>Tecniche: partizioni e valori di confine sul periodo (CA-01, CA-05), classi dei conteggi e somma oltre la
 * precisione dei numeri JavaScript (CA-02), casi d'uso con rilettura (CA-03, revoca), categorie sovrapposte
 * (CA-04), tabella di decisione ruolo × risorsa con controllo positivo (CA-06).</p>
 *
 * <p>Esecuzione: {@code ./mvnw test -Dgroups=US-57}; serve Docker.</p>
 */
@Tag("US-57")
@DisplayName("#57 · Riepilogo delle schede per la rendicontazione")
class RiepilogoSchedeBlackBoxTest extends BlackBoxPostgresTest {

    private static final String SCHEDE = "/api/monitoraggio/schede-operatore";
    private static final String RICEVUTE = SCHEDE + "/ricevute";
    private static final String RIEPILOGO = RICEVUTE + "/riepilogo";
    private static final String SCHEDA = SCHEDE + "/{id}";
    private static final String REVOCA = "/api/operatori/{id}/revoca";

    private static final TipoErogatore INTERNO = TipoErogatore.INTERNO_BANCO;
    private static final TipoErogatore ESTERNO = TipoErogatore.ESTERNO_PAGATO_BANCO;

    /** Numeri letti senza perdita di precisione. */
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .build();

    @Autowired
    private WebApplicationContext web;

    private ClientHttp http;

    /** La sospensione precede la preparazione dei dati di ogni caso. */
    @BeforeEach
    void preparaClient() {
        assumeTrue(Capacita.MONITORAGGIO.consegnata(), "capacità MONITORAGGIO non ancora consegnata: caso sospeso");
        http = new ClientHttp(web);
    }

    private record Periodo(int anno, int trimestre) {
        Map<String, Object> json() {
            return Map.of("anno", BigInteger.valueOf(anno), "numero", BigInteger.valueOf(trimestre));
        }

        @Override
        public String toString() {
            return anno + "-T" + trimestre;
        }
    }

    /** Scheda accettata, con il corpo JSON inviato e l'account del titolare. */
    private record Inviata(String id, Account titolare, Periodo periodo, String corpo) {}

    // ------------------------------------------------------------------ CA-01 schede ricevute nel periodo

    static Stream<Arguments> coppieDiPeriodi() {
        return Stream.of(
                Arguments.of("trimestri consecutivi dello stesso anno", new Periodo(1950, 1), new Periodo(1950, 2)),
                Arguments.of("trimestri consecutivi a cavallo d'anno", new Periodo(1951, 4), new Periodo(1952, 1)),
                Arguments.of("stesso trimestre dell'anno successivo", new Periodo(1953, 2), new Periodo(1954, 2)));
    }

    @ParameterizedTest(name = "CA-01 · {0}: {1} e {2}")
    @MethodSource("coppieDiPeriodi")
    @DisplayName("CA-01 · il Banco vede tutte e sole le schede del trimestre scelto, con i rispettivi operatori")
    void schedeDelPeriodo(String nome, Periodo p, Periodo q) throws Exception {
        Inviata interna = inviata(scenario.operatore(INTERNO), p, "{\"donne\":1}");
        Inviata esterna = inviata(scenario.operatore(ESTERNO), p, "{\"donne\":2}");
        Inviata altroPeriodo = inviata(scenario.operatore(INTERNO), q, "{\"donne\":4}");
        MockHttpSession banco = http.accedi(scenario.banco());

        assertSchede(ricevute(banco, p), interna, esterna);
        assertSchede(schedeDelRiepilogo(banco, p), interna, esterna);
        assertSchede(ricevute(banco, q), altroPeriodo);
        assertSchede(schedeDelRiepilogo(banco, q), altroPeriodo);
    }

    // ------------------------------------------------------------------ CA-02 totali per voce

    @Test
    @DisplayName("CA-02 · ogni voce è la somma dei conteggi delle sole schede del trimestre")
    void totaliDelPeriodo() throws Exception {
        Periodo p = new Periodo(1955, 2);
        Periodo q = new Periodo(1955, 3);
        Account interno = scenario.operatore(INTERNO);
        inviata(interno, p, "{\"donne\":3,\"minori\":0,\"anziani\":0,\"stranieri\":6}");
        inviata(scenario.operatore(ESTERNO), p, "{\"donne\":4,\"minori\":5,\"anziani\":0,\"senza_dimora\":0}");
        // stesso operatore in un altro trimestre: i suoi conteggi non devono entrare nei totali di P
        inviata(interno, q, "{\"donne\":100,\"minori\":200,\"solo_altro_periodo\":9}");
        MockHttpSession banco = http.accedi(scenario.banco());

        Map<String, Object> totali = conteggi(riepilogo(banco, p));
        assertEquals("7", totali.get("donne"), "voce dichiarata da entrambe le schede");
        assertEquals("5", totali.get("minori"), "zero di una scheda e valore dell'altra");
        assertEquals("0", totali.get("anziani"), "zero dichiarato da entrambe le schede");
        assertEquals("6", totali.get("stranieri"), "voce dichiarata da una sola scheda");
        assertEquals("0", totali.get("senza_dimora"), "zero dichiarato da una sola scheda");
        assertFalse(totali.containsKey("solo_altro_periodo"), "voce dichiarata solo nell'altro trimestre");

        Map<String, Object> totaliAltroPeriodo = conteggi(riepilogo(banco, q));
        assertEquals("100", totaliAltroPeriodo.get("donne"));
        assertEquals("200", totaliAltroPeriodo.get("minori"));
        assertEquals("9", totaliAltroPeriodo.get("solo_altro_periodo"));
        assertFalse(totaliAltroPeriodo.containsKey("stranieri"), "voce dichiarata solo nel primo trimestre");
    }

    @Test
    @DisplayName("CA-02 · il totale resta esatto oltre la precisione dei numeri JavaScript")
    void totaleEsatto() throws Exception {
        Periodo p = new Periodo(1956, 1);
        inviata(scenario.operatore(INTERNO), p, "{\"donne\":9007199254740991}"); // 2^53 - 1
        inviata(scenario.operatore(ESTERNO), p, "{\"donne\":2}");

        assertEquals("9007199254740993", conteggi(riepilogo(http.accedi(scenario.banco()), p)).get("donne"));
    }

    // ------------------------------------------------------------------ CA-03 consultazione

    static Stream<Arguments> tipologie() {
        return Stream.of(Arguments.of(INTERNO, new Periodo(1957, 1)), Arguments.of(ESTERNO, new Periodo(1957, 2)));
    }

    @ParameterizedTest(name = "CA-03 · scheda di {0} in {1}")
    @MethodSource("tipologie")
    @DisplayName("CA-03 · il Banco consulta i dati inviati e le letture lasciano invariata la scheda")
    void consultazione(TipoErogatore tipologia, Periodo periodo) throws Exception {
        Inviata inviata = inviata(scenario.operatore(tipologia), periodo, "{\"donne\":5,\"minori\":0}");
        MockHttpSession banco = http.accedi(scenario.banco());

        Map<String, Object> prima = scheda(banco, inviata.id());
        assertScheda(prima, inviata);
        assertEquals(List.of(prima.get("sintesi")), ricevute(banco, periodo), "stessi riferimenti della scheda");
        assertEquals(List.of(prima.get("sintesi")), schedeDelRiepilogo(banco, periodo));

        assertEquals(prima, scheda(banco, inviata.id()), "la consultazione non modifica la scheda");
    }

    // ------------------------------------------------------------------ CA-04 categorie sovrapposte

    /** Le tre minori della prima scheda sono anche donne: le voci restano due, ciascuna con il suo totale. */
    @Test
    @DisplayName("CA-04 · le categorie sovrapposte restano voci distinte e non vengono sommate fra loro")
    void categorieSovrapposte() throws Exception {
        Periodo p = new Periodo(1958, 1);
        inviata(scenario.operatore(INTERNO), p, "{\"donne\":3,\"minori\":3}");
        inviata(scenario.operatore(ESTERNO), p, "{\"donne\":4,\"minori\":2,\"stranieri\":8}");

        assertEquals(Map.of("donne", "7", "minori", "5", "stranieri", "8"),
                conteggi(riepilogo(http.accedi(scenario.banco()), p)), "tutte e sole le voci dichiarate");
    }

    // ------------------------------------------------------------------ CA-05 periodo vuoto

    static Stream<Arguments> periodiVuoti() {
        return Stream.of(Arguments.of(new Periodo(1959, 1), new Periodo(1959, 2)),
                Arguments.of(new Periodo(1960, 4), new Periodo(1961, 1)));
    }

    @ParameterizedTest(name = "CA-05 · schede in {0}, nessuna in {1}")
    @MethodSource("periodiVuoti")
    @DisplayName("CA-05 · un trimestre senza schede ha elenco e conteggi vuoti, senza dati di altri periodi")
    void periodoVuoto(Periodo precedente, Periodo vuoto) throws Exception {
        Inviata inviata = inviata(scenario.operatore(INTERNO), precedente, "{\"donne\":5}");
        MockHttpSession banco = http.accedi(scenario.banco());

        Map<String, Object> riepilogo = riepilogo(banco, vuoto);
        assertEquals(List.of(), riepilogo.get("schede"));
        assertEquals(Map.of(), riepilogo.get("conteggi"));
        assertEquals(List.of(), ricevute(banco, vuoto));

        // controllo positivo: la scheda esiste, ma solo nel suo periodo
        assertSchede(schedeDelRiepilogo(banco, precedente), inviata);
    }

    // ------------------------------------------------------------------ CA-06 account non autorizzati

    private enum Attore { OPT, OPERATORE_TITOLARE_NEL_PERIODO, OPERATORE_SENZA_SCHEDE, SESSIONE_ASSENTE }

    /** Risorse riservate; {@code SCHEDA_ALTRUI} è la scheda di un operatore diverso dall'attore. */
    private enum Risorsa {
        RICEVUTE, RIEPILOGO, SCHEDA_ALTRUI;

        MockHttpServletRequestBuilder richiesta(Inviata altrui) {
            return switch (this) {
                case RICEVUTE -> conPeriodo(get(RiepilogoSchedeBlackBoxTest.RICEVUTE), altrui.periodo());
                case RIEPILOGO -> conPeriodo(get(RiepilogoSchedeBlackBoxTest.RIEPILOGO), altrui.periodo());
                case SCHEDA_ALTRUI -> get(SCHEDA, altrui.id());
            };
        }
    }

    /** Matrice completa attore × risorsa, con un anno riservato a ogni attore e un trimestre a ogni risorsa. */
    static Stream<Arguments> accessiNegati() {
        return Stream.of(Attore.values()).flatMap(attore -> Stream.of(Risorsa.values()).map(risorsa ->
                Arguments.of(attore, risorsa, new Periodo(1965 + attore.ordinal(), 1 + risorsa.ordinal()))));
    }

    @ParameterizedTest(name = "CA-06 · {0} su {1} ({2})")
    @MethodSource("accessiNegati")
    @DisplayName("CA-06 · senza il permesso del Banco nessun dato riservato; il Banco riceve 200")
    void accessoNegato(Attore attore, Risorsa risorsa, Periodo periodo) throws Exception {
        Inviata altrui = inviata(scenario.operatore(INTERNO), periodo, "{\"donne\":5}");
        Inviata altra = inviata(scenario.operatore(ESTERNO), periodo, "{\"minori\":2}");
        MockHttpSession banco = http.accedi(scenario.banco());
        Map<String, Object> prima = statoDalBanco(banco, periodo, altrui, altra);

        MockHttpSession sessione = switch (attore) {
            case OPT -> http.accedi(scenario.optRichiedente());
            case OPERATORE_TITOLARE_NEL_PERIODO -> http.accedi(altra.titolare());
            case OPERATORE_SENZA_SCHEDE -> http.accedi(scenario.operatore(ESTERNO));
            case SESSIONE_ASSENTE -> null;
        };
        MockHttpServletResponse negata = leggi(sessione, risorsa.richiesta(altrui));
        if (attore == Attore.SESSIONE_ASSENTE) {
            ClientHttp.assertNonAutenticatoSenzaDati(negata);
        } else {
            ClientHttp.assertDiniegoSenzaCorpo(negata);
        }

        // controllo positivo: la stessa richiesta del Banco restituisce i dati
        Object consentita = corpo(leggi(banco, risorsa.richiesta(altrui)));
        switch (risorsa) {
            case RICEVUTE -> assertSchede(comeLista(consentita), altrui, altra);
            case RIEPILOGO -> {
                assertSchede(comeLista(comeMappa(consentita).get("schede")), altrui, altra);
                assertEquals("5", conteggi(comeMappa(consentita)).get("donne"));
                assertEquals("2", conteggi(comeMappa(consentita)).get("minori"));
            }
            case SCHEDA_ALTRUI -> assertScheda(comeMappa(consentita), altrui);
        }
        assertEquals(prima, statoDalBanco(banco, periodo, altrui, altra), "schede invariate dopo il diniego");
    }

    /** Dettagli e sintesi delle sole schede del caso, riletti dal Banco. */
    private Map<String, Object> statoDalBanco(MockHttpSession banco, Periodo periodo, Inviata... schede)
            throws Exception {
        Map<String, Object> stato = new TreeMap<>();
        List<Map<String, Object>> ricevute = ricevute(banco, periodo);
        for (Inviata inviata : schede) {
            List<Map<String, Object>> sintesi = ricevute.stream()
                    .filter(s -> inviata.id().equals(s.get("id"))).toList();
            assertEquals(1, sintesi.size(), "la scheda compare una volta tra le ricevute");
            stato.put(inviata.id(), List.of(sintesi.getFirst(), scheda(banco, inviata.id())));
        }
        return stato;
    }

    // ------------------------------------------------------------------ scheda inviata prima della revoca (#30)

    static Stream<Arguments> operatoriDaRevocare() {
        return Stream.of(Arguments.of(INTERNO, new Periodo(1975, 1)), Arguments.of(ESTERNO, new Periodo(1975, 2)));
    }

    @ParameterizedTest(name = "CA-01/CA-02/CA-03 · {0} revocato dopo l''invio in {1}")
    @MethodSource("operatoriDaRevocare")
    @DisplayName("CA-01/CA-02/CA-03 · la scheda inviata prima della revoca resta invariata e leggibile dal Banco")
    void schedaPrimaDellaRevoca(TipoErogatore tipologia, Periodo periodo) throws Exception {
        Inviata daRevocare = inviata(scenario.operatore(tipologia), periodo, "{\"donne\":3,\"minori\":0}");
        Inviata attiva = inviata(scenario.operatore(INTERNO), periodo, "{\"donne\":4}");
        MockHttpSession banco = http.accedi(scenario.banco());
        Map<String, Object> schedaPrima = scheda(banco, daRevocare.id());
        List<Map<String, Object>> ricevutePrima = ricevute(banco, periodo);
        Map<String, Object> riepilogoPrima = riepilogo(banco, periodo);

        Csrf csrf = http.csrf(banco);
        MockHttpServletResponse revoca = esegui(post(REVOCA, daRevocare.titolare().id()).session(banco)
                .header(csrf.header(), csrf.token()).accept(MediaType.APPLICATION_JSON));
        assertEquals("REVOCATO", comeMappa(corpo(revoca)).get("stato"), "passo preparatorio");

        Map<String, Object> schedaDopo = scheda(banco, daRevocare.id());
        assertScheda(schedaDopo, daRevocare);
        assertEquals(schedaPrima, schedaDopo, "scheda invariata dopo la revoca");
        List<Map<String, Object>> ricevuteDopo = ricevute(banco, periodo);
        assertSchede(ricevuteDopo, daRevocare, attiva);
        assertEquals(ricevutePrima, ricevuteDopo);
        Map<String, Object> riepilogoDopo = riepilogo(banco, periodo);
        assertSchede(comeLista(riepilogoDopo.get("schede")), daRevocare, attiva);
        assertEquals("7", conteggi(riepilogoDopo).get("donne"), "il totale comprende la scheda del revocato");
        assertEquals("0", conteggi(riepilogoDopo).get("minori"));
        assertEquals(riepilogoPrima, riepilogoDopo, "riepilogo invariato dopo la revoca");
    }

    // ------------------------------------------------------------------ chiamate HTTP

    /** Scheda sintetica valida con i conteggi indicati. */
    private static String corpoScheda(String conteggi) {
        return """
                {"nome":"NomeSintetico57","cognome":"CognomeSintetico57","luogoNascita":"LuogoSintetico57",
                 "dataNascita":"1980-01-31","codiceFiscale":"PRVSNT80A31F839X","qualifica":"QualificaSintetica57",
                 "tipologiaContratto":"ContrattoSintetico57","areaLivello":"LivelloSintetico57",
                 "ore":120.75,"costoOrarioLordo":23.45,"conteggi":%s}""".formatted(conteggi);
    }

    /** Passo preparatorio: l'operatore accede e invia la scheda del periodo. */
    private Inviata inviata(Account titolare, Periodo periodo, String conteggi) throws Exception {
        MockHttpSession sessione = http.accedi(titolare);
        Csrf csrf = http.csrf(sessione);
        String corpo = corpoScheda(conteggi);
        MockHttpServletResponse risposta = esegui(conPeriodo(post(SCHEDE), periodo).session(sessione)
                .header(csrf.header(), csrf.token()).contentType(MediaType.APPLICATION_JSON).content(corpo)
                .accept(MediaType.APPLICATION_JSON));
        assertEquals(201, risposta.getStatus(), "invio preparatorio non accettato");
        Map<String, Object> ricevuta = comeMappa(leggiJson(testo(risposta)));
        assertEquals(periodo.json(), ricevuta.get("trimestre"));
        return new Inviata(assertInstanceOf(String.class, ricevuta.get("id")), titolare, periodo, corpo);
    }

    private static MockHttpServletRequestBuilder conPeriodo(MockHttpServletRequestBuilder richiesta, Periodo periodo) {
        return richiesta.queryParam("anno", String.valueOf(periodo.anno()))
                .queryParam("trimestre", String.valueOf(periodo.trimestre()));
    }

    /** Lettura con la sessione indicata; {@code null} è una richiesta senza sessione. */
    private MockHttpServletResponse leggi(MockHttpSession sessione, MockHttpServletRequestBuilder richiesta)
            throws Exception {
        if (sessione != null) {
            richiesta.session(sessione);
        }
        return esegui(richiesta.accept(MediaType.APPLICATION_JSON));
    }

    /** Ogni esito si attribuisce a una rotta reale: un 401 o un 403 da solo non dimostra che esista. */
    private MockHttpServletResponse esegui(MockHttpServletRequestBuilder richiesta) throws Exception {
        http.richiediEndpointConsegnato(richiesta);
        return http.esegui(richiesta);
    }

    private Map<String, Object> scheda(MockHttpSession sessione, String id) throws Exception {
        return comeMappa(corpo(leggi(sessione, get(SCHEDA, id))));
    }

    private List<Map<String, Object>> ricevute(MockHttpSession sessione, Periodo periodo) throws Exception {
        return comeLista(corpo(leggi(sessione, conPeriodo(get(RICEVUTE), periodo))));
    }

    private Map<String, Object> riepilogo(MockHttpSession sessione, Periodo periodo) throws Exception {
        return comeMappa(corpo(leggi(sessione, conPeriodo(get(RIEPILOGO), periodo))));
    }

    private List<Map<String, Object>> schedeDelRiepilogo(MockHttpSession sessione, Periodo periodo) throws Exception {
        return comeLista(riepilogo(sessione, periodo).get("schede"));
    }

    // ------------------------------------------------------------------ lettura e verifiche

    private static String testo(MockHttpServletResponse risposta) throws Exception {
        return risposta.getContentAsString(StandardCharsets.UTF_8);
    }

    /** Corpo JSON di una risposta {@code 200}. */
    private static Object corpo(MockHttpServletResponse risposta) throws Exception {
        assertEquals(200, risposta.getStatus());
        return leggiJson(testo(risposta));
    }

    private static Object leggiJson(String json) {
        return JSON.readValue(json, Object.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> comeMappa(Object valore) {
        return (Map<String, Object>) assertInstanceOf(Map.class, valore);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> comeLista(Object valore) {
        return (List<Map<String, Object>>) assertInstanceOf(List.class, valore);
    }

    private static Map<String, Object> conteggi(Map<String, Object> riepilogo) {
        return comeMappa(riepilogo.get("conteggi"));
    }

    /** L'elenco contiene una volta tutte e sole le schede attese, ciascuna con operatore e periodo. */
    private static void assertSchede(List<Map<String, Object>> elenco, Inviata... attese) {
        assertEquals(Stream.of(attese).map(Inviata::id).sorted().toList(),
                elenco.stream().map(s -> (String) s.get("id")).sorted().toList(), "tutte e sole le schede attese");
        for (Inviata attesa : attese) {
            assertSintesi(elenco.stream().filter(s -> attesa.id().equals(s.get("id"))).findFirst().orElseThrow(),
                    attesa);
        }
    }

    private static void assertSintesi(Map<String, Object> sintesi, Inviata attesa) {
        assertEquals(attesa.id(), sintesi.get("id"));
        assertEquals(attesa.titolare().id().toString(), sintesi.get("operatoreId"), "operatore della scheda");
        assertEquals(attesa.periodo().json(), sintesi.get("trimestre"));
        assertNotNull(sintesi.get("inviataIl"));
    }

    /** Ogni dato riletto è uguale a quello inviato; i decimali si confrontano per valore. */
    private static void assertScheda(Map<String, Object> scheda, Inviata attesa) {
        assertSintesi(comeMappa(scheda.get("sintesi")), attesa);
        Map<String, Object> riletti = comeMappa(scheda.get("dati"));
        Set<Map.Entry<String, Object>> inviati = comeMappa(leggiJson(attesa.corpo())).entrySet();
        for (Map.Entry<String, Object> campo : inviati) {
            Object riletto = riletti.get(campo.getKey());
            if (campo.getValue() instanceof BigDecimal inviato) {
                assertEquals(0, inviato.compareTo(new BigDecimal(assertInstanceOf(Number.class, riletto).toString())),
                        () -> campo.getKey() + ": inviato " + inviato + ", riletto " + riletto);
            } else {
                assertEquals(campo.getValue(), riletto, campo.getKey());
            }
        }
    }
}
