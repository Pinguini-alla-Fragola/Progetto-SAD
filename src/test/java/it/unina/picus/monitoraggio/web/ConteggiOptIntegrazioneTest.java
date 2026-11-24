package it.unina.picus.monitoraggio.web;

import static it.unina.picus.shared.ClientHttp.assertDiniegoSenzaCorpo;
import static it.unina.picus.shared.ClientHttp.assertNonAutenticatoSenzaDati;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntUnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;

import it.unina.picus.shared.AccessoDiProva;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.shared.SessioneDiProva;

/**
 * Test di integrazione della task #174 «Test di salvataggio e isolamento dei conteggi OPT»,
 * sotto-task della user story #43 «Registrazione dei dati trimestrali delle OPT».
 *
 * Endpoint HTTP reali ({@code /api/monitoraggio/conteggi-opt}), login reale, catena di
 * Spring Security attiva e PostgreSQL 17 in Docker con lo schema creato da Flyway. Completano
 * gli unit test con i mock di {@code MonitoraggioOptServiceTest}: qui contano i vincoli veri
 * della migrazione V24, la transazione dell'invio e le regole di accesso di {@code SecurityConfig}.
 *
 * Ogni test riporta nel nome il criterio di completamento della #174 e, dove c'è,
 * l'Acceptance Criteria della #43 che verifica:
 * * criterio 1: trimestre, misura, categorie sovrapponibili, dati invalidi e ritrasmissione senza raddoppio.
 * * criterio 2: lettura e scrittura da una seconda OPT e versione valida dopo una correzione ammessa.
 *
 * <p>Ciò che l'applicazione ha registrato si legge dalle righe di {@code monitoraggio_conteggi_opt};
 * ciò che vede l'utente si legge dalla consultazione HTTP. Con {@link DatiSintetici} si creano
 * soltanto account e profili, che l'applicazione non permette ancora di creare. L'unica scrittura
 * diretta su un dato del monitoraggio è la configurazione delle correzioni: la decide il Banco, ma
 * non c'è ancora un endpoint per farlo.</p>
 *
 * {@link DatiSintetici#svuota()} non conosce le tabelle del monitoraggio e cancella i profili OPT
 * a cui i conteggi fanno riferimento: per non far fallire le altre classi che condividono il
 * database, questa classe cancella i propri conteggi e ripristina la configurazione prima e dopo
 * ogni test.
 */
@IntegrazionePostgresql
@DisplayName("#174 Salvataggio e isolamento dei conteggi OPT: integrazione su HTTP e PostgreSQL")
@Tag("issue-174")
class ConteggiOptIntegrazioneTest {

    private static final String CONTEGGI = "/api/monitoraggio/conteggi-opt";
    private static final int ANNO = 2026;

    /**
     * Primi invii spediti insieme nei test di concorrenza. Durante l'attesa ognuno occupa una
     * connessione: con quella che tiene il lock e quella del controllo restano sotto il pool
     * predefinito di Hikari (10), come nel test di concorrenza della #92.
     */
    private static final int INVII_CONCORRENTI = 6;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private DataSource dataSource;

    private DatiSintetici dati;
    private AccessoDiProva accesso;
    private final Map<UUID, SessioneDiProva> sessioni = new HashMap<>();

    private long m01;
    private long m02;
    private UUID optA;
    private UUID optB;
    private UUID banco;
    private UUID operatore;

    /** Given comune: due OPT, il Banco e un operatore, nessun conteggio, correzioni disattivate. */
    @BeforeEach
    void preparaAttori() {
        ripulisciMonitoraggio();
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        accesso = new AccessoDiProva(mvc, jdbc, encoder);
        sessioni.clear();

        m01 = dati.misura("M01");
        m02 = dati.misura("M02");
        optA = dati.opt("OPT A", "napoli");
        optB = dati.opt("OPT B", "portici");
        banco = dati.banco("Banco");
        operatore = dati.operatore("Operatore", "INTERNO_BANCO", "napoli", false, m01);
    }

    @AfterEach
    void ripulisciMonitoraggio() {
        jdbc.update("delete from monitoraggio_conteggi_opt");
        jdbc.update("update monitoraggio_configurazione set correzioni_abilitate = false, scadenza_correzioni = null");
        jdbc.update("update monitoraggio_categorie set attiva = true");
    }

    // ---------------------------------------------------------------------------------------
    // Criterio 1 - trimestre e misura
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("C1 / AC1 - i conteggi validi sono salvati per OPT, anno, trimestre, misura e voce")
    void c1_salvataggioAssociatoAOptPeriodoEMisura() throws Exception {
        registra(optA, ANNO, 2, voce(m01, "PERSONE_RAGGIUNTE", 42))
                .andExpect(status().isNoContent());

        assertAll(
                () -> assertEquals(1, righe(), "una sola riga per una sola voce"),
                () -> assertEquals(42L, valoreSalvato(optA, ANNO, 2, m01, "PERSONE_RAGGIUNTE")),
                () -> assertNull(valoreSalvato(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE"), "nessun altro trimestre"),
                () -> assertNull(valoreSalvato(optA, ANNO, 2, m02, "PERSONE_RAGGIUNTE"), "nessun'altra misura"));
    }

    @Test
    @DisplayName("C1 / AC4 - trimestri diversi restano distinti e la consultazione restituisce il solo periodo chiesto")
    void c1_trimestriDistinti() throws Exception {
        registra(optA, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 20)).andExpect(status().isNoContent());
        registra(optA, ANNO, 2, voce(m01, "PERSONE_RAGGIUNTE", 30)).andExpect(status().isNoContent());

        assertEquals(2, righe());
        assertEquals(Map.of(chiave(optA, m01, "PERSONE_RAGGIUNTE"), 20L), consulta(optA, optA, ANNO, 1));
        assertEquals(Map.of(chiave(optA, m01, "PERSONE_RAGGIUNTE"), 30L), consulta(optA, optA, ANNO, 2));
        assertEquals(Map.of(), consulta(optA, optA, ANNO, 3), "un trimestre senza invii è vuoto");
    }

    @ParameterizedTest(name = "anno {0}, trimestre {1}")
    @CsvSource({"2026, 0", "2026, 5", "2026, -1", "0, 1"})
    @DisplayName("C1 / AC2 - un trimestre o un anno fuori intervallo è rifiutato con 400 e nulla è salvato")
    void c1_trimestreNonValido(int anno, int trimestre) throws Exception {
        registra(optA, anno, trimestre, voce(m01, "PERSONE_RAGGIUNTE", 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codice").value("BAD_REQUEST"));

        assertEquals(0, righe());
    }

    @Test
    @DisplayName("C1 / AC2 - una misura inesistente è rifiutata indicando il campo misuraId")
    void c1_misuraInesistente() throws Exception {
        long inesistente = jdbc.queryForObject("select coalesce(max(id), 0) + 1000 from catalogo_misure", Long.class);

        registra(optA, ANNO, 1, voce(inesistente, "PERSONE_RAGGIUNTE", 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codice").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.campi", hasKey("misuraId")));

        assertEquals(0, righe());
    }

    @Test
    @DisplayName("C1 - la stessa voce su due misure diverse produce due conteggi distinti")
    void c1_stessaVoceSuMisureDiverse() throws Exception {
        registra(optA, ANNO, 1,
                voce(m01, "PERSONE_RAGGIUNTE", 12),
                voce(m02, "PERSONE_RAGGIUNTE", 7))
                .andExpect(status().isNoContent());

        assertEquals(Map.of(
                chiave(optA, m01, "PERSONE_RAGGIUNTE"), 12L,
                chiave(optA, m02, "PERSONE_RAGGIUNTE"), 7L), consulta(optA, optA, ANNO, 1));
    }

    // ---------------------------------------------------------------------------------------
    // Criterio 1 - categorie sovrapponibili
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("C1 / AC3 - le categorie sovrapponibili restano distinte anche se la somma supera le persone raggiunte")
    void c1_categorieSovrapponibiliDistinte() throws Exception {
        // minori + anziani + nuclei = 16 > 10 persone raggiunte: è ammesso, le voci non si sommano
        registra(optA, ANNO, 1,
                voce(m01, "PERSONE_RAGGIUNTE", 10),
                voce(m01, "MINORI", 8),
                voce(m01, "ANZIANI", 5),
                voce(m01, "NUCLEI_FAMILIARI", 3))
                .andExpect(status().isNoContent());

        assertEquals(4, righe());
        assertEquals(Map.of(
                chiave(optA, m01, "PERSONE_RAGGIUNTE"), 10L,
                chiave(optA, m01, "MINORI"), 8L,
                chiave(optA, m01, "ANZIANI"), 5L,
                chiave(optA, m01, "NUCLEI_FAMILIARI"), 3L), consulta(optA, optA, ANNO, 1));
    }

    @Test
    @DisplayName("C1 / AC2 - la stessa voce ripetuta per la stessa misura nel medesimo invio è rifiutata")
    void c1_voceDuplicataNelloStessoInvio() throws Exception {
        registra(optA, ANNO, 1,
                voce(m01, "MINORI", 8),
                voce(m01, "MINORI", 9))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi", hasKey("voci[1].voce")));

        assertEquals(0, righe());
    }

    // ---------------------------------------------------------------------------------------
    // Criterio 1 - dati invalidi
    // ---------------------------------------------------------------------------------------

    /** Corpi non validi: {@code {misura}} è sostituito con l'id di una misura esistente. */
    static Stream<Arguments> corpiNonValidi() {
        String voce = "\"voci\": [{\"misuraId\": {misura}, \"voce\": \"MINORI\", \"valore\": 3}]";
        return Stream.of(
                Arguments.of("valore negativo",
                        "{\"anno\": 2026, \"trimestre\": 1, \"voci\": [{\"misuraId\": {misura}, \"voce\": \"MINORI\", \"valore\": -1}]}"),
                Arguments.of("voce fuori dal tracciato",
                        "{\"anno\": 2026, \"trimestre\": 1, \"voci\": [{\"misuraId\": {misura}, \"voce\": \"INVENTATA\", \"valore\": 3}]}"),
                Arguments.of("voce vuota",
                        "{\"anno\": 2026, \"trimestre\": 1, \"voci\": [{\"misuraId\": {misura}, \"voce\": \"  \", \"valore\": 3}]}"),
                Arguments.of("voce assente",
                        "{\"anno\": 2026, \"trimestre\": 1, \"voci\": [{\"misuraId\": {misura}, \"valore\": 3}]}"),
                Arguments.of("valore assente",
                        "{\"anno\": 2026, \"trimestre\": 1, \"voci\": [{\"misuraId\": {misura}, \"voce\": \"MINORI\"}]}"),
                Arguments.of("valore non numerico",
                        "{\"anno\": 2026, \"trimestre\": 1, \"voci\": [{\"misuraId\": {misura}, \"voce\": \"MINORI\", \"valore\": \"tre\"}]}"),
                Arguments.of("misura assente",
                        "{\"anno\": 2026, \"trimestre\": 1, \"voci\": [{\"voce\": \"MINORI\", \"valore\": 3}]}"),
                Arguments.of("nessuna voce", "{\"anno\": 2026, \"trimestre\": 1, \"voci\": []}"),
                Arguments.of("elenco delle voci assente", "{\"anno\": 2026, \"trimestre\": 1}"),
                Arguments.of("anno assente", "{\"trimestre\": 1, " + voce + "}"),
                Arguments.of("trimestre assente", "{\"anno\": 2026, " + voce + "}"),
                Arguments.of("JSON malformato", "{\"anno\": 2026, \"trimestre\": 1, " + voce));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpiNonValidi")
    @DisplayName("C1 / AC2 - i dati non validi sono rifiutati con 400 e nessuna riga viene scritta")
    void c1_datiNonValidi(String caso, String modello) throws Exception {
        sessione(optA).post(CONTEGGI, modello.replace("{misura}", Long.toString(m01)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codice").value("BAD_REQUEST"));

        assertEquals(0, righe(), caso);
    }

    @Test
    @DisplayName("C1 / AC2 - una voce disattivata dal Banco non è più accettata")
    void c1_voceDisattivata() throws Exception {
        jdbc.update("update monitoraggio_categorie set attiva = false where codice = 'ANZIANI'");

        registra(optA, ANNO, 1, voce(m01, "ANZIANI", 4))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi", hasKey("voci[0].voce")));

        assertEquals(0, righe());
    }

    @Test
    @DisplayName("C1 / AC2 - un invio con una riga non valida non sostituisce i dati validi già registrati")
    void c1_invioNonValidoNonSostituisceIDatiRegistrati() throws Exception {
        registra(optA, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 42)).andExpect(status().isNoContent());
        Instant primoSalvataggio = aggiornatoIl(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE");
        // anche con le correzioni ammesse: è la riga non valida a dover bloccare tutto l'invio
        abilitaCorrezioni(Instant.now().plus(1, ChronoUnit.DAYS));

        registra(optA, ANNO, 1,
                voce(m01, "PERSONE_RAGGIUNTE", 50),
                voce(m02, "MINORI", -1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campi", hasKey("voci[1].valore")));

        assertAll(
                () -> assertEquals(1, righe(), "nessuna riga aggiunta"),
                () -> assertEquals(42L, valoreSalvato(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE"), "valore invariato"),
                () -> assertEquals(primoSalvataggio, aggiornatoIl(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE")));
    }

    // ---------------------------------------------------------------------------------------
    // Criterio 1 - ritrasmissione senza raddoppio
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("C1 / AC5 - un invio ritrasmesso identico non viene sommato né duplicato")
    void c1_ritrasmissioneSenzaRaddoppio() throws Exception {
        String[] invio = {voce(m01, "PERSONE_RAGGIUNTE", 42), voce(m01, "MINORI", 15)};
        registra(optA, ANNO, 1, invio).andExpect(status().isNoContent());
        Instant primoSalvataggio = aggiornatoIl(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE");

        registra(optA, ANNO, 1, invio).andExpect(status().isNoContent());
        registra(optA, ANNO, 1, invio).andExpect(status().isNoContent());

        assertAll(
                () -> assertEquals(2, righe(), "una riga per voce, nessun duplicato"),
                () -> assertEquals(42L, valoreSalvato(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE")),
                () -> assertEquals(15L, valoreSalvato(optA, ANNO, 1, m01, "MINORI")),
                () -> assertEquals(primoSalvataggio, aggiornatoIl(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE"),
                        "la ritrasmissione non riscrive la riga"));
        assertEquals(Map.of(
                chiave(optA, m01, "PERSONE_RAGGIUNTE"), 42L,
                chiave(optA, m01, "MINORI"), 15L), consulta(optA, optA, ANNO, 1));
    }

    @Test
    @DisplayName("C1 / AC4 - il cumulativo su più trimestri è calcolato in consultazione, senza un secondo inserimento")
    void c1_cumulativoSenzaSecondoInserimento() throws Exception {
        registra(optA, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 20)).andExpect(status().isNoContent());
        registra(optA, ANNO, 2, voce(m01, "PERSONE_RAGGIUNTE", 30)).andExpect(status().isNoContent());

        assertEquals(Map.of(chiave(optA, m01, "PERSONE_RAGGIUNTE"), 50L), consulta(optA, optA, ANNO, 1, ANNO, 2));
        assertEquals(2, righe(), "la consultazione cumulativa non scrive righe");
    }

    // ---------------------------------------------------------------------------------------
    // Criterio 2 - correzioni
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("C2 - con le correzioni disattivate un valore diverso è respinto con 409 e resta quello registrato")
    void c2_correzioneNonAmmessa() throws Exception {
        registra(optA, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 42)).andExpect(status().isNoContent());

        registra(optA, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 50))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codice").value("CONFLICT"));

        assertEquals(42L, valoreSalvato(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE"));
        assertEquals(Map.of(chiave(optA, m01, "PERSONE_RAGGIUNTE"), 42L), consulta(optA, optA, ANNO, 1));
    }

    @Test
    @DisplayName("C2 - scaduto il termine fissato dal Banco la correzione è respinta con 409")
    void c2_correzioneOltreIlTermine() throws Exception {
        registra(optA, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 42)).andExpect(status().isNoContent());
        abilitaCorrezioni(Instant.now().minus(1, ChronoUnit.HOURS));

        registra(optA, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 50))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codice").value("CONFLICT"));

        assertEquals(42L, valoreSalvato(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE"));
    }

    @Test
    @DisplayName("C2 - dopo una correzione ammessa la versione valida è quella corretta, per la OPT e per il Banco")
    void c2_versioneValidaDopoCorrezioneAmmessa() throws Exception {
        registra(optA, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 42), voce(m01, "MINORI", 15))
                .andExpect(status().isNoContent());
        registra(optA, ANNO, 2, voce(m01, "PERSONE_RAGGIUNTE", 30)).andExpect(status().isNoContent());
        Instant primoSalvataggio = aggiornatoIl(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE");
        abilitaCorrezioni(Instant.now().plus(1, ChronoUnit.DAYS));

        registra(optA, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 50), voce(m01, "MINORI", 15))
                .andExpect(status().isNoContent());

        assertAll(
                () -> assertEquals(3, righe(), "la correzione sostituisce la riga, non ne aggiunge una"),
                () -> assertEquals(50L, valoreSalvato(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE")),
                () -> assertEquals(15L, valoreSalvato(optA, ANNO, 1, m01, "MINORI"), "voce non corretta invariata"),
                () -> assertTrue(aggiornatoIl(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE").isAfter(primoSalvataggio),
                        "la riga corretta registra il nuovo istante"));

        Map<String, Long> attesi = Map.of(
                chiave(optA, m01, "PERSONE_RAGGIUNTE"), 50L,
                chiave(optA, m01, "MINORI"), 15L);
        assertEquals(attesi, consulta(optA, optA, ANNO, 1), "la OPT vede il valore corretto");
        assertEquals(attesi, consulta(banco, optA, ANNO, 1), "il Banco vede il valore corretto");
        assertEquals(Map.of(
                        chiave(optA, m01, "PERSONE_RAGGIUNTE"), 80L,
                        chiave(optA, m01, "MINORI"), 15L), consulta(banco, optA, ANNO, 1, ANNO, 2),
                "il cumulativo usa il valore corretto, non quello sostituito");
    }

    @Test
    @DisplayName("C2 - senza termine fissato dal Banco la correzione abilitata è sempre ammessa")
    void c2_correzioneAmmessaSenzaTermine() throws Exception {
        registra(optA, ANNO, 1, voce(m01, "MINORI", 15)).andExpect(status().isNoContent());
        abilitaCorrezioni(null);

        registra(optA, ANNO, 1, voce(m01, "MINORI", 12)).andExpect(status().isNoContent());

        assertEquals(1, righe());
        assertEquals(12L, valoreSalvato(optA, ANNO, 1, m01, "MINORI"));
    }

    // ---------------------------------------------------------------------------------------
    // Criterio 2 - seconda OPT (isolamento)
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("C2 / AC6 - una seconda OPT non legge i conteggi della prima: 403 senza dati")
    void c2_secondaOptNonLegge() throws Exception {
        registra(optA, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 42)).andExpect(status().isNoContent());

        assertDiniegoSenzaCorpo(sessione(optB)
                .get(CONTEGGI + "?optId={opt}&anno={a}&trimestre={t}", optA, ANNO, 1)
                .andReturn().getResponse());
        // senza optId la vista di tutte le OPT è riservata al Banco
        assertDiniegoSenzaCorpo(sessione(optB)
                .get(CONTEGGI + "?anno={a}&trimestre={t}", ANNO, 1)
                .andReturn().getResponse());
    }

    @Test
    @DisplayName("C2 / AC6 - una seconda OPT scrive solo i propri conteggi e non modifica quelli della prima")
    void c2_secondaOptScriveSoloIPropri() throws Exception {
        registra(optA, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 42)).andExpect(status().isNoContent());
        Instant salvataggioA = aggiornatoIl(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE");

        // stessa chiave della prima OPT: per la seconda è un primo invio, non una correzione
        registra(optB, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 7)).andExpect(status().isNoContent());

        assertAll(
                () -> assertEquals(2, righe()),
                () -> assertEquals(42L, valoreSalvato(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE"), "prima OPT invariata"),
                () -> assertEquals(salvataggioA, aggiornatoIl(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE")),
                () -> assertEquals(7L, valoreSalvato(optB, ANNO, 1, m01, "PERSONE_RAGGIUNTE"), "riga propria della seconda"));
        assertEquals(Map.of(chiave(optA, m01, "PERSONE_RAGGIUNTE"), 42L), consulta(optA, optA, ANNO, 1));
        assertEquals(Map.of(chiave(optB, m01, "PERSONE_RAGGIUNTE"), 7L), consulta(optB, optB, ANNO, 1));
    }

    @Test
    @DisplayName("C2 / AC4 - il Banco consulta una OPT o tutte insieme, con i valori di ciascuna separati")
    void c2_bancoConsultaTutteLeOpt() throws Exception {
        registra(optA, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 42)).andExpect(status().isNoContent());
        registra(optB, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 7)).andExpect(status().isNoContent());

        assertEquals(Map.of(chiave(optA, m01, "PERSONE_RAGGIUNTE"), 42L), consulta(banco, optA, ANNO, 1));
        assertEquals(Map.of(
                chiave(optA, m01, "PERSONE_RAGGIUNTE"), 42L,
                chiave(optB, m01, "PERSONE_RAGGIUNTE"), 7L), consulta(banco, null, ANNO, 1));
    }

    @Test
    @DisplayName("AC6 - Banco e operatore non registrano conteggi: 403 e nessuna riga")
    void ac6_soloLaOptRegistra() throws Exception {
        assertDiniegoSenzaCorpo(registra(banco, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 10)).andReturn().getResponse());
        assertDiniegoSenzaCorpo(registra(operatore, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 10)).andReturn().getResponse());

        assertEquals(0, righe());
    }

    @Test
    @DisplayName("AC6 - l'operatore non consulta i conteggi: 403 senza dati")
    void ac6_operatoreNonConsulta() throws Exception {
        registra(optA, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", 42)).andExpect(status().isNoContent());

        assertDiniegoSenzaCorpo(sessione(operatore)
                .get(CONTEGGI + "?optId={opt}&anno={a}&trimestre={t}", optA, ANNO, 1)
                .andReturn().getResponse());
    }

    @Test
    @DisplayName("AC6 - senza accesso registrazione e consultazione ricevono 401 e nulla cambia")
    void ac6_senzaAccesso() throws Exception {
        SessioneDiProva anonima = SessioneDiProva.anonima(mvc);

        assertNonAutenticatoSenzaDati(anonima.post(CONTEGGI, corpo(ANNO, 1, voce(m01, "MINORI", 3)))
                .andReturn().getResponse());
        assertNonAutenticatoSenzaDati(anonima.get(CONTEGGI + "?optId={opt}&anno={a}&trimestre={t}", optA, ANNO, 1)
                .andReturn().getResponse());

        assertEquals(0, righe());
    }

    // ---------------------------------------------------------------------------------------
    // Criterio 1 - primo invio concorrente (ritrasmissione senza raddoppio su PostgreSQL)
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("C1 / AC5 - primi invii identici e simultanei: ognuno riceve 204 o 409, una sola riga, nessun errore non gestito")
    void c1_primoInvioConcorrenteIdentico() throws Exception {
        List<Integer> stati = primiInviiConcorrenti(i -> 42);

        assertSoloEsitiPrevisti(stati);
        assertTrue(stati.contains(204), "almeno un invio deve essere registrato: " + stati);
        assertAll(
                () -> assertEquals(1, righe(), "il vincolo di unicità non deve produrre righe doppie"),
                () -> assertEquals(42L, valoreSalvato(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE")));
        assertEquals(Map.of(chiave(optA, m01, "PERSONE_RAGGIUNTE"), 42L), consulta(optA, optA, ANNO, 1),
                "la consultazione mostra il valore una sola volta, non sommato");
    }

    @Test
    @DisplayName("C1 / AC5 - primi invii simultanei con valori diversi: uno solo vince, gli altri ricevono 409")
    void c1_primoInvioConcorrenteConValoriDiversi() throws Exception {
        // correzioni disattivate: dopo il primo salvataggio un valore diverso è una correzione non ammessa
        List<Integer> stati = primiInviiConcorrenti(i -> 10 + i);

        assertSoloEsitiPrevisti(stati);
        Set<Long> accettati = new HashSet<>();
        for (int i = 0; i < stati.size(); i++) {
            if (stati.get(i) == 204) {
                accettati.add(10L + i);
            }
        }
        Long salvato = valoreSalvato(optA, ANNO, 1, m01, "PERSONE_RAGGIUNTE");
        assertAll(
                () -> assertEquals(1, righe(), "una sola riga per la voce"),
                () -> assertEquals(Set.of(salvato), accettati,
                        "un solo valore accettato, ed è quello registrato: esiti " + stati));
    }

    /**
     * Spedisce {@link #INVII_CONCORRENTI} primi invii della stessa voce (OPT A, trimestre 1, M01,
     * persone raggiunte) da sessioni distinte, come lo stesso account aperto su più dispositivi.
     *
     * Per rendere la gara ripetibile, il test tiene {@code FOR UPDATE} sul profilo della OPT:
     * l'inserimento del conteggio deve controllare la chiave esterna {@code opt_id} con
     * {@code FOR KEY SHARE} e quindi si ferma. Così tutti gli invii superano la ricerca
     * («nessuna riga») prima che il primo inserimento sia confermato. Quando tutti risultano in
     * attesa di un lock su PostgreSQL (o dopo 10 secondi, se un'implementazione diversa non si ferma
     * su quel lock) il test lo rilascia.
     *
     * @param valore valore inviato dall'i-esimo invio
     * @return stato HTTP di ogni invio, nell'ordine; {@code 500} se l'eccezione non è stata convertita
     */
    private List<Integer> primiInviiConcorrenti(IntUnaryOperator valore) throws Exception {
        List<SessioneDiProva> sessioniDellaOpt = new ArrayList<>();
        for (int i = 0; i < INVII_CONCORRENTI; i++) {
            sessioniDellaOpt.add(accesso.accedi(optA));
        }

        ExecutorService esecutore = Executors.newFixedThreadPool(INVII_CONCORRENTI);
        List<Future<Integer>> esiti = new ArrayList<>();
        try {
            try (Connection blocco = dataSource.getConnection()) {
                blocco.setAutoCommit(false);
                int processoDelBlocco = bloccaProfiloOpt(blocco, optA);
                try {
                    for (int i = 0; i < INVII_CONCORRENTI; i++) {
                        SessioneDiProva sessione = sessioniDellaOpt.get(i);
                        String json = corpo(ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", valore.applyAsInt(i)));
                        esiti.add(esecutore.submit(() -> statoDellInvio(sessione, json)));
                    }
                    attendiInviiInAttesaDiLock(processoDelBlocco, INVII_CONCORRENTI);
                } finally {
                    blocco.rollback();
                }
            }
            List<Integer> stati = new ArrayList<>();
            for (Future<Integer> esito : esiti) {
                stati.add(esito.get(30, TimeUnit.SECONDS));
            }
            return stati;
        } finally {
            esecutore.shutdownNow();
        }
    }

    /**
     * Stato HTTP dell'invio, contato come {@link #statoDi(Richiesta)}.
     */
    private static int statoDellInvio(SessioneDiProva sessione, String json) {
        return statoDi(() -> sessione.post(CONTEGGI, json));
    }

    private static void assertSoloEsitiPrevisti(List<Integer> stati) {
        assertTrue(stati.stream().allMatch(s -> s == 204 || s == 409),
                "ogni invio concorrente deve ricevere 204 (idempotente) o 409 (conflitto), "
                        + "mai un errore non gestito: " + stati);
    }

    /** Prende {@code FOR UPDATE} sul profilo OPT e restituisce il processo PostgreSQL che lo tiene. */
    private static int bloccaProfiloOpt(Connection connessione, UUID optId) throws Exception {
        try (PreparedStatement blocco = connessione.prepareStatement(
                "select pg_backend_pid() from utenze_profili_opt where account_id = ? for update")) {
            blocco.setObject(1, optId);
            try (ResultSet riga = blocco.executeQuery()) {
                assertTrue(riga.next(), "profilo OPT da bloccare non trovato");
                return riga.getInt(1);
            }
        }
    }

    /** Attende che gli invii risultino in attesa di un lock su PostgreSQL, al più per 10 secondi. */
    private void attendiInviiInAttesaDiLock(int processoDelBlocco, int attesi) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            Integer inAttesa = jdbc.queryForObject("""
                    select count(*) from pg_stat_activity
                    where datname = current_database() and wait_event_type = 'Lock' and pid <> ?
                    """, Integer.class, processoDelBlocco);
            if (inAttesa != null && inAttesa >= attesi) {
                return;
            }
            Thread.sleep(20);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Richieste anomale: mai un errore non gestito
    // ---------------------------------------------------------------------------------------

    /**
     * Corpi di registrazione malformati nella struttura o nei tipi. {@code {misura}} è sostituito con
     * l'id di una misura esistente, {@code {opt}} con quello della prima OPT.
     */
    static Stream<Arguments> corpiAnomali() {
        String voce = "{\"misuraId\": {misura}, \"voce\": \"MINORI\", \"valore\": 3}";
        String radice = "\"anno\": 2026, \"trimestre\": 1";
        return Stream.of(
                Arguments.of("corpo JSON null", "null"),
                Arguments.of("corpo vuoto", ""),
                Arguments.of("elenco al posto dell'oggetto", "[" + voce + "]"),
                Arguments.of("testo al posto dell'oggetto", "\"conteggi\""),
                Arguments.of("voci come oggetto e non come elenco", "{" + radice + ", \"voci\": " + voce + "}"),
                Arguments.of("voce nulla nell'elenco", "{" + radice + ", \"voci\": [null]}"),
                Arguments.of("voce che non è un oggetto", "{" + radice + ", \"voci\": [42]}"),
                Arguments.of("campo sconosciuto nella radice",
                        "{" + radice + ", \"optId\": \"{opt}\", \"voci\": [" + voce + "]}"),
                Arguments.of("campo sconosciuto nella voce", "{" + radice
                        + ", \"voci\": [{\"misuraId\": {misura}, \"voce\": \"MINORI\", \"valore\": 3, \"nota\": \"x\"}]}"),
                Arguments.of("anno come testo", "{\"anno\": \"2026\", \"trimestre\": 1, \"voci\": [" + voce + "]}"),
                Arguments.of("anno oltre il limite di int", "{\"anno\": 3000000000, \"trimestre\": 1, \"voci\": [" + voce + "]}"),
                Arguments.of("anno fuori dall'intervallo ammesso", "{\"anno\": 99999, \"trimestre\": 1, \"voci\": [" + voce + "]}"),
                Arguments.of("trimestre decimale", "{\"anno\": 2026, \"trimestre\": 1.5, \"voci\": [" + voce + "]}"),
                Arguments.of("misuraId zero", "{" + radice
                        + ", \"voci\": [{\"misuraId\": 0, \"voce\": \"MINORI\", \"valore\": 3}]}"),
                Arguments.of("misuraId negativo", "{" + radice
                        + ", \"voci\": [{\"misuraId\": -5, \"voce\": \"MINORI\", \"valore\": 3}]}"),
                Arguments.of("misuraId oltre il limite di long", "{" + radice
                        + ", \"voci\": [{\"misuraId\": 9223372036854775808, \"voce\": \"MINORI\", \"valore\": 3}]}"),
                Arguments.of("misuraId come testo", "{" + radice
                        + ", \"voci\": [{\"misuraId\": \"{misura}\", \"voce\": \"MINORI\", \"valore\": 3}]}"),
                Arguments.of("voce numerica", "{" + radice
                        + ", \"voci\": [{\"misuraId\": {misura}, \"voce\": 7, \"valore\": 3}]}"),
                Arguments.of("codice voce molto lungo", "{" + radice
                        + ", \"voci\": [{\"misuraId\": {misura}, \"voce\": \"" + "X".repeat(300) + "\", \"valore\": 3}]}"),
                Arguments.of("valore booleano", "{" + radice
                        + ", \"voci\": [{\"misuraId\": {misura}, \"voce\": \"MINORI\", \"valore\": true}]}"),
                Arguments.of("valore oltre il limite di long", "{" + radice
                        + ", \"voci\": [{\"misuraId\": {misura}, \"voce\": \"MINORI\", \"valore\": 9223372036854775808}]}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpiAnomali")
    @DisplayName("Robustezza - una registrazione malformata riceve 400, mai un errore non gestito, e non scrive nulla")
    void registrazioneAnomalaSenzaErroreNonGestito(String caso, String modello) throws Exception {
        String json = modello.replace("{misura}", Long.toString(m01)).replace("{opt}", optA.toString());
        SessioneDiProva sessione = sessione(optA);

        assertEquals(400, statoDi(() -> sessione.post(CONTEGGI, json)), caso);
        assertEquals(0, righe(), caso);
    }

    /** Consultazioni del Banco con parametri anomali; {@code {opt}} e {@code {banco}} sono sostituiti. */
    static Stream<Arguments> consultazioniAnomale() {
        return Stream.of(
                Arguments.of("anno non numerico", "?optId={opt}&anno=abc&trimestre=1", 400),
                Arguments.of("anno assente", "?optId={opt}&trimestre=1", 400),
                Arguments.of("anno oltre il limite di int", "?optId={opt}&anno=3000000000&trimestre=1", 400),
                Arguments.of("anno fuori dall'intervallo ammesso", "?optId={opt}&anno=99999&trimestre=1", 400),
                Arguments.of("trimestre fuori intervallo", "?optId={opt}&anno=2026&trimestre=5", 400),
                Arguments.of("trimestre finale fuori intervallo",
                        "?optId={opt}&anno=2026&trimestre=1&annoFine=2026&trimestreFine=0", 400),
                Arguments.of("intervallo rovesciato", "?optId={opt}&anno=2026&trimestre=3&annoFine=2026&trimestreFine=1", 400),
                Arguments.of("optId che non è un UUID", "?optId=abc&anno=2026&trimestre=1", 400),
                Arguments.of("optId inesistente", "?optId=" + UUID.randomUUID() + "&anno=2026&trimestre=1", 404),
                Arguments.of("optId di un account che non è una OPT", "?optId={banco}&anno=2026&trimestre=1", 404));
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("consultazioniAnomale")
    @DisplayName("Robustezza - una consultazione con parametri anomali riceve un errore previsto, mai un errore non gestito")
    void consultazioneAnomalaSenzaErroreNonGestito(String caso, String query, int atteso) throws Exception {
        String percorso = CONTEGGI + query.replace("{opt}", optA.toString()).replace("{banco}", banco.toString());
        SessioneDiProva sessione = sessione(banco);

        assertEquals(atteso, statoDi(() -> sessione.get(percorso)), caso);
    }

    @Test
    @DisplayName("Robustezza - un cumulativo oltre il massimo rappresentabile riceve 409, mai un errore non gestito")
    void cumulativoOltreIlMassimoSenzaErroreNonGestito() throws Exception {
        registra(optA, ANNO, 1, voce(m01, "PERSONE_RAGGIUNTE", Long.MAX_VALUE)).andExpect(status().isNoContent());
        registra(optA, ANNO, 2, voce(m01, "PERSONE_RAGGIUNTE", 1)).andExpect(status().isNoContent());
        SessioneDiProva sessione = sessione(banco);
        String intervallo = CONTEGGI + "?optId=" + optA + "&anno=" + ANNO + "&trimestre=1&annoFine=" + ANNO
                + "&trimestreFine=2";

        assertEquals(409, statoDi(() -> sessione.get(intervallo)), "somma dei due trimestri oltre Long.MAX_VALUE");
        assertEquals(Map.of(chiave(optA, m01, "PERSONE_RAGGIUNTE"), Long.MAX_VALUE), consulta(banco, optA, ANNO, 1),
                "il singolo trimestre resta consultabile");
    }

    /** Una richiesta HTTP da eseguire; può lanciare l'eccezione che nessun gestore ha convertito. */
    @FunctionalInterface
    private interface Richiesta {
        ResultActions esegui() throws Exception;
    }

    /**
     * Stato HTTP della risposta. MockMvc rilancia le eccezioni che nessun gestore ha convertito: in
     * esercizio diventerebbero un {@code 500}, e come tale vengono contate.
     */
    private static int statoDi(Richiesta richiesta) {
        try {
            return richiesta.esegui().andReturn().getResponse().getStatus();
        } catch (Exception nonGestita) {
            return 500;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Supporto
    // ---------------------------------------------------------------------------------------

    private SessioneDiProva sessione(UUID account) throws Exception {
        SessioneDiProva esistente = sessioni.get(account);
        if (esistente == null) {
            esistente = accesso.accedi(account);
            sessioni.put(account, esistente);
        }
        return esistente;
    }

    private static String voce(long misuraId, String voce, long valore) {
        return "{\"misuraId\": " + misuraId + ", \"voce\": \"" + voce + "\", \"valore\": " + valore + "}";
    }

    private static String corpo(int anno, int trimestre, String... voci) {
        return "{\"anno\": " + anno + ", \"trimestre\": " + trimestre + ", \"voci\": [" + String.join(", ", voci) + "]}";
    }

    private ResultActions registra(UUID attore, int anno, int trimestre, String... voci) throws Exception {
        return sessione(attore).post(CONTEGGI, corpo(anno, trimestre, voci));
    }

    private Map<String, Long> consulta(UUID attore, UUID optId, int anno, int trimestre) throws Exception {
        return consulta(attore, optId, anno, trimestre, anno, trimestre);
    }

    /**
     * Consulta i conteggi via HTTP e li restituisce come {@code optId|misura|voce -> valore}:
     * il confronto non dipende dall'ordine della risposta e rileva le chiavi ripetute.
     */
    private Map<String, Long> consulta(UUID attore, UUID optId, int anno, int trimestre, int annoFine,
                                       int trimestreFine) throws Exception {
        String percorso = CONTEGGI + "?anno={a}&trimestre={t}&annoFine={af}&trimestreFine={tf}";
        ResultActions risposta = optId == null
                ? sessione(attore).get(percorso, anno, trimestre, annoFine, trimestreFine)
                : sessione(attore).get(percorso + "&optId={opt}", anno, trimestre, annoFine, trimestreFine, optId);
        String json = risposta.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> conteggi = JsonPath.read(json, "$");
        Map<String, Long> valori = new LinkedHashMap<>();
        for (Map<String, Object> conteggio : conteggi) {
            Map<String, Object> voce = asMappa(conteggio.get("conteggio"));
            String chiave = chiave(UUID.fromString((String) conteggio.get("optId")),
                    ((Number) voce.get("misuraId")).longValue(), (String) voce.get("voce"));
            Long precedente = valori.put(chiave, valore(voce.get("valore")));
            assertNull(precedente, () -> "chiave ripetuta nella consultazione: " + chiave);
        }
        return valori;
    }

    /**
     * Valore di un conteggio nella risposta: numero JSON fino al massimo intero esatto in JavaScript,
     * testo decimale oltre quel limite.
     */
    private static long valore(Object json) {
        return json instanceof Number numero ? numero.longValue() : Long.parseLong((String) json);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMappa(Object valore) {
        return (Map<String, Object>) valore;
    }

    private static String chiave(UUID optId, long misuraId, String voce) {
        return optId + "|" + misuraId + "|" + voce;
    }

    private int righe() {
        return jdbc.queryForObject("select count(*) from monitoraggio_conteggi_opt", Integer.class);
    }

    /** Valore registrato su PostgreSQL, oppure {@code null} se la riga non esiste. */
    private Long valoreSalvato(UUID optId, int anno, int trimestre, long misuraId, String voce) {
        List<Long> valori = jdbc.queryForList("""
                select valore from monitoraggio_conteggi_opt
                where opt_id = ? and anno = ? and trimestre = ? and misura_id = ? and voce = ?
                """, Long.class, optId, anno, trimestre, misuraId, voce);
        return valori.isEmpty() ? null : valori.get(0);
    }

    private Instant aggiornatoIl(UUID optId, int anno, int trimestre, long misuraId, String voce) {
        return jdbc.queryForObject("""
                select aggiornato_il from monitoraggio_conteggi_opt
                where opt_id = ? and anno = ? and trimestre = ? and misura_id = ? and voce = ?
                """, Timestamp.class, optId, anno, trimestre, misuraId, voce).toInstant();
    }

    /** Correzioni abilitate dal Banco fino al termine indicato, oppure senza termine con {@code null}. */
    private void abilitaCorrezioni(Instant scadenza) {
        jdbc.update("update monitoraggio_configurazione set correzioni_abilitate = true, scadenza_correzioni = ?",
                scadenza == null ? null : Timestamp.from(scadenza));
    }
}