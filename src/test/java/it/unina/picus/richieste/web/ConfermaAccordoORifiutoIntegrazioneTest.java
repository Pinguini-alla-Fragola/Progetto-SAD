package it.unina.picus.richieste.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import it.unina.picus.shared.AccessoDiProva;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.shared.SessioneDiProva;

/**
 * Test di integrazione della user story #52 «Conferma dell'accordo o rifiuto della richiesta»,
 * richiesti dalla #116: endpoint HTTP reali, login reale e PostgreSQL.
 *
 * Ogni test riporta nel nome l'Acceptance Criterion della #52 che verifica (AC1..AC9). I test
 * marcati «#116» coprono i criteri della task che la user story non esprime: compatibilità della
 * modalità, assenza di scritture parziali, richieste che hanno già un esito, accessi senza
 * sessione e comandi concorrenti.
 *
 * Lo scenario nasce dal flusso dell'applicazione: la OPT titolare invia la richiesta, il Banco
 * la prende in carico, interpella un candidato, ne riceve la disponibilità e lo assegna. Ogni
 * attore accede con il login reale e opera nella propria sessione. Nel database si inseriscono
 * con {@link DatiSintetici} soltanto account, profili, offerte e l'abilitazione a distanza delle
 * misure, che l'applicazione non permette ancora di gestire.
 *
 * Visibilità e persistenza hanno verifiche distinte: i dettagli dell'accordo si leggono dalle
 * API e dalla pagina di dettaglio di chi è autorizzato, quelli registrati dalla riga su
 * PostgreSQL. Ogni {@code 409} viene verificato anche nel corpo: errore JSON comune del modulo
 * Richieste, senza dati della pratica.
 */
@IntegrazionePostgresql
@Tag("issue-52")
@DisplayName("#52 Conferma dell'accordo o rifiuto della richiesta: integrazione su HTTP e PostgreSQL")
class ConfermaAccordoORifiutoIntegrazioneTest {

    private static final String DESCRIZIONE_RISERVATA = "Descrizione riservata a titolare e Banco";
    private static final String DATA_ORA = "2030-03-14T10:30:00+01:00";
    private static final Instant ISTANTE = Instant.parse("2030-03-14T09:30:00Z");
    private static final String LUOGO = "Sede di prova, via Sintetica 1";
    private static final String NOME = "Referente";
    private static final String COGNOME = "Sintetico";
    private static final String TELEFONO = "+39 081 000 0000";
    private static final String LINK = "https://videochiamata.example/stanza-di-prova";
    private static final String NESSUN_EROGATORE = "Nessun erogatore disponibile";

    /**
     * Comandi inviati insieme nei test di concorrenza. Durante l'attesa del lock ognuno occupa
     * una connessione: con quella del test e quella del controllo restano sotto il pool
     * predefinito di Hikari (10).
     */
    private static final int COMANDI_CONCORRENTI = 6;

    /** Account autenticati diversi dal Banco. */
    enum NonBanco {
        OPT_EROGATRICE_ASSEGNATA,
        OPT_TITOLARE,
        OPT_ESTRANEA,
        OPERATORE_ESTRANEO
    }

    /** Account senza legame con la pratica confermata. */
    enum Estraneo {
        OPT_ESTRANEA,
        OPT_CANDIDATA_CHE_HA_DECLINATO,
        OPERATORE_ESTRANEO
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordEncoder encoder;

    private DatiSintetici dati;
    private AccessoDiProva accesso;
    private final Map<UUID, SessioneDiProva> sessioni = new HashMap<>();

    private long misuraId;
    private UUID banco;
    private UUID titolare;
    private UUID erogatrice;
    private UUID altraCandidata;
    private UUID optEstranea;
    private UUID operatoreEstraneo;

    @BeforeEach
    void preparaAttori() {
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        accesso = new AccessoDiProva(mvc, jdbc, encoder);
        sessioni.clear();

        misuraId = dati.misura("M01");
        banco = dati.banco("Banco di prova");
        titolare = dati.opt("OPT titolare", "napoli");
        erogatrice = optErogatrice("OPT erogatrice", misuraId);
        altraCandidata = optErogatrice("OPT candidata alternativa", misuraId);
        optEstranea = dati.opt("OPT estranea", "napoli");
        operatoreEstraneo = dati.operatore("Operatore estraneo", "INTERNO_BANCO", "napoli", false);
    }

    // ------------------------------------------------------------------ AC1, AC2

    @Test
    @DisplayName("AC1 - accordo in presenza: Confermata e dettagli disponibili a OPT titolare ed erogatore assegnato")
    void ac1_confermaInPresenza() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);

        ResultActions esito = conferma(richiestaId, json(inPresenza()));

        assertConfermataInPresenza(esito, erogatrice);
        assertAccordoInPresenzaPersistito(richiestaId, erogatrice);
        assertConfermataInPresenza(come(banco).get("/api/richieste/{id}", richiestaId), erogatrice);
        assertConfermataInPresenza(come(titolare).get("/api/richieste/{id}", richiestaId), erogatrice);
        assertConfermataInPresenza(come(erogatrice).get("/api/richieste/{id}", richiestaId), erogatrice)
                // all'erogatore non arrivano i dati riservati alla OPT titolare
                .andExpect(jsonPath("$.titolareId").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist());
        come(titolare).get("/api/richieste/mie")
                .andExpect(jsonPath("$[?(@.id == '" + richiestaId + "')].stato", hasItem("CONFERMATA")));
        for (UUID autorizzato : List.of(titolare, erogatrice)) {
            assertPaginaDelDettaglioMostra(autorizzato, richiestaId, LUOGO, NOME + " " + COGNOME, TELEFONO);
        }
    }

    @ParameterizedTest(name = "AC1 - i dettagli confermati sono disponibili anche all''operatore {0} assegnato")
    @ValueSource(strings = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    void ac1_confermaConOperatoreAssegnato(String tipologia) throws Exception {
        UUID operatore = dati.operatore("Operatore assegnato", tipologia, "napoli", false, misuraId);
        UUID richiestaId = richiestaInProgrammazione(operatore);

        conferma(richiestaId, json(inPresenza())).andExpect(status().isOk());

        assertAccordoInPresenzaPersistito(richiestaId, operatore);
        assertConfermataInPresenza(come(operatore).get("/api/richieste/{id}", richiestaId), operatore);
        assertConfermataInPresenza(come(titolare).get("/api/richieste/{id}", richiestaId), operatore);
    }

    @Test
    @DisplayName("AC2 - accordo a distanza: Confermata e dettagli disponibili a OPT titolare ed erogatore assegnato")
    void ac2_confermaADistanza() throws Exception {
        ScenarioADistanza scenario = richiestaADistanzaInProgrammazione();

        ResultActions esito = conferma(scenario.richiestaId(), json(aDistanza()));

        assertConfermataADistanza(esito, scenario.centroPicus());
        assertAccordoADistanzaPersistito(scenario.richiestaId(), scenario.centroPicus());
        assertConfermataADistanza(come(banco).get("/api/richieste/{id}", scenario.richiestaId()), scenario.centroPicus());
        assertConfermataADistanza(come(titolare).get("/api/richieste/{id}", scenario.richiestaId()), scenario.centroPicus());
        assertConfermataADistanza(
                come(scenario.centroPicus()).get("/api/richieste/{id}", scenario.richiestaId()), scenario.centroPicus());
        for (UUID autorizzato : List.of(titolare, scenario.centroPicus())) {
            assertPaginaDelDettaglioMostra(autorizzato, scenario.richiestaId(), LINK);
        }
    }

    // ------------------------------------------------------------------ AC3 e compatibilità (#116)

    static Stream<Arguments> accordiInPresenzaNonValidi() {
        return Stream.of(
                Arguments.of("senza data e ora", senza(inPresenza(), "dataOra"), "dataOra"),
                Arguments.of("con data senza fuso orario", con(inPresenza(), "dataOra", "2030-03-14T10:30:00"), "dataOra"),
                Arguments.of("con data illeggibile", con(inPresenza(), "dataOra", "domani mattina"), "dataOra"),
                Arguments.of("senza luogo", senza(inPresenza(), "luogo"), "luogo"),
                Arguments.of("con luogo vuoto", con(inPresenza(), "luogo", "   "), "luogo"),
                Arguments.of("con luogo non testuale", con(inPresenza(), "luogo", 42), "luogo"),
                Arguments.of("senza nome del referente", senza(inPresenza(), "nomeReferente"), "nomeReferente"),
                Arguments.of("senza cognome del referente", senza(inPresenza(), "cognomeReferente"), "cognomeReferente"),
                Arguments.of("senza telefono", senza(inPresenza(), "telefonoReferente"), "telefonoReferente"),
                Arguments.of("con telefono non valido", con(inPresenza(), "telefonoReferente", "chiamami"), "telefonoReferente"),
                Arguments.of("con un link, non previsto in presenza", con(inPresenza(), "link", LINK), "link"),
                Arguments.of("senza modalità", senza(inPresenza(), "modalita"), "modalita"),
                Arguments.of("con modalità sconosciuta", con(inPresenza(), "modalita", "TELEPATIA"), "modalita"),
                Arguments.of("con un campo estraneo", con(inPresenza(), "note", "testo libero"), "accordo"),
                Arguments.of("a distanza su un'assegnazione locale", aDistanza(), "modalita"));
    }

    @ParameterizedTest(name = "AC3 - assegnazione locale, accordo {0}: 400 su «{2}», resta In programmazione senza scritture")
    @MethodSource("accordiInPresenzaNonValidi")
    void ac3_datiNonValidiPerUnAccordoInPresenza(String caso, Map<String, Object> corpo, String campo) throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);

        assertDatiDaCompletare(conferma(richiestaId, json(corpo)), campo);

        assertInProgrammazioneSenzaAccordo(richiestaId, erogatrice);
    }

    static Stream<Arguments> accordiADistanzaNonValidi() {
        return Stream.of(
                Arguments.of("senza link", senza(aDistanza(), "link"), "link"),
                Arguments.of("con link non HTTP", con(aDistanza(), "link", "ftp://file.example/stanza"), "link"),
                Arguments.of("con link relativo", con(aDistanza(), "link", "/stanza-di-prova"), "link"),
                Arguments.of("con credenziali nel link", con(aDistanza(), "link", "https://utente:segreto@video.example/s"), "link"),
                Arguments.of("senza data e ora", senza(aDistanza(), "dataOra"), "dataOra"),
                Arguments.of("con un luogo, non previsto a distanza", con(aDistanza(), "luogo", LUOGO), "luogo"),
                Arguments.of("in presenza su un'assegnazione a distanza", inPresenza(), "modalita"));
    }

    @ParameterizedTest(name = "AC3 - assegnazione a distanza, accordo {0}: 400 su «{2}», resta In programmazione senza scritture")
    @MethodSource("accordiADistanzaNonValidi")
    void ac3_datiNonValidiPerUnAccordoADistanza(String caso, Map<String, Object> corpo, String campo) throws Exception {
        ScenarioADistanza scenario = richiestaADistanzaInProgrammazione();

        assertDatiDaCompletare(conferma(scenario.richiestaId(), json(corpo)), campo);

        assertInProgrammazioneSenzaAccordo(scenario.richiestaId(), scenario.centroPicus());
    }

    @Test
    @DisplayName("AC3 - con più dati mancanti l'errore li indica tutti insieme")
    void ac3_tuttiICampiDaCompletareInUnaRisposta() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);

        conferma(richiestaId, json(Map.of("modalita", "IN_PRESENZA")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codice").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.campi", hasKey("dataOra")))
                .andExpect(jsonPath("$.campi", hasKey("luogo")))
                .andExpect(jsonPath("$.campi", hasKey("nomeReferente")))
                .andExpect(jsonPath("$.campi", hasKey("cognomeReferente")))
                .andExpect(jsonPath("$.campi", hasKey("telefonoReferente")));

        assertInProgrammazioneSenzaAccordo(richiestaId, erogatrice);
    }

    @Test
    @DisplayName("AC3 - dopo l'errore il Banco completa i dati e la conferma riesce")
    void ac3_dopoLaCorrezioneLaConfermaRiesce() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);
        conferma(richiestaId, json(senza(inPresenza(), "telefonoReferente"))).andExpect(status().isBadRequest());

        assertConfermataInPresenza(conferma(richiestaId, json(inPresenza())), erogatrice);
        assertAccordoInPresenzaPersistito(richiestaId, erogatrice);
    }

    @Test
    @DisplayName("#116 - un corpo con chiavi duplicate riceve 400 e non scrive nulla")
    void corpoConChiaviDuplicate() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);
        String duplicato = json(inPresenza()).replaceFirst("\\{", "{\"luogo\": \"Altra sede\", ");

        conferma(richiestaId, duplicato)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codice").value("BAD_REQUEST"));

        assertInProgrammazioneSenzaAccordo(richiestaId, erogatrice);
    }

    @Test
    @DisplayName("#116 - se la misura non è più erogabile a distanza la conferma a distanza riceve 409 e non scrive nulla")
    void misuraNonPiuErogabileADistanza() throws Exception {
        ScenarioADistanza scenario = richiestaADistanzaInProgrammazione();
        // Il catalogo cambia dopo l'assegnazione: il Banco non può più confermare a distanza.
        jdbc.update("update catalogo_misure set erogabile_a_distanza = false where id = ?", scenario.misuraId());

        assertConflitto(conferma(scenario.richiestaId(), json(aDistanza())), scenario.richiestaId());

        assertInProgrammazioneSenzaAccordo(scenario.richiestaId(), scenario.centroPicus());
    }

    // ------------------------------------------------------------------ AC4

    @Test
    @DisplayName("AC4 - fino alla conferma esplicita del Banco la richiesta resta In programmazione senza accordo")
    void ac4_senzaConfermaRestaInProgrammazione() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);

        // Lo scambio di proposte avviene fuori dall'API: nessuna consultazione né tentativo
        // respinto fa avanzare lo stato o produce un accordo.
        conferma(richiestaId, json(senza(inPresenza(), "luogo"))).andExpect(status().isBadRequest());
        conferma(richiestaId, json(aDistanza())).andExpect(status().isBadRequest());

        assertInProgrammazioneSenzaAccordo(richiestaId, erogatrice);
    }

    // ------------------------------------------------------------------ AC5

    @Test
    @DisplayName("AC5 - rifiuto da In programmazione: Rifiutata e «Nessun erogatore disponibile» per la OPT titolare")
    void ac5_rifiutoDaInProgrammazione() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);

        rifiuta(richiestaId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("RIFIUTATA"));

        assertRifiutataPerLaTitolare(richiestaId);
    }

    @Test
    @DisplayName("AC5 - rifiuto da Presa in carico dopo che tutti i candidati hanno declinato")
    void ac5_rifiutoDaPresaInCaricoDopoIDinieghi() throws Exception {
        UUID richiestaId = inviaEPrendiInCarico(misuraId);
        rispondi(erogatrice, interpella(richiestaId, erogatrice, "STESSO_COMUNE"), false);
        rispondi(altraCandidata, interpella(richiestaId, altraCandidata, "STESSO_COMUNE"), false);

        rifiuta(richiestaId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("RIFIUTATA"));

        assertRifiutataPerLaTitolare(richiestaId);
    }

    @Test
    @DisplayName("AC5 - rifiuto da Presa in carico quando la misura non ha erogatori compatibili")
    void ac5_rifiutoDaPresaInCaricoSenzaCandidati() throws Exception {
        // dopo svuota() nessuno offre la M02 e nessuna misura è erogabile a distanza
        UUID richiestaId = inviaEPrendiInCarico(dati.misura("M02"));

        rifiuta(richiestaId, null).andExpect(status().isOk());

        assertRifiutataPerLaTitolare(richiestaId);
    }

    @Test
    @DisplayName("AC5 - finché restano candidati locali non interpellati il rifiuto definitivo è respinto")
    void ac5_rifiutoRespintoConCandidatiLocaliDaValutare() throws Exception {
        UUID richiestaId = inviaEPrendiInCarico(misuraId);
        assertConflitto(rifiuta(richiestaId, null), richiestaId);

        rispondi(erogatrice, interpella(richiestaId, erogatrice, "STESSO_COMUNE"), false);
        assertConflitto(rifiuta(richiestaId, null), richiestaId);

        assertPresaInCaricoPerLaTitolare(richiestaId);
    }

    @Test
    @DisplayName("AC5 - il remoto compatibile va valutato prima del rifiuto definitivo")
    void ac5_rifiutoRespintoSeRestaIlRemoto() throws Exception {
        long misuraRemota = dati.misura("M03");
        dati.abilitaADistanza(misuraRemota);
        UUID locale = optErogatrice("OPT erogatrice locale", misuraRemota);
        UUID centroPicus = centroPicus(misuraRemota);
        UUID richiestaId = inviaEPrendiInCarico(misuraRemota);
        rispondi(locale, interpella(richiestaId, locale, "STESSO_COMUNE"), false);

        assertConflitto(rifiuta(richiestaId, null), richiestaId);
        assertPresaInCaricoPerLaTitolare(richiestaId);

        rispondi(centroPicus, interpella(richiestaId, centroPicus, "CENTRO_PICUS_A_DISTANZA"), false);
        rifiuta(richiestaId, null).andExpect(status().isOk());

        assertRifiutataPerLaTitolare(richiestaId);
    }

    @Test
    @DisplayName("AC5 - con un contatto ancora in attesa il rifiuto definitivo è respinto")
    void ac5_rifiutoRespintoConContattoInAttesa() throws Exception {
        UUID richiestaId = inviaEPrendiInCarico(misuraId);
        rispondi(erogatrice, interpella(richiestaId, erogatrice, "STESSO_COMUNE"), false);
        interpella(richiestaId, altraCandidata, "STESSO_COMUNE");

        assertConflitto(rifiuta(richiestaId, null), richiestaId);

        assertPresaInCaricoPerLaTitolare(richiestaId);
    }

    @Test
    @DisplayName("AC5 - il rifiuto non richiede una motivazione: basta un corpo vuoto")
    void ac5_rifiutoSenzaMotivazione() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);

        rifiuta(richiestaId, "{}").andExpect(status().isOk());

        assertRifiutataPerLaTitolare(richiestaId);
    }

    @Test
    @DisplayName("AC5 - il comando di rifiuto non accetta campi, nemmeno una motivazione libera")
    void ac5_rifiutoConCampiRespinto() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);

        rifiuta(richiestaId, json(Map.of("motivazione", "Nessuna soluzione")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codice").value("BAD_REQUEST"));

        assertInProgrammazioneSenzaAccordo(richiestaId, erogatrice);
    }

    // ------------------------------------------------------------------ AC6

    @Test
    @DisplayName("AC6 - il diniego di un candidato lascia Presa in carico e il Banco cerca un'alternativa")
    void ac6_diniegoDelCandidatoNonRifiutaLaRichiesta() throws Exception {
        UUID richiestaId = inviaEPrendiInCarico(misuraId);

        UUID diniego = interpella(richiestaId, erogatrice, "STESSO_COMUNE");
        rispondi(erogatrice, diniego, false);

        assertPresaInCaricoPerLaTitolare(richiestaId);
        assertEquals("NON_DISPONIBILE", dati.esitoContatto(diniego), "esito del candidato su PostgreSQL");
        come(banco).get("/api/richieste/{id}/candidati", richiestaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].erogatore.accountId", hasItem(altraCandidata.toString())));
        UUID alternativa = interpella(richiestaId, altraCandidata, "STESSO_COMUNE");
        come(altraCandidata).get("/api/contatti-disponibilita/{id}", alternativa)
                .andExpect(jsonPath("$.contatto.esito").value("IN_ATTESA"));
    }

    // ------------------------------------------------------------------ AC7 e richieste con un esito (#116)

    @Test
    @DisplayName("AC7 - dopo la conferma il server nega ogni modifica dei dettagli e conserva i valori")
    void ac7_accordoConfermatoNonModificabile() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);
        conferma(richiestaId, json(inPresenza())).andExpect(status().isOk());
        Map<String, Object> salvato = rigaDellaRichiesta(richiestaId);

        assertConflitto(conferma(richiestaId, json(con(inPresenza(), "luogo", "Altra sede"))), richiestaId);
        assertConflitto(conferma(richiestaId, json(con(inPresenza(), "dataOra", "2030-04-01T09:00:00+02:00"))), richiestaId);
        assertConflitto(rifiuta(richiestaId, null), richiestaId);

        assertEquals(salvato, rigaDellaRichiesta(richiestaId));
        assertConfermataInPresenza(come(titolare).get("/api/richieste/{id}", richiestaId), erogatrice);
    }

    @ParameterizedTest(name = "#116 - richiesta {0}: conferma e rifiuto ricevono 409 e i dati restano invariati")
    @ValueSource(strings = {"CREATA", "PRESA_IN_CARICO", "CONFERMATA", "RIFIUTATA", "ANNULLATA"})
    void comandiSuUnaRichiestaNonInLavorazione(String stato) throws Exception {
        UUID richiestaId = richiestaNelloStato(stato);
        Map<String, Object> prima = rigaDellaRichiesta(richiestaId);

        assertConflitto(conferma(richiestaId, json(inPresenza())), richiestaId);
        if (!stato.equals("PRESA_IN_CARICO")) {
            // da Presa in carico il rifiuto è ammesso: i suoi casi sono negli AC5
            assertConflitto(rifiuta(richiestaId, null), richiestaId);
        }

        assertEquals(prima, rigaDellaRichiesta(richiestaId));
    }

    // ------------------------------------------------------------------ AC8 e dinieghi (#116)

    @ParameterizedTest(name = "AC8 - {0} non può confermare l''accordo: 403 e stato e dati invariati")
    @EnumSource(NonBanco.class)
    void ac8_soloIlBancoConferma(NonBanco attore) throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);

        come(account(attore)).post("/api/richieste/{id}/conferma", json(inPresenza()), richiestaId)
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));

        assertInProgrammazioneSenzaAccordo(richiestaId, erogatrice);
    }

    @ParameterizedTest(name = "AC8 - l''operatore {0} assegnato non può confermare l''accordo")
    @ValueSource(strings = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    void ac8_operatoreAssegnatoNonConferma(String tipologia) throws Exception {
        UUID operatore = dati.operatore("Operatore assegnato", tipologia, "napoli", false, misuraId);
        UUID richiestaId = richiestaInProgrammazione(operatore);

        come(operatore).post("/api/richieste/{id}/conferma", json(inPresenza()), richiestaId)
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));

        assertInProgrammazioneSenzaAccordo(richiestaId, operatore);
    }

    @ParameterizedTest(name = "#116 - {0} non può registrare il rifiuto definitivo: 403 e stato invariato")
    @EnumSource(NonBanco.class)
    void soloIlBancoRifiuta(NonBanco attore) throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);

        come(account(attore)).post("/api/richieste/{id}/rifiuta", null, richiestaId)
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));

        assertInProgrammazioneSenzaAccordo(richiestaId, erogatrice);
    }

    @Test
    @DisplayName("#116 - senza accesso conferma, rifiuto e consultazione ricevono 401 e nulla cambia")
    void senzaAccesso() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);
        SessioneDiProva anonima = SessioneDiProva.anonima(mvc);

        anonima.post("/api/richieste/{id}/conferma", json(inPresenza()), richiestaId).andExpect(status().isUnauthorized());
        anonima.post("/api/richieste/{id}/rifiuta", null, richiestaId).andExpect(status().isUnauthorized());
        anonima.get("/api/richieste/{id}", richiestaId).andExpect(status().isUnauthorized());

        assertInProgrammazioneSenzaAccordo(richiestaId, erogatrice);
    }

    @Test
    @DisplayName("#116 - dopo la revoca dell'account Banco la sua sessione non può più confermare né rifiutare")
    void accountBancoRevocato() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);
        UUID altroBanco = dati.banco("Banco revocato");
        SessioneDiProva sessione = come(altroBanco);
        sessione.get("/api/richieste/{id}", richiestaId).andExpect(status().isOk());

        dati.impostaStatoAccount(altroBanco, "REVOCATO");

        sessione.post("/api/richieste/{id}/conferma", json(inPresenza()), richiestaId)
                .andExpect(status().isUnauthorized());
        sessione.post("/api/richieste/{id}/rifiuta", null, richiestaId)
                .andExpect(status().isUnauthorized());
        assertInProgrammazioneSenzaAccordo(richiestaId, erogatrice);
    }

    // ------------------------------------------------------------------ AC9

    @ParameterizedTest(name = "AC9 - {0} che consulta una richiesta confermata riceve 403 senza dati")
    @EnumSource(Estraneo.class)
    void ac9_consultazioneNegataAChiNonHaLegami(Estraneo estraneo) throws Exception {
        UUID richiestaId = inviaEPrendiInCarico(misuraId);
        rispondi(altraCandidata, interpella(richiestaId, altraCandidata, "STESSO_COMUNE"), false);
        assegna(richiestaId, erogatrice);
        conferma(richiestaId, json(inPresenza())).andExpect(status().isOk());

        come(account(estraneo)).get("/api/richieste/{id}", richiestaId)
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("AC9 - una OPT estranea non trova la richiesta nei propri elenchi")
    void ac9_richiestaAssenteDagliElenchiDellaOptEstranea() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);
        conferma(richiestaId, json(inPresenza())).andExpect(status().isOk());

        come(optEstranea).get("/api/richieste/mie")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + richiestaId + "')]").isEmpty());
    }

    // ------------------------------------------------------------------ comandi concorrenti (#116)

    /**
     * Il test tiene il lock della riga della richiesta, lo stesso che il servizio acquisisce
     * prima di applicare conferma o rifiuto. I comandi partono e si fermano su quel lock: il test
     * attende di osservarli tutti in attesa su PostgreSQL, quindi contemporaneamente in corso nel
     * punto in cui si decide l'esito, e solo allora rilascia il lock.
     */
    @Test
    @DisplayName("#116 - conferme e rifiuti concorrenti fermi sul lock: uno solo riceve 200, gli altri 409, esito coerente")
    void confermaERifiutoConcorrenti() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);

        List<MvcResult> esiti = inParalleloSulLock(richiestaId, (sessione, i) -> i % 2 == 0
                ? sessione.post("/api/richieste/{id}/conferma", json(inPresenza()), richiestaId)
                : sessione.post("/api/richieste/{id}/rifiuta", null, richiestaId));

        int vincitore = unicoAccettato(esiti, richiestaId);
        if (vincitore % 2 == 0) {
            assertAccordoInPresenzaPersistito(richiestaId, erogatrice);
            assertConfermataInPresenza(come(titolare).get("/api/richieste/{id}", richiestaId), erogatrice);
        } else {
            assertRifiutataPerLaTitolare(richiestaId);
        }
    }

    @Test
    @DisplayName("#116 - conferme concorrenti con dettagli diversi: resta solo l'accordo della conferma accettata")
    void confermeConcorrenti() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);

        List<MvcResult> esiti = inParalleloSulLock(richiestaId, (sessione, i) -> sessione.post(
                "/api/richieste/{id}/conferma", json(con(inPresenza(), "luogo", "Sede " + i)), richiestaId));

        int vincitore = unicoAccettato(esiti, richiestaId);
        Map<String, Object> salvato = rigaDellaRichiesta(richiestaId);
        assertAll("accordo su PostgreSQL",
                () -> assertEquals("CONFERMATA", salvato.get("stato")),
                () -> assertEquals("Sede " + vincitore, salvato.get("accordo_luogo")));
        come(titolare).get("/api/richieste/{id}", richiestaId)
                .andExpect(jsonPath("$.accordo.luogo").value("Sede " + vincitore));
    }

    // ------------------------------------------------------------------ passi del flusso

    /** Sessione autenticata dell'account, aperta al primo uso e riusata nel resto del test. */
    private SessioneDiProva come(UUID account) throws Exception {
        SessioneDiProva sessione = sessioni.get(account);
        if (sessione == null) {
            sessione = accesso.accedi(account);
            sessioni.put(account, sessione);
        }
        return sessione;
    }

    /** OPT di Napoli che offre la misura: compare fra i candidati dello stesso comune. */
    private UUID optErogatrice(String nome, long misura) {
        UUID opt = dati.opt(nome, "napoli");
        dati.offerta(opt, misura);
        return opt;
    }

    /** Operatore del Centro PICUS in un comune non adiacente a Napoli: raggiungibile solo a distanza. */
    private UUID centroPicus(long misura) {
        return dati.operatore("Centro PICUS", "INTERNO_BANCO", DatiSintetici.COMUNE_LONTANO, true, misura);
    }

    /** La OPT titolare invia una richiesta per la misura e il Banco la prende in carico. */
    private UUID inviaEPrendiInCarico(long misura) throws Exception {
        UUID id = invia(misura);
        come(banco).post("/api/richieste/{id}/presa-in-carico", null, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("PRESA_IN_CARICO"));
        return id;
    }

    private UUID invia(long misura) throws Exception {
        String invio = json(Map.of("misuraId", misura, "tipoDestinatario", "PERSONA",
                "comuneCodice", "napoli", "description", DESCRIZIONE_RISERVATA));
        String creata = come(titolare).post("/api/richieste", invio)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(JsonPath.read(creata, "$.id"));
    }

    private UUID interpella(UUID richiesta, UUID erogatore, String ambito) throws Exception {
        String creato = come(banco)
                .post("/api/richieste/{id}/contatti",
                        json(Map.of("erogatoreId", erogatore.toString(), "ambito", ambito)), richiesta)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(JsonPath.read(creato, "$.id"));
    }

    private void rispondi(UUID erogatore, UUID contatto, boolean disponibile) throws Exception {
        come(erogatore).post("/api/contatti-disponibilita/{id}/risposta",
                        "{\"disponibile\": " + disponibile + "}", contatto)
                .andExpect(status().isOk());
    }

    /** Il Banco interpella l'erogatore nello stesso comune, ne riceve la disponibilità e lo assegna. */
    private void assegna(UUID richiesta, UUID erogatore) throws Exception {
        assegna(richiesta, erogatore, "STESSO_COMUNE");
    }

    private void assegna(UUID richiesta, UUID erogatore, String ambito) throws Exception {
        UUID contatto = interpella(richiesta, erogatore, ambito);
        rispondi(erogatore, contatto, true);
        come(banco).post("/api/contatti-disponibilita/{id}/assegnazione", null, contatto)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("IN_PROGRAMMAZIONE"));
    }

    /** Richiesta In programmazione con l'erogatore assegnato nello stesso comune. */
    private UUID richiestaInProgrammazione(UUID erogatore) throws Exception {
        UUID richiestaId = inviaEPrendiInCarico(misuraId);
        assegna(richiestaId, erogatore);
        return richiestaId;
    }

    /** Richiesta assegnata al Centro PICUS a distanza: unica opzione per una misura erogabile a distanza. */
    private record ScenarioADistanza(UUID richiestaId, UUID centroPicus, long misuraId) {}

    private ScenarioADistanza richiestaADistanzaInProgrammazione() throws Exception {
        long misuraRemota = dati.misura("M04");
        dati.abilitaADistanza(misuraRemota);
        UUID centroPicus = centroPicus(misuraRemota);
        UUID richiestaId = inviaEPrendiInCarico(misuraRemota);
        assegna(richiestaId, centroPicus, "CENTRO_PICUS_A_DISTANZA");
        return new ScenarioADistanza(richiestaId, centroPicus, misuraRemota);
    }

    /**
     * Richiesta portata nello stato indicato con le operazioni disponibili. Solo l'annullamento,
     * che appartiene alla #54, è forzato nel database a partire da una richiesta confermata: i
     * vincoli della V14 ammettono l'accordo anche su una richiesta annullata.
     */
    private UUID richiestaNelloStato(String stato) throws Exception {
        return switch (stato) {
            case "CREATA" -> invia(misuraId);
            case "PRESA_IN_CARICO" -> inviaEPrendiInCarico(misuraId);
            case "CONFERMATA", "ANNULLATA" -> {
                UUID id = richiestaInProgrammazione(erogatrice);
                conferma(id, json(inPresenza())).andExpect(status().isOk());
                if (stato.equals("ANNULLATA")) {
                    jdbc.update("update richieste set stato = 'ANNULLATA' where id = ?", id);
                }
                yield id;
            }
            case "RIFIUTATA" -> {
                UUID id = richiestaInProgrammazione(erogatrice);
                rifiuta(id, null).andExpect(status().isOk());
                yield id;
            }
            default -> throw new IllegalArgumentException(stato);
        };
    }

    private ResultActions conferma(UUID richiestaId, String corpo) throws Exception {
        return come(banco).post("/api/richieste/{id}/conferma", corpo, richiestaId);
    }

    private ResultActions rifiuta(UUID richiestaId, String corpo) throws Exception {
        return come(banco).post("/api/richieste/{id}/rifiuta", corpo, richiestaId);
    }

    private UUID account(NonBanco attore) {
        return switch (attore) {
            case OPT_EROGATRICE_ASSEGNATA -> erogatrice;
            case OPT_TITOLARE -> titolare;
            case OPT_ESTRANEA -> optEstranea;
            case OPERATORE_ESTRANEO -> operatoreEstraneo;
        };
    }

    private UUID account(Estraneo estraneo) {
        return switch (estraneo) {
            case OPT_ESTRANEA -> optEstranea;
            case OPT_CANDIDATA_CHE_HA_DECLINATO -> altraCandidata;
            case OPERATORE_ESTRANEO -> operatoreEstraneo;
        };
    }

    /** Stato, assegnazione e colonne dell'accordo della richiesta, letti da PostgreSQL. */
    private Map<String, Object> rigaDellaRichiesta(UUID richiestaId) {
        return jdbc.queryForMap("""
                select stato, erogatore_id, erogatore_ambito, accordo_modalita, accordo_data_ora, accordo_luogo,
                       accordo_nome_referente, accordo_cognome_referente, accordo_telefono_referente, accordo_link
                from richieste where id = ?
                """, richiestaId);
    }

    // ------------------------------------------------------------------ concorrenza

    @FunctionalInterface
    private interface Comando {
        ResultActions esegui(SessioneDiProva sessione, int indice) throws Exception;
    }

    /**
     * Esegue i comandi, ciascuno in una sessione Banco distinta, mentre il test tiene il lock
     * della richiesta. Li rilascia solo dopo averli visti tutti in attesa su PostgreSQL.
     */
    private List<MvcResult> inParalleloSulLock(UUID richiestaId, Comando comando) throws Exception {
        // sessioni distinte dello stesso account Banco, come più postazioni aperte insieme
        List<SessioneDiProva> sessioniBanco = new ArrayList<>();
        for (int i = 0; i < COMANDI_CONCORRENTI; i++) {
            sessioniBanco.add(accesso.accedi(banco));
        }

        ExecutorService esecutore = Executors.newFixedThreadPool(COMANDI_CONCORRENTI);
        List<Future<MvcResult>> futuri = new ArrayList<>();
        try {
            try (Connection blocco = dataSource.getConnection()) {
                blocco.setAutoCommit(false);
                int processoDelBlocco = bloccaRichiesta(blocco, richiestaId);
                try {
                    for (int i = 0; i < COMANDI_CONCORRENTI; i++) {
                        SessioneDiProva sessione = sessioniBanco.get(i);
                        int indice = i;
                        futuri.add(esecutore.submit(() -> comando.esegui(sessione, indice).andReturn()));
                    }
                    attendiComandiFermiSulLock(processoDelBlocco, COMANDI_CONCORRENTI);
                    assertEquals("IN_PROGRAMMAZIONE", dati.stato(richiestaId),
                            "nessun comando applicato prima del rilascio del lock");
                } finally {
                    blocco.rollback();
                }
            }
            List<MvcResult> esiti = new ArrayList<>();
            for (Future<MvcResult> futuro : futuri) {
                esiti.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return esiti;
        } finally {
            esecutore.shutdownNow();
        }
    }

    /**
     * Blocca la riga della richiesta nella transazione della connessione indicata.
     *
     * @return identificativo del processo PostgreSQL che tiene il lock
     */
    private static int bloccaRichiesta(Connection connessione, UUID richiesta) throws SQLException {
        try (PreparedStatement blocca = connessione.prepareStatement("select id from richieste where id = ? for update")) {
            blocca.setObject(1, richiesta);
            try (ResultSet riga = blocca.executeQuery()) {
                assertTrue(riga.next(), "la richiesta da bloccare esiste");
            }
        }
        try (Statement interrogazione = connessione.createStatement();
             ResultSet processo = interrogazione.executeQuery("select pg_backend_pid()")) {
            assertTrue(processo.next());
            return processo.getInt(1);
        }
    }

    /**
     * Attende che le transazioni in attesa dietro il lock siano almeno quelle indicate. La prima
     * aspetta direttamente il processo del blocco, le successive si accodano dietro la prima: per
     * questo la catena delle attese si segue in modo ricorsivo.
     */
    private void attendiComandiFermiSulLock(int processoDelBlocco, int attese) throws InterruptedException {
        long scadenza = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        int inAttesa;
        do {
            inAttesa = jdbc.queryForObject("""
                    with recursive in_attesa(pid) as (
                        select a.pid from pg_stat_activity a where ? = any(pg_blocking_pids(a.pid))
                        union
                        select a.pid from pg_stat_activity a join in_attesa b on b.pid = any(pg_blocking_pids(a.pid))
                    )
                    select count(*) from in_attesa
                    """, Integer.class, processoDelBlocco);
            if (inAttesa >= attese) {
                return;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < scadenza);
        fail("dopo 30 secondi solo " + inAttesa + " comandi su " + attese + " attendono il lock della richiesta");
    }

    /**
     * Verifica che un solo comando sia stato accettato e che tutti gli altri abbiano ricevuto un
     * {@code 409} nel formato comune; restituisce l'indice del comando accettato.
     */
    private int unicoAccettato(List<MvcResult> esiti, UUID richiestaId) throws Exception {
        int vincitore = -1;
        int accettati = 0;
        int inConflitto = 0;
        for (int i = 0; i < esiti.size(); i++) {
            int stato = esiti.get(i).getResponse().getStatus();
            if (stato == 200) {
                accettati++;
                vincitore = i;
            } else if (stato == 409) {
                assertConflitto(esiti.get(i), richiestaId);
                inConflitto++;
            }
        }
        assertEquals(1, accettati, "un solo comando deve essere accettato");
        assertEquals(esiti.size() - 1, inConflitto, "tutti gli altri devono ricevere 409 Conflict");
        return vincitore;
    }

    // ------------------------------------------------------------------ corpi JSON

    private static Map<String, Object> inPresenza() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("modalita", "IN_PRESENZA");
        corpo.put("dataOra", DATA_ORA);
        corpo.put("luogo", LUOGO);
        corpo.put("nomeReferente", NOME);
        corpo.put("cognomeReferente", COGNOME);
        corpo.put("telefonoReferente", TELEFONO);
        return corpo;
    }

    private static Map<String, Object> aDistanza() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("modalita", "A_DISTANZA");
        corpo.put("dataOra", DATA_ORA);
        corpo.put("link", LINK);
        return corpo;
    }

    private static Map<String, Object> senza(Map<String, Object> corpo, String campo) {
        Map<String, Object> copia = new LinkedHashMap<>(corpo);
        copia.remove(campo);
        return copia;
    }

    private static Map<String, Object> con(Map<String, Object> corpo, String campo, Object valore) {
        Map<String, Object> copia = new LinkedHashMap<>(corpo);
        copia.put(campo, valore);
        return copia;
    }

    /** Serializza un oggetto JSON piatto: testi tra virgolette, numeri e booleani così come sono. */
    private static String json(Map<String, ?> campi) {
        StringBuilder testo = new StringBuilder("{");
        for (var campo : campi.entrySet()) {
            if (testo.length() > 1) {
                testo.append(", ");
            }
            testo.append('"').append(campo.getKey()).append("\": ");
            Object valore = campo.getValue();
            if (valore instanceof String stringa) {
                testo.append('"').append(stringa.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            } else {
                testo.append(valore);
            }
        }
        return testo.append('}').toString();
    }

    // ------------------------------------------------------------------ verifiche ricorrenti

    private ResultActions assertConfermataInPresenza(ResultActions risposta, UUID erogatoreAtteso) throws Exception {
        risposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.richiesta.stato").value("CONFERMATA"))
                .andExpect(jsonPath("$.erogatoreAssegnatoId").value(erogatoreAtteso.toString()))
                .andExpect(jsonPath("$.accordo.luogo").value(LUOGO))
                .andExpect(jsonPath("$.accordo.nomeReferente").value(NOME))
                .andExpect(jsonPath("$.accordo.cognomeReferente").value(COGNOME))
                .andExpect(jsonPath("$.accordo.telefonoReferente").value(TELEFONO))
                .andExpect(jsonPath("$.accordo.link").doesNotExist());
        assertEquals(ISTANTE, istante(risposta, "$.accordo.dataOra"));
        return risposta;
    }

    private void assertConfermataADistanza(ResultActions risposta, UUID erogatoreAtteso) throws Exception {
        risposta.andExpect(status().isOk())
                .andExpect(jsonPath("$.richiesta.stato").value("CONFERMATA"))
                .andExpect(jsonPath("$.erogatoreAssegnatoId").value(erogatoreAtteso.toString()))
                .andExpect(jsonPath("$.accordo.link").value(LINK))
                .andExpect(jsonPath("$.accordo.luogo").doesNotExist())
                .andExpect(jsonPath("$.accordo.telefonoReferente").doesNotExist());
        assertEquals(ISTANTE, istante(risposta, "$.accordo.dataOra"));
    }

    /** Accordo in presenza registrato su PostgreSQL con i valori inviati dal Banco. */
    private void assertAccordoInPresenzaPersistito(UUID richiestaId, UUID erogatore) {
        Map<String, Object> riga = rigaDellaRichiesta(richiestaId);
        assertAll("accordo in presenza su PostgreSQL",
                () -> assertEquals("CONFERMATA", riga.get("stato")),
                () -> assertEquals(erogatore, riga.get("erogatore_id")),
                () -> assertEquals("IN_PRESENZA", riga.get("accordo_modalita")),
                () -> assertEquals(ISTANTE, ((Timestamp) riga.get("accordo_data_ora")).toInstant()),
                () -> assertEquals(LUOGO, riga.get("accordo_luogo")),
                () -> assertEquals(NOME, riga.get("accordo_nome_referente")),
                () -> assertEquals(COGNOME, riga.get("accordo_cognome_referente")),
                () -> assertEquals(TELEFONO, riga.get("accordo_telefono_referente")),
                () -> assertNull(riga.get("accordo_link")));
    }

    /** Accordo a distanza registrato su PostgreSQL con i valori inviati dal Banco. */
    private void assertAccordoADistanzaPersistito(UUID richiestaId, UUID erogatore) {
        Map<String, Object> riga = rigaDellaRichiesta(richiestaId);
        assertAll("accordo a distanza su PostgreSQL",
                () -> assertEquals("CONFERMATA", riga.get("stato")),
                () -> assertEquals(erogatore, riga.get("erogatore_id")),
                () -> assertEquals("CENTRO_PICUS_A_DISTANZA", riga.get("erogatore_ambito")),
                () -> assertEquals("A_DISTANZA", riga.get("accordo_modalita")),
                () -> assertEquals(ISTANTE, ((Timestamp) riga.get("accordo_data_ora")).toInstant()),
                () -> assertEquals(LINK, riga.get("accordo_link")),
                () -> assertNull(riga.get("accordo_luogo")),
                () -> assertNull(riga.get("accordo_telefono_referente")));
    }

    /** La pagina di dettaglio dell'interfaccia, servita all'account, contiene i testi indicati. */
    private void assertPaginaDelDettaglioMostra(UUID account, UUID richiestaId, String... testi) throws Exception {
        ResultActions pagina = come(account).get("/richieste/{id}", richiestaId)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        for (String testo : testi) {
            pagina.andExpect(content().string(containsString(testo)));
        }
    }

    /** 400 nel formato comune del modulo, con il campo da completare indicato in {@code campi}. */
    private static void assertDatiDaCompletare(ResultActions risposta, String campo) throws Exception {
        risposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codice").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.campi", hasKey(campo)))
                .andExpect(jsonPath("$.campi." + campo).isNotEmpty());
    }

    private void assertConflitto(ResultActions risposta, UUID richiestaId) throws Exception {
        assertConflitto(risposta.andReturn(), richiestaId);
    }

    /**
     * Verifica un conflitto nel formato comune del modulo Richieste: stato {@code 409}, corpo
     * JSON con i soli {@code codice}, {@code messaggio} e {@code campi}, nessun dato della
     * pratica (identificativi, partecipanti, descrizione, dettagli dell'accordo).
     */
    private void assertConflitto(MvcResult risposta, UUID richiestaId) throws Exception {
        for (ResultMatcher verifica : List.of(
                status().isConflict(),
                content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON),
                jsonPath("$.*", hasSize(3)),
                jsonPath("$.codice").value("CONFLICT"),
                jsonPath("$.messaggio").isNotEmpty(),
                jsonPath("$.campi").isEmpty())) {
            verifica.match(risposta);
        }

        String corpo = risposta.getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (String datoDellaPratica : List.of(richiestaId.toString(), titolare.toString(), erogatrice.toString(),
                DESCRIZIONE_RISERVATA, LUOGO, TELEFONO, LINK)) {
            assertFalse(corpo.contains(datoDellaPratica), "il 409 non deve contenere " + datoDellaPratica);
        }
    }

    /**
     * Nessuna scrittura parziale: la pratica resta In programmazione con l'erogatore assegnato,
     * le colonne dell'accordo sono vuote e nessuno dei partecipanti vede un accordo confermato.
     */
    private void assertInProgrammazioneSenzaAccordo(UUID richiestaId, UUID erogatore) throws Exception {
        Map<String, Object> salvato = rigaDellaRichiesta(richiestaId);
        assertEquals("IN_PROGRAMMAZIONE", salvato.get("stato"));
        assertEquals(erogatore, salvato.get("erogatore_id"));
        salvato.forEach((colonna, valore) -> {
            if (colonna.startsWith("accordo_")) {
                assertNull(valore, "colonna " + colonna + " scritta da un comando respinto");
            }
        });
        for (UUID account : List.of(banco, titolare, erogatore)) {
            come(account).get("/api/richieste/{id}", richiestaId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.richiesta.stato").value("IN_PROGRAMMAZIONE"))
                    .andExpect(jsonPath("$.erogatoreAssegnatoId").value(erogatore.toString()))
                    .andExpect(jsonPath("$.accordo").doesNotExist());
        }
    }

    /**
     * Rifiuto registrato su PostgreSQL e visibile alla OPT titolare: stato nelle API e nei suoi
     * elenchi, messaggio «Nessun erogatore disponibile» nella pagina di dettaglio.
     */
    private void assertRifiutataPerLaTitolare(UUID richiestaId) throws Exception {
        Map<String, Object> salvato = rigaDellaRichiesta(richiestaId);
        assertAll("rifiuto su PostgreSQL",
                () -> assertEquals("RIFIUTATA", salvato.get("stato")),
                () -> assertNull(salvato.get("accordo_modalita")));
        come(titolare).get("/api/richieste/{id}", richiestaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.richiesta.stato").value("RIFIUTATA"))
                .andExpect(jsonPath("$.accordo").doesNotExist());
        come(titolare).get("/api/richieste/mie")
                .andExpect(jsonPath("$[?(@.id == '" + richiestaId + "')].stato", hasItem("RIFIUTATA")));
        assertPaginaDelDettaglioMostra(titolare, richiestaId, NESSUN_EROGATORE);
    }

    private void assertPresaInCaricoPerLaTitolare(UUID richiestaId) throws Exception {
        assertEquals("PRESA_IN_CARICO", dati.stato(richiestaId), "stato su PostgreSQL");
        come(titolare).get("/api/richieste/{id}", richiestaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.richiesta.stato").value("PRESA_IN_CARICO"));
    }

    /** Legge un istante serializzato come testo ISO 8601 oppure come secondi dall'epoca. */
    private static Instant istante(ResultActions risposta, String percorso) throws Exception {
        Object valore = JsonPath.read(
                risposta.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), percorso);
        if (valore instanceof String testo) {
            return Instant.parse(testo);
        }
        BigDecimal secondi = new BigDecimal(valore.toString());
        return Instant.ofEpochSecond(secondi.longValue(),
                secondi.remainder(BigDecimal.ONE).movePointRight(9).longValue());
    }
}