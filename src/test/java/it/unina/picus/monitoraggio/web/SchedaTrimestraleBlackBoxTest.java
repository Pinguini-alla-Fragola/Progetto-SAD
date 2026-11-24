package it.unina.picus.monitoraggio.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
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
import it.unina.picus.shared.ClientHttp.SessioneAnonima;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * #58 Compilazione e invio della scheda trimestrale: test black box (#180) sulle rotte HTTP reali.
 *
 * <p>Nei nomi {@code CA-nn} indica l'n-esimo criterio di accettazione nell'ordine della issue: CA-01 modulo,
 * CA-02 invio e consultazione, CA-03 dati non validi, CA-04 categorie sovrapponibili, CA-05 attività senza
 * richiesta, CA-06 account OPT, CA-07 operatore diverso dal titolare.</p>
 *
 * <p>Il database si usa solo per preparare account e profili: le schede si inviano e si rileggono soltanto
 * dalle API. Ogni test ha account nuovi e le schede degli altri test restano nel database, quindi gli elenchi
 * del Banco si confrontano solo prima e dopo o sulla scheda del test.</p>
 *
 * <p>Tecniche: classi di equivalenza e valori limite con un solo difetto per caso (CA-03), tipologie di
 * operatore e coppie complete di colleghi (CA-01, CA-02, CA-07), test negativi di sicurezza con controllo
 * positivo sulla stessa risorsa (CA-06, CA-07, sessione assente).</p>
 *
 * <p>Esecuzione: {@code ./mvnw test -Dgroups=US-58}; serve Docker.</p>
 */
@Tag("US-58")
@DisplayName("#58 · Compilazione e invio della scheda trimestrale")
class SchedaTrimestraleBlackBoxTest extends BlackBoxPostgresTest {

    private static final String SCHEDE = "/api/monitoraggio/schede-operatore";
    private static final String MODULO = SCHEDE + "/modulo";
    private static final String MIE = SCHEDE + "/mie";
    private static final String RICEVUTE = SCHEDE + "/ricevute";
    private static final String SCHEDA = SCHEDE + "/{id}";

    private static final String CAMPI_ERRATI = "Controlla i campi della scheda";
    private static final String PARAMETRI_ERRATI = "Controlla i parametri";

    private static final Set<String> CAMPI_SCHEDA = Set.of("nome", "cognome", "luogoNascita", "dataNascita",
            "codiceFiscale", "qualifica", "tipologiaContratto", "areaLivello", "ore", "costoOrarioLordo", "conteggi");
    private static final Set<String> DECIMALI = Set.of("ore", "costoOrarioLordo");
    private static final Set<String> OBBLIGATORI = Set.of("ore", "costoOrarioLordo", "conteggi");

    /** Emoji fuori dal piano base: un code point, due caratteri Java. */
    private static final String EMOJI = Character.toString(0x1F600);

    /** Periodo concluso usato quando il trimestre non è l'oggetto del caso. */
    private static final Periodo PERIODO = new Periodo("2025", "4");
    /** Altro periodo concluso, per i controlli positivi. */
    private static final Periodo ALTRO_PERIODO = new Periodo("2026", "2");

    /** Numeri letti senza perdita di precisione: un double non rappresenta 13 cifre intere e 6 decimali. */
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .build();

    @Autowired
    private WebApplicationContext web;

    private ClientHttp http;

    @BeforeEach
    void preparaClient() {
        http = new ClientHttp(web);
    }

    /** Parametri di periodo come li invia il client; {@code null} è un parametro assente. */
    private record Periodo(String anno, String trimestre) {
        Map<String, Object> json() {
            return Map.of("anno", new BigInteger(anno), "numero", new BigInteger(trimestre));
        }
    }

    // ------------------------------------------------------------------ CA-01 modulo

    static Stream<Arguments> tipologieEPeriodi() {
        return Stream.of(Arguments.of(TipoErogatore.INTERNO_BANCO, "2025", "4"),
                Arguments.of(TipoErogatore.ESTERNO_PAGATO_BANCO, "2026", "2"));
    }

    @ParameterizedTest(name = "CA-01 · {0} apre il modulo di {1}-{2}")
    @MethodSource("tipologieEPeriodi")
    @DisplayName("CA-01 · l'operatore apre il modulo e riceve i campi della scheda per il periodo chiesto")
    void moduloDelPeriodo(TipoErogatore tipologia, String anno, String trimestre) throws Exception {
        Periodo periodo = new Periodo(anno, trimestre);
        MockHttpSession operatore = http.accedi(scenario.operatore(tipologia));

        MockHttpServletResponse risposta = leggi(operatore, conPeriodo(get(MODULO), periodo));

        assertEquals(200, risposta.getStatus());
        Map<String, Object> modulo = oggetto(risposta);
        assertEquals(periodo.json(), modulo.get("trimestre"));
        assertEquals("TM-OP-01", modulo.get("tracciato"));
        assertFalse(assertInstanceOf(String.class, modulo.get("versione")).isBlank(), "versione assente");
        List<Map<String, Object>> campi = comeLista(modulo.get("campi"));
        campi.forEach(campo -> assertEquals(Set.of("nome", "tipo", "obbligatorio"), campo.keySet()));
        assertEquals(CAMPI_SCHEDA, campi.stream().map(c -> (String) c.get("nome")).collect(Collectors.toSet()));
        assertEquals(OBBLIGATORI, campi.stream().filter(c -> Boolean.TRUE.equals(c.get("obbligatorio")))
                .map(c -> (String) c.get("nome")).collect(Collectors.toSet()));
    }

    // ------------------------------------------------------------------ CA-02 invio e consultazione

    @ParameterizedTest(name = "CA-02 · {0} invia la scheda di {1}-{2}")
    @MethodSource("tipologieEPeriodi")
    @DisplayName("CA-02 · invio senza firma: conferma del periodo, rilettura del titolare e del Banco")
    void invioEConsultazione(TipoErogatore tipologia, String anno, String trimestre) throws Exception {
        Periodo periodo = new Periodo(anno, trimestre);
        Account titolare = scenario.operatore(tipologia);
        MockHttpSession operatore = http.accedi(titolare);
        MockHttpSession banco = http.accedi(scenario.banco());
        String corpo = json(dati());
        assertEquals(CAMPI_SCHEDA, mappa(corpo).keySet(), "l'invio contiene i soli campi del tracciato, senza firma");

        String id = assertInviata(invia(operatore, periodo, corpo), periodo);

        Map<String, Object> delTitolare = scheda(operatore, id);
        assertScheda(delTitolare, id, titolare, periodo, corpo);
        List<Map<String, Object>> mie = elenco(operatore, get(MIE));
        assertEquals(1, mie.size());
        assertSintesi(mie.getFirst(), id, titolare, periodo);
        assertEquals(delTitolare, scheda(banco, id), "il Banco consulta la stessa scheda");
        List<Map<String, Object>> ricevute = elenco(banco, conPeriodo(get(RICEVUTE), periodo)).stream()
                .filter(s -> id.equals(s.get("id"))).toList();
        assertEquals(1, ricevute.size(), "la scheda compare una volta tra le ricevute del periodo");
        assertSintesi(ricevute.getFirst(), id, titolare, periodo);
    }

    @ParameterizedTest(name = "CA-02 · secondo invio di {0} per {1}-{2}")
    @MethodSource("tipologieEPeriodi")
    @DisplayName("CA-02 · un secondo invio per lo stesso periodo riceve 409 e non cambia la scheda salvata")
    void primoInvioUnico(TipoErogatore tipologia, String anno, String trimestre) throws Exception {
        Periodo periodo = new Periodo(anno, trimestre);
        Periodo altro = periodo.equals(PERIODO) ? ALTRO_PERIODO : PERIODO;
        MockHttpSession operatore = http.accedi(scenario.operatore(tipologia));
        String id = assertInviata(invia(operatore, periodo, json(dati())), periodo);
        Map<String, Object> prima = scheda(operatore, id);

        MockHttpServletResponse duplicato = invia(operatore, periodo,
                json(con("nome", "\"Altro nome\"", "ore", "7", "conteggi", "{\"altra_categoria\":9}")));

        assertEquals(409, duplicato.getStatus(), "il duplicato è un conflitto, non un errore del server");
        assertNull(duplicato.getHeader("Location"));
        Map<String, Object> errore = oggetto(duplicato);
        assertEquals(Set.of("codice", "messaggio", "campi"), errore.keySet());
        assertEquals("CONFLICT", errore.get("codice"));
        assertEquals(Map.of(), errore.get("campi"));
        assertEquals(prima, scheda(operatore, id), "la scheda salvata deve restare identica");
        assertEquals(List.of(id), ids(elenco(operatore, get(MIE))));

        String successiva = assertInviata(invia(operatore, altro, json(dati())), altro);
        assertNotEquals(id, successiva);
        assertEquals(Set.of(id, successiva), Set.copyOf(ids(elenco(operatore, get(MIE)))));
    }

    // ------------------------------------------------------------------ CA-03 dati non validi

    /**
     * Invio da rifiutare.
     *
     * @param nome descrizione del caso
     * @param periodo parametri di periodo
     * @param corpo JSON inviato
     * @param messaggio messaggio fisso atteso
     * @param campi campi da correggere, tutti attesi
     * @param valoreErrato valore che non deve tornare nella risposta, oppure {@code null}
     */
    private record Rifiuto(String nome, Periodo periodo, String corpo, String messaggio, Set<String> campi,
            String valoreErrato) {
        @Override
        public String toString() {
            return nome;
        }
    }

    static Stream<Rifiuto> invioNonValido() {
        String categoria65 = "c".repeat(65);
        return Stream.of(
                // obbligatori
                corpoErrato("ore assenti", json(senza("ore")), null, "ore"),
                corpoErrato("ore null", json(con("ore", "null")), null, "ore"),
                corpoErrato("costoOrarioLordo assente", json(senza("costoOrarioLordo")), null, "costoOrarioLordo"),
                corpoErrato("conteggi assenti", json(senza("conteggi")), null, "conteggi"),
                corpoErrato("ore e costoOrarioLordo assenti", json(senza("ore", "costoOrarioLordo")), null,
                        "ore", "costoOrarioLordo"),
                // decimali
                campoErrato("ore negative", "ore", "-0.5"),
                campoErrato("ore con 14 cifre intere", "ore", "10000000000000"),
                campoErrato("ore con 7 decimali", "ore", "1.2345678"),
                campoErrato("ore come stringa", "ore", "\"8137\""),
                campoErrato("costoOrarioLordo negativo", "costoOrarioLordo", "-0.000001"),
                campoErrato("costoOrarioLordo con 14 cifre intere", "costoOrarioLordo", "10000000000000"),
                campoErrato("costoOrarioLordo con 7 decimali", "costoOrarioLordo", "0.0000001"),
                campoErrato("costoOrarioLordo come stringa", "costoOrarioLordo", "\"8137\""),
                // testi e data
                campoErrato("nome di 256 caratteri", "nome", "\"" + "x".repeat(256) + "\""),
                campoErrato("areaLivello di 256 code point fuori dal piano base", "areaLivello",
                        "\"" + EMOJI.repeat(256) + "\""),
                corpoErrato("qualifica con NUL", json(con("qualifica", "\"prima\\u0000dopo\"")), "dopo", "qualifica"),
                corpoErrato("cognome con surrogato isolato", json(con("cognome", "\"prima\\uD800dopo\"")), "dopo",
                        "cognome"),
                campoErrato("nome come numero", "nome", "8137"),
                campoErrato("dataNascita in altro formato", "dataNascita", "\"31/12/1980\""),
                campoErrato("dataNascita inesistente", "dataNascita", "\"1980-02-30\""),
                // conteggi
                conteggiErrati("codice di 65 caratteri", "{\"" + categoria65 + "\":1}", categoria65),
                conteggiErrati("codice che inizia con underscore", "{\"_minori\":1}", "_minori"),
                conteggiErrati("codice vuoto", "{\"\":1}", null),
                conteggiErrati("conteggio -1", "{\"minori\":-1}", "-1"),
                conteggiErrati("conteggio 1.5", "{\"minori\":1.5}", "1.5"),
                conteggiErrati("conteggio come stringa", "{\"minori\":\"8137\"}", "8137"),
                conteggiErrati("conteggio null", "{\"minori\":null}", "minori"),
                conteggiErrati("conteggi come lista", "[8137]", "8137"),
                // struttura del JSON
                corpoErrato("proprietà sconosciuta", json(con("proprietaIgnota", "\"valore-ignoto\"")),
                        "valore-ignoto", "dati"),
                corpoErrato("JSON malformato", "{non-json", "non-json", "dati"),
                corpoErrato("corpo vuoto", "", null, "dati"),
                // periodo
                periodoErrato("anno assente", null, "4", "anno"),
                periodoErrato("anno 0", "0", "4", "anno"),
                periodoErrato("trimestre assente", "2025", null, "trimestre"),
                periodoErrato("trimestre 0", "2025", "0", "trimestre"),
                periodoErrato("trimestre 5", "2025", "5", "trimestre"),
                new Rifiuto("trimestre abc", new Periodo("2025", "abc"), json(dati()), PARAMETRI_ERRATI,
                        Set.of("trimestre"), "abc"),
                new Rifiuto("anno abc", new Periodo("abc", "4"), json(dati()), PARAMETRI_ERRATI,
                        Set.of("anno"), "abc"),
                trimestreAperto("trimestre corrente", 0),
                trimestreAperto("trimestre successivo", 1),
                trimestreAperto("stesso trimestre dell'anno successivo", 4));
    }

    @ParameterizedTest(name = "CA-03 · {0} → 400 e nessuna scheda registrata")
    @MethodSource("invioNonValido")
    @DisplayName("CA-03 · dati mancanti o non validi: il campo è indicato e l'invio non è registrato")
    void invioRifiutato(Rifiuto caso) throws Exception {
        MockHttpSession operatore = http.accedi(scenario.operatore(TipoErogatore.INTERNO_BANCO));
        assertEquals(List.of(), elenco(operatore, get(MIE)));

        MockHttpServletResponse risposta = invia(operatore, caso.periodo(), caso.corpo());

        assertEquals(400, risposta.getStatus());
        assertNull(risposta.getHeader("Location"));
        Map<String, Object> errore = oggetto(risposta);
        assertEquals(Set.of("codice", "messaggio", "campi"), errore.keySet());
        assertEquals("BAD_REQUEST", errore.get("codice"));
        assertEquals(caso.messaggio(), errore.get("messaggio"));
        Map<String, Object> campi = comeMappa(errore.get("campi"));
        assertEquals(caso.campi(), campi.keySet(), "tutti e soli i campi da correggere");
        campi.values().forEach(m -> assertFalse(assertInstanceOf(String.class, m).isBlank()));
        assertSenzaValoriInviati(risposta, caso.valoreErrato());
        assertEquals(List.of(), elenco(operatore, get(MIE)), "nessuna scheda deve essere registrata");
    }

    /** Invio sul lato valido di un limite. */
    private record Ammesso(String nome, Periodo periodo, String corpo) {
        @Override
        public String toString() {
            return nome;
        }
    }

    static Stream<Ammesso> limiteAmmesso() {
        return Stream.of(
                ammesso("ore 0", "ore", "0"),
                ammesso("ore con 13 cifre intere", "ore", "9999999999999"),
                ammesso("ore con 6 decimali", "ore", "0.000001"),
                ammesso("ore con 13 cifre intere e 6 decimali", "ore", "9999999999999.999999"),
                ammesso("costoOrarioLordo 0", "costoOrarioLordo", "0"),
                ammesso("costoOrarioLordo con 13 cifre intere", "costoOrarioLordo", "9999999999999"),
                ammesso("costoOrarioLordo con 6 decimali", "costoOrarioLordo", "0.000001"),
                ammesso("nome di 255 caratteri", "nome", "\"" + "x".repeat(255) + "\""),
                ammesso("areaLivello di 255 code point fuori dal piano base", "areaLivello",
                        "\"" + EMOJI.repeat(255) + "\""),
                ammesso("codice categoria di 64 caratteri", "conteggi", "{\"" + "c".repeat(64) + "\":1}"),
                ammesso("codice categoria di 1 carattere", "conteggi", "{\"7\":1}"),
                ammesso("codice categoria con punto, trattino e underscore", "conteggi", "{\"a_b.c-d\":1}"),
                new Ammesso("soli campi obbligatori", PERIODO, json(senza("nome", "cognome", "luogoNascita",
                        "dataNascita", "codiceFiscale", "qualifica", "tipologiaContratto", "areaLivello"))),
                new Ammesso("trimestre 1", new Periodo("2025", "1"), json(dati())),
                new Ammesso("anno 1", new Periodo("1", "4"), json(dati())),
                new Ammesso("trimestre immediatamente precedente", trimestreDaOggi(-1), json(dati())));
    }

    @ParameterizedTest(name = "CA-03 · {0} → 201")
    @MethodSource("limiteAmmesso")
    @DisplayName("CA-03 · controllo positivo: i valori sul limite ammesso sono registrati senza modifiche")
    void limiteRegistrato(Ammesso caso) throws Exception {
        Account titolare = scenario.operatore(TipoErogatore.ESTERNO_PAGATO_BANCO);
        MockHttpSession operatore = http.accedi(titolare);

        String id = assertInviata(invia(operatore, caso.periodo(), caso.corpo()), caso.periodo());

        assertScheda(scheda(operatore, id), id, titolare, caso.periodo(), caso.corpo());
        assertEquals(List.of(id), ids(elenco(operatore, get(MIE))));
    }

    // ------------------------------------------------------------------ CA-04 e CA-05 conteggi

    @Test
    @DisplayName("CA-04 · le categorie sovrapponibili non devono sommare al totale di persone uniche")
    void categorieSovrapponibili() throws Exception {
        assertConteggiConservati(TipoErogatore.INTERNO_BANCO,
                "{\"donne\":5,\"minori\":3,\"stranieri\":4,\"persone_uniche\":6}");
    }

    @Test
    @DisplayName("CA-04 · un conteggio a 0 dichiarato viene conservato")
    void conteggioZeroConservato() throws Exception {
        assertConteggiConservati(TipoErogatore.INTERNO_BANCO, "{\"donne\":0,\"minori\":3,\"persone_uniche\":2}");
    }

    /** L'operatore appena creato non ha misure, richieste né assegnazioni. */
    @ParameterizedTest(name = "CA-05 · {0} rendiconta attività senza richiesta PICUS")
    @EnumSource(value = TipoErogatore.class, names = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    @DisplayName("CA-05 · attività senza richieste PICUS, senza collegarle a richiesta, OPT o misura")
    void attivitaSenzaRichiesta(TipoErogatore tipologia) throws Exception {
        assertConteggiConservati(tipologia, "{\"colloqui_senza_richiesta\":12,\"accoglienze_senza_richiesta\":1}");
    }

    private void assertConteggiConservati(TipoErogatore tipologia, String conteggi) throws Exception {
        Account titolare = scenario.operatore(tipologia);
        MockHttpSession operatore = http.accedi(titolare);
        String corpo = json(con("conteggi", conteggi));

        String id = assertInviata(invia(operatore, PERIODO, corpo), PERIODO);

        assertScheda(scheda(operatore, id), id, titolare, PERIODO, corpo);
    }

    // ------------------------------------------------------------------ CA-06 account OPT

    @Test
    @DisplayName("CA-06 · la OPT riceve FORBIDDEN su modulo, elenchi e dettaglio di una scheda esistente")
    void optNonLegge() throws Exception {
        MockHttpSession titolare = http.accedi(scenario.operatore(TipoErogatore.INTERNO_BANCO));
        String id = assertInviata(invia(titolare, PERIODO, json(dati())), PERIODO);
        Map<String, Object> prima = scheda(titolare, id);
        MockHttpSession opt = http.accedi(scenario.optRichiedente());

        ClientHttp.assertDiniegoSenzaCorpo(leggi(opt, conPeriodo(get(MODULO), PERIODO)));
        ClientHttp.assertDiniegoSenzaCorpo(leggi(opt, get(MIE)));
        ClientHttp.assertDiniegoSenzaCorpo(leggi(opt, get(SCHEDA, id)));
        ClientHttp.assertDiniegoSenzaCorpo(leggi(opt, conPeriodo(get(RICEVUTE), PERIODO)));

        assertEquals(prima, scheda(titolare, id));
    }

    @Test
    @DisplayName("CA-06 · la OPT che invia una scheda personale riceve FORBIDDEN e nulla viene registrato")
    void optNonInvia() throws Exception {
        MockHttpSession titolare = http.accedi(scenario.operatore(TipoErogatore.ESTERNO_PAGATO_BANCO));
        String id = assertInviata(invia(titolare, PERIODO, json(dati())), PERIODO);
        MockHttpSession banco = http.accedi(scenario.banco());
        Account opt = scenario.optRichiedente();
        MockHttpSession sessioneOpt = http.accedi(opt);
        Map<String, Object> prima = scheda(titolare, id);
        List<Map<String, Object>> ricevutePrima = elenco(banco, conPeriodo(get(RICEVUTE), PERIODO));

        MockHttpServletResponse risposta = invia(sessioneOpt, PERIODO, json(dati()));

        ClientHttp.assertDiniegoSenzaCorpo(risposta);
        assertNull(risposta.getHeader("Location"));
        List<Map<String, Object>> ricevuteDopo = elenco(banco, conPeriodo(get(RICEVUTE), PERIODO));
        assertEquals(ricevutePrima, ricevuteDopo, "nessuna scheda creata");
        assertTrue(ricevuteDopo.stream().noneMatch(s -> opt.id().toString().equals(s.get("operatoreId"))));
        assertEquals(prima, scheda(titolare, id));
    }

    // ------------------------------------------------------------------ CA-07 altro operatore

    static Stream<Arguments> coppieDiColleghi() {
        TipoErogatore interno = TipoErogatore.INTERNO_BANCO;
        TipoErogatore esterno = TipoErogatore.ESTERNO_PAGATO_BANCO;
        return Stream.of(Arguments.of(interno, interno), Arguments.of(interno, esterno),
                Arguments.of(esterno, interno), Arguments.of(esterno, esterno));
    }

    @ParameterizedTest(name = "CA-07 · titolare {0}, collega {1}")
    @MethodSource("coppieDiColleghi")
    @DisplayName("CA-07 · il collega non legge la scheda altrui e l'esito è quello di una scheda inesistente")
    void collegaNonLegge(TipoErogatore tipoTitolare, TipoErogatore tipoCollega) throws Exception {
        MockHttpSession titolare = http.accedi(scenario.operatore(tipoTitolare));
        MockHttpSession collega = http.accedi(scenario.operatore(tipoCollega));
        String id = assertInviata(invia(titolare, PERIODO, json(dati())), PERIODO);
        Map<String, Object> prima = scheda(titolare, id);

        MockHttpServletResponse altrui = leggi(collega, get(SCHEDA, id));
        MockHttpServletResponse assente = leggi(collega, get(SCHEDA, UUID.randomUUID()));

        ClientHttp.assertDiniegoSenzaCorpo(altrui);
        ClientHttp.assertDiniegoSenzaCorpo(assente);
        assertEquals(assente.getContentType(), altrui.getContentType(), "l'esito non rivela l'esistenza della scheda");
        ClientHttp.assertDiniegoSenzaCorpo(leggi(collega, conPeriodo(get(RICEVUTE), PERIODO)));
        assertEquals(List.of(), elenco(collega, get(MIE)));
        assertEquals(prima, scheda(titolare, id));
    }

    /** Il collega dichiara la stessa anagrafica: la titolarità viene dalla sessione, non dai dati inviati. */
    @ParameterizedTest(name = "CA-07 · titolare {0}, collega {1}")
    @MethodSource("coppieDiColleghi")
    @DisplayName("CA-07 · l'invio del collega per lo stesso periodo è suo e lascia invariata la scheda del titolare")
    void invioDelCollegaSeparato(TipoErogatore tipoTitolare, TipoErogatore tipoCollega) throws Exception {
        MockHttpSession titolare = http.accedi(scenario.operatore(tipoTitolare));
        Account accountCollega = scenario.operatore(tipoCollega);
        MockHttpSession collega = http.accedi(accountCollega);
        String id = assertInviata(invia(titolare, PERIODO, json(dati())), PERIODO);
        Map<String, Object> prima = scheda(titolare, id);
        String corpo = json(con("ore", "7", "conteggi", "{\"donne\":99}"));

        String idCollega = assertInviata(invia(collega, PERIODO, corpo), PERIODO);

        assertNotEquals(id, idCollega);
        assertScheda(scheda(collega, idCollega), idCollega, accountCollega, PERIODO, corpo);
        assertEquals(prima, scheda(titolare, id));
        assertEquals(List.of(id), ids(elenco(titolare, get(MIE))));
        assertEquals(List.of(idCollega), ids(elenco(collega, get(MIE))));
    }

    @Test
    @DisplayName("CA-07 · controllo positivo: il Banco legge la scheda (200) e riceve 404 su un id inesistente")
    void letturaDelBanco() throws Exception {
        Account titolare = scenario.operatore(TipoErogatore.INTERNO_BANCO);
        String corpo = json(dati());
        String id = assertInviata(invia(http.accedi(titolare), PERIODO, corpo), PERIODO);
        MockHttpSession banco = http.accedi(scenario.banco());

        assertScheda(scheda(banco, id), id, titolare, PERIODO, corpo);
        ClientHttp.assertErrorePubblico(leggi(banco, get(SCHEDA, UUID.randomUUID())), 404, "NOT_FOUND",
                "Scheda non trovata");
    }

    // ------------------------------------------------------------------ sessione assente

    /** La POST porta un token CSRF valido: il 401 dipende dall'autenticazione e non da un diniego CSRF. */
    @Test
    @DisplayName("CA-01/CA-02 · senza sessione: modulo, invio e dettaglio ricevono 401 senza dati né modifiche")
    void sessioneAssente() throws Exception {
        MockHttpSession titolare = http.accedi(scenario.operatore(TipoErogatore.INTERNO_BANCO));
        MockHttpSession banco = http.accedi(scenario.banco());
        String id = assertInviata(invia(titolare, PERIODO, json(dati())), PERIODO);
        Map<String, Object> prima = scheda(titolare, id);
        List<Map<String, Object>> ricevutePrima = elenco(banco, conPeriodo(get(RICEVUTE), ALTRO_PERIODO));
        SessioneAnonima anonima = http.sessioneAnonima();

        ClientHttp.assertNonAutenticatoSenzaDati(leggi(anonima.sessione(), conPeriodo(get(MODULO), PERIODO)));
        ClientHttp.assertNonAutenticatoSenzaDati(leggi(anonima.sessione(), get(SCHEDA, id)));
        ClientHttp.assertNonAutenticatoSenzaDati(
                invia(anonima.sessione(), anonima.csrf(), ALTRO_PERIODO, json(dati())));

        assertEquals(ricevutePrima, elenco(banco, conPeriodo(get(RICEVUTE), ALTRO_PERIODO)));
        assertEquals(prima, scheda(titolare, id));
    }

    // ------------------------------------------------------------------ dati sintetici

    /** Scheda valida: ogni campo con il suo valore JSON letterale. */
    private static Map<String, String> dati() {
        Map<String, String> dati = new LinkedHashMap<>();
        dati.put("nome", "\"NomeSintetico58\"");
        dati.put("cognome", "\"CognomeSintetico58\"");
        dati.put("luogoNascita", "\"LuogoSintetico58\"");
        dati.put("dataNascita", "\"1980-01-31\"");
        dati.put("codiceFiscale", "\"PRVSNT80A31F839X\"");
        dati.put("qualifica", "\"QualificaSintetica58\"");
        dati.put("tipologiaContratto", "\"ContrattoSintetico58\"");
        dati.put("areaLivello", "\"LivelloSintetico58\"");
        dati.put("ore", "120.75");
        dati.put("costoOrarioLordo", "23.45");
        dati.put("conteggi", "{\"colloqui_senza_richiesta\":12,\"donne\":5}");
        return dati;
    }

    /** Scheda valida con i campi indicati sostituiti o aggiunti, a coppie nome e valore JSON. */
    private static Map<String, String> con(String... coppie) {
        Map<String, String> dati = dati();
        for (int i = 0; i < coppie.length; i += 2) {
            dati.put(coppie[i], coppie[i + 1]);
        }
        return dati;
    }

    private static Map<String, String> senza(String... campi) {
        Map<String, String> dati = dati();
        List.of(campi).forEach(dati::remove);
        return dati;
    }

    private static String json(Map<String, String> dati) {
        return dati.entrySet().stream().map(c -> "\"" + c.getKey() + "\":" + c.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static Rifiuto corpoErrato(String nome, String corpo, String valoreErrato, String... campi) {
        return new Rifiuto(nome, PERIODO, corpo, CAMPI_ERRATI, Set.of(campi), valoreErrato);
    }

    private static Rifiuto campoErrato(String nome, String campo, String valore) {
        return corpoErrato(nome, json(con(campo, valore)), valore.replace("\"", ""), campo);
    }

    private static Rifiuto conteggiErrati(String nome, String conteggi, String valoreErrato) {
        return corpoErrato(nome, json(con("conteggi", conteggi)), valoreErrato, "conteggi");
    }

    private static Rifiuto periodoErrato(String nome, String anno, String trimestre, String campo) {
        return new Rifiuto(nome, new Periodo(anno, trimestre), json(dati()), CAMPI_ERRATI, Set.of(campo), null);
    }

    private static Rifiuto trimestreAperto(String nome, int distanza) {
        return new Rifiuto(nome, trimestreDaOggi(distanza), json(dati()), CAMPI_ERRATI, Set.of("trimestre"), null);
    }

    private static Ammesso ammesso(String nome, String campo, String valore) {
        return new Ammesso(nome, PERIODO, json(con(campo, valore)));
    }

    /** Trimestre a una distanza da quello corrente nel fuso della rendicontazione. */
    private static Periodo trimestreDaOggi(int distanza) {
        LocalDate data = LocalDate.now(ZoneId.of("Europe/Rome")).plus(distanza, IsoFields.QUARTER_YEARS);
        return new Periodo(String.valueOf(data.getYear()), String.valueOf(data.get(IsoFields.QUARTER_OF_YEAR)));
    }

    // ------------------------------------------------------------------ chiamate HTTP

    private static MockHttpServletRequestBuilder conPeriodo(MockHttpServletRequestBuilder richiesta, Periodo periodo) {
        if (periodo.anno() != null) {
            richiesta.queryParam("anno", periodo.anno());
        }
        if (periodo.trimestre() != null) {
            richiesta.queryParam("trimestre", periodo.trimestre());
        }
        return richiesta;
    }

    private MockHttpServletResponse leggi(MockHttpSession sessione, MockHttpServletRequestBuilder richiesta)
            throws Exception {
        return esegui(richiesta.session(sessione).accept(MediaType.APPLICATION_JSON));
    }

    private MockHttpServletResponse invia(MockHttpSession sessione, Periodo periodo, String corpo) throws Exception {
        return invia(sessione, http.csrf(sessione), periodo, corpo);
    }

    private MockHttpServletResponse invia(MockHttpSession sessione, Csrf csrf, Periodo periodo, String corpo)
            throws Exception {
        return esegui(conPeriodo(post(SCHEDE), periodo).session(sessione).header(csrf.header(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(corpo).accept(MediaType.APPLICATION_JSON));
    }

    /** Ogni esito si attribuisce a una rotta reale: un 401 o un 403 da solo non dimostra che esista. */
    private MockHttpServletResponse esegui(MockHttpServletRequestBuilder richiesta) throws Exception {
        http.richiediEndpointDellaCapacita(Capacita.MONITORAGGIO, richiesta);
        return http.esegui(richiesta);
    }

    private Map<String, Object> scheda(MockHttpSession sessione, String id) throws Exception {
        MockHttpServletResponse risposta = leggi(sessione, get(SCHEDA, id));
        assertEquals(200, risposta.getStatus());
        return oggetto(risposta);
    }

    private List<Map<String, Object>> elenco(MockHttpSession sessione, MockHttpServletRequestBuilder richiesta)
            throws Exception {
        MockHttpServletResponse risposta = leggi(sessione, richiesta);
        assertEquals(200, risposta.getStatus());
        return comeLista(JSON.readValue(testo(risposta), Object.class));
    }

    // ------------------------------------------------------------------ lettura e verifiche

    private static String testo(MockHttpServletResponse risposta) throws Exception {
        return risposta.getContentAsString(StandardCharsets.UTF_8);
    }

    private static Map<String, Object> oggetto(MockHttpServletResponse risposta) throws Exception {
        return mappa(testo(risposta));
    }

    private static Map<String, Object> mappa(String json) {
        return comeMappa(JSON.readValue(json, Object.class));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> comeMappa(Object valore) {
        return (Map<String, Object>) assertInstanceOf(Map.class, valore);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> comeLista(Object valore) {
        return (List<Map<String, Object>>) assertInstanceOf(List.class, valore);
    }

    private static List<String> ids(List<Map<String, Object>> schede) {
        return schede.stream().map(s -> (String) s.get("id")).toList();
    }

    /** Conferma dell'invio: 201, riferimento alla risorsa e periodo scelto. Restituisce l'id della scheda. */
    private static String assertInviata(MockHttpServletResponse risposta, Periodo periodo) throws Exception {
        assertEquals(201, risposta.getStatus(), "invio non accettato");
        Map<String, Object> ricevuta = oggetto(risposta);
        assertEquals(Set.of("id", "trimestre"), ricevuta.keySet());
        String id = UUID.fromString(assertInstanceOf(String.class, ricevuta.get("id"))).toString();
        assertEquals(periodo.json(), ricevuta.get("trimestre"), "la conferma riporta anno e trimestre scelti");
        assertEquals(SCHEDE + "/" + id, risposta.getHeader("Location"));
        return id;
    }

    /** Scheda riletta: titolare, periodo e ogni campo dichiarato; un campo non inviato è {@code null}. */
    private static void assertScheda(Map<String, Object> scheda, String id, Account titolare, Periodo periodo,
            String corpoInviato) {
        assertSintesi(comeMappa(scheda.get("sintesi")), id, titolare, periodo);
        Map<String, Object> inviati = mappa(corpoInviato);
        Map<String, Object> riletti = comeMappa(scheda.get("dati"));
        assertEquals(Set.of(), riletti.keySet().stream().filter(c -> !CAMPI_SCHEDA.contains(c))
                .collect(Collectors.toSet()), "soli campi del tracciato");
        for (String campo : CAMPI_SCHEDA) {
            if (DECIMALI.contains(campo)) {
                assertEquals(0, decimale(inviati.get(campo)).compareTo(decimale(riletti.get(campo))),
                        () -> campo + ": inviato " + inviati.get(campo) + ", riletto " + riletti.get(campo));
            } else {
                assertEquals(inviati.get(campo), riletti.get(campo), campo);
            }
        }
    }

    private static void assertSintesi(Map<String, Object> sintesi, String id, Account titolare, Periodo periodo) {
        assertEquals(id, sintesi.get("id"));
        assertEquals(titolare.id().toString(), sintesi.get("operatoreId"), "la scheda è a nome del titolare");
        assertEquals(periodo.json(), sintesi.get("trimestre"));
        assertNotNull(sintesi.get("inviataIl"));
    }

    private static BigDecimal decimale(Object valore) {
        return new BigDecimal(assertInstanceOf(Number.class, valore).toString());
    }

    private static void assertSenzaValoriInviati(MockHttpServletResponse risposta, String valoreErrato)
            throws Exception {
        String corpo = testo(risposta);
        Map<String, String> validi = dati();
        validi.remove("conteggi");
        Stream.concat(validi.values().stream().map(v -> v.replace("\"", "")),
                        Stream.of("colloqui_senza_richiesta", "donne"))
                .forEach(v -> assertFalse(corpo.contains(v), () -> "la risposta riporta un valore inviato: " + v));
        if (valoreErrato != null) {
            assertFalse(corpo.contains(valoreErrato), () -> "la risposta riporta il valore errato: " + valoreErrato);
        }
    }
}
