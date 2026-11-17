package it.unina.picus.richieste.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.AccessoDiProva;
import it.unina.picus.DatiDiProva;
import it.unina.picus.SessioneDiProva;
import it.unina.picus.TestIntegrazionePostgres;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Test di integrazione della user story #52 «Conferma dell'accordo o rifiuto della richiesta»,
 * richiesti dalla #116: endpoint HTTP reali, login reale e PostgreSQL.
 *
 * Ogni test riporta nel nome l'Acceptance Criterion della #52 che verifica (AC1..AC9). I test
 * marcati «#116» coprono i criteri della task che la user story non esprime: compatibilità
 * della modalità, assenza di scritture parziali, richieste che hanno già un esito, accessi senza
 * sessione e comandi concorrenti.
 *
 * Lo scenario nasce dal flusso dell'applicazione: la OPT titolare invia la richiesta, il Banco
 * la prende in carico, interpella un candidato, ne riceve la disponibilità e lo assegna. Nel
 * database si inseriscono soltanto account, profili, offerte, misure e comuni, che
 * l'applicazione non permette ancora di creare. Ogni test usa una misura nuova, quindi i suoi
 * candidati sono solo quelli che crea.
 *
 * Il testo «Nessun erogatore disponibile» dell'AC5 è compito dell'interfaccia: qui si verifica
 * che la OPT titolare riceva lo stato {@code RIFIUTATA} da cui l'interfaccia lo ricava.
 */
@TestIntegrazionePostgres
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
    private PasswordEncoder encoder;

    private DatiDiProva dati;
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
        dati = new DatiDiProva(jdbc);
        accesso = new AccessoDiProva(mvc, jdbc, encoder);
        sessioni.clear();

        misuraId = dati.creaMisura();
        banco = dati.creaAccount("BANCO", "Banco di prova");
        titolare = dati.creaOptConProfilo("OPT titolare");
        erogatrice = dati.creaOptErogatrice("OPT erogatrice", misuraId);
        altraCandidata = dati.creaOptErogatrice("OPT candidata alternativa", misuraId);
        optEstranea = dati.creaOptConProfilo("OPT estranea");
        operatoreEstraneo = dati.creaOperatore("Operatore estraneo");
    }

    // ------------------------------------------------------------------ AC1, AC2

    @Test
    @DisplayName("AC1 - accordo in presenza: Confermata e dettagli disponibili a OPT titolare ed erogatore assegnato")
    void ac1_confermaInPresenza() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);

        ResultActions esito = conferma(richiestaId, json(inPresenza()));

        assertConfermataInPresenza(esito, erogatrice);
        assertConfermataInPresenza(come(banco).get("/api/richieste/{id}", richiestaId), erogatrice);
        assertConfermataInPresenza(come(titolare).get("/api/richieste/{id}", richiestaId), erogatrice);
        assertConfermataInPresenza(come(erogatrice).get("/api/richieste/{id}", richiestaId), erogatrice)
                // all'erogatore non arrivano i dati riservati alla OPT titolare
                .andExpect(jsonPath("$.titolareId").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist());
        come(titolare).get("/api/richieste/mie")
                .andExpect(jsonPath("$[?(@.id == '" + richiestaId + "')].stato", hasItem("CONFERMATA")));
    }

    @Test
    @DisplayName("AC1 - i dettagli confermati sono disponibili anche all'operatore del Banco assegnato")
    void ac1_confermaConOperatoreAssegnato() throws Exception {
        UUID operatore = dati.creaOperatoreErogatore("Operatore assegnato", misuraId);
        UUID richiestaId = richiestaInProgrammazione(operatore);

        conferma(richiestaId, json(inPresenza())).andExpect(status().isOk());

        assertConfermataInPresenza(come(operatore).get("/api/richieste/{id}", richiestaId), operatore);
        assertConfermataInPresenza(come(titolare).get("/api/richieste/{id}", richiestaId), operatore);
    }

    @Test
    @DisplayName("AC2 - accordo a distanza: Confermata e dettagli disponibili a OPT titolare ed erogatore assegnato")
    void ac2_confermaADistanza() throws Exception {
        ScenarioADistanza scenario = richiestaADistanzaInProgrammazione();

        ResultActions esito = conferma(scenario.richiestaId(), json(aDistanza()));

        assertConfermataADistanza(esito, scenario.centroPicus());
        assertConfermataADistanza(come(banco).get("/api/richieste/{id}", scenario.richiestaId()), scenario.centroPicus());
        assertConfermataADistanza(come(titolare).get("/api/richieste/{id}", scenario.richiestaId()), scenario.centroPicus());
        assertConfermataADistanza(
                come(scenario.centroPicus()).get("/api/richieste/{id}", scenario.richiestaId()), scenario.centroPicus());
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
    @DisplayName("#116 - se la misura non è più erogabile a distanza la conferma a distanza è respinta")
    void misuraNonPiuErogabileADistanza() throws Exception {
        ScenarioADistanza scenario = richiestaADistanzaInProgrammazione();
        // Il catalogo cambia dopo l'assegnazione: il Banco non può più confermare a distanza.
        dati.impostaErogabileADistanza(scenario.misuraId(), false);

        assertDatiDaCompletare(conferma(scenario.richiestaId(), json(aDistanza())), "modalita");

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
    @DisplayName("AC5 - rifiuto da In programmazione: Rifiutata e visibile alla OPT titolare, senza motivazione")
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
        UUID richiestaId = inviaEPrendiInCarico(dati.creaMisura());

        rifiuta(richiestaId, null).andExpect(status().isOk());

        assertRifiutataPerLaTitolare(richiestaId);
    }

    @Test
    @DisplayName("AC5 - finché restano candidati locali non interpellati il rifiuto definitivo è respinto")
    void ac5_rifiutoRespintoConCandidatiLocaliDaValutare() throws Exception {
        UUID richiestaId = inviaEPrendiInCarico(misuraId);
        assertConflitto(rifiuta(richiestaId, null));

        rispondi(erogatrice, interpella(richiestaId, erogatrice, "STESSO_COMUNE"), false);
        assertConflitto(rifiuta(richiestaId, null));

        assertStatoPerLaTitolare(richiestaId, "PRESA_IN_CARICO");
    }

    @Test
    @DisplayName("AC5 - il remoto compatibile va valutato prima del rifiuto definitivo")
    void ac5_rifiutoRespintoSeRestaIlRemoto() throws Exception {
        long misuraRemota = dati.creaMisuraADistanza();
        UUID locale = dati.creaOptErogatrice("OPT erogatrice locale", misuraRemota);
        UUID centroPicus = dati.creaOperatoreCentroPicus("Centro PICUS", misuraRemota, dati.creaComuneIsolato());
        UUID richiestaId = inviaEPrendiInCarico(misuraRemota);
        rispondi(locale, interpella(richiestaId, locale, "STESSO_COMUNE"), false);

        assertConflitto(rifiuta(richiestaId, null));
        assertStatoPerLaTitolare(richiestaId, "PRESA_IN_CARICO");

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

        assertConflitto(rifiuta(richiestaId, null));

        assertStatoPerLaTitolare(richiestaId, "PRESA_IN_CARICO");
    }

    @Test
    @DisplayName("AC5 - il rifiuto non richiede una motivazione: corpo assente o vuoto")
    void ac5_rifiutoSenzaMotivazione() throws Exception {
        UUID conCorpoVuoto = richiestaInProgrammazione(erogatrice);

        rifiuta(conCorpoVuoto, "{}").andExpect(status().isOk());

        assertRifiutataPerLaTitolare(conCorpoVuoto);
    }

    @Test
    @DisplayName("AC5 - il comando di rifiuto non accetta campi, nemmeno una motivazione libera")
    void ac5_rifiutoConCampiRespinto() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);

        rifiuta(richiestaId, json(Map.of("motivazione", "Nessuna soluzione")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codice").value("BAD_REQUEST"));

        assertStatoPerLaTitolare(richiestaId, "IN_PROGRAMMAZIONE");
    }

    // ------------------------------------------------------------------ AC6

    @Test
    @DisplayName("AC6 - il diniego di un candidato lascia Presa in carico e il Banco cerca un'alternativa")
    void ac6_diniegoDelCandidatoNonRifiutaLaRichiesta() throws Exception {
        UUID richiestaId = inviaEPrendiInCarico(misuraId);

        rispondi(erogatrice, interpella(richiestaId, erogatrice, "STESSO_COMUNE"), false);

        assertStatoPerLaTitolare(richiestaId, "PRESA_IN_CARICO");
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
        Map<String, Object> salvato = dati.accordoPersistito(richiestaId);

        assertConflitto(conferma(richiestaId, json(con(inPresenza(), "luogo", "Altra sede"))));
        assertConflitto(conferma(richiestaId, json(con(inPresenza(), "dataOra", "2030-04-01T09:00:00+02:00"))));
        assertConflitto(rifiuta(richiestaId, null));

        assertEquals(salvato, dati.accordoPersistito(richiestaId));
        assertConfermataInPresenza(come(titolare).get("/api/richieste/{id}", richiestaId), erogatrice);
    }

    @ParameterizedTest(name = "#116 - richiesta {0}: conferma e rifiuto ricevono 409 e i dati restano invariati")
    @ValueSource(strings = {"CREATA", "PRESA_IN_CARICO", "CONFERMATA", "RIFIUTATA", "ANNULLATA"})
    void comandiSuUnaRichiestaNonInLavorazione(String stato) throws Exception {
        UUID richiestaId = richiestaNelloStato(stato);
        Map<String, Object> prima = dati.accordoPersistito(richiestaId);

        assertConflitto(conferma(richiestaId, json(inPresenza())));
        if (!stato.equals("PRESA_IN_CARICO")) {
            // da Presa in carico il rifiuto è ammesso: i suoi casi sono negli AC5
            assertConflitto(rifiuta(richiestaId, null));
        }

        assertEquals(prima, dati.accordoPersistito(richiestaId));
    }

    // ------------------------------------------------------------------ AC8 e dinieghi (#116)

    @ParameterizedTest(name = "AC8 - {0} non può confermare l'accordo: 403 e stato e dati invariati")
    @EnumSource(NonBanco.class)
    void ac8_soloIlBancoConferma(NonBanco attore) throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);

        come(account(attore)).post("/api/richieste/{id}/conferma", json(inPresenza()), richiestaId)
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));

        assertInProgrammazioneSenzaAccordo(richiestaId, erogatrice);
    }

    @Test
    @DisplayName("AC8 - l'operatore del Banco assegnato non può confermare l'accordo")
    void ac8_operatoreAssegnatoNonConferma() throws Exception {
        UUID operatore = dati.creaOperatoreErogatore("Operatore assegnato", misuraId);
        UUID richiestaId = richiestaInProgrammazione(operatore);

        come(operatore).post("/api/richieste/{id}/conferma", json(inPresenza()), richiestaId)
                .andExpect(status().isForbidden());

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
    @DisplayName("#116 - dopo la revoca dell'account Banco la sua sessione non può più confermare")
    void accountBancoRevocato() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);
        UUID altroBanco = dati.creaAccount("BANCO", "Banco revocato");
        SessioneDiProva sessione = come(altroBanco);
        sessione.get("/api/richieste/{id}", richiestaId).andExpect(status().isOk());

        dati.revoca(altroBanco);

        sessione.post("/api/richieste/{id}/conferma", json(inPresenza()), richiestaId)
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

    @Test
    @DisplayName("#116 - conferme e rifiuti concorrenti: uno solo riceve 200, gli altri 409, esito coerente")
    void confermaERifiutoConcorrenti() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);
        int comandi = 8;

        List<MvcResult> esiti = inParallelo(comandi, i -> i % 2 == 0
                ? sessioneBanco(i).post("/api/richieste/{id}/conferma", json(inPresenza()), richiestaId)
                : sessioneBanco(i).post("/api/richieste/{id}/rifiuta", null, richiestaId));

        int vincitore = unicoAccettato(esiti);
        Map<String, Object> salvato = dati.accordoPersistito(richiestaId);
        if (vincitore % 2 == 0) {
            assertAll(
                    () -> assertEquals("CONFERMATA", salvato.get("stato")),
                    () -> assertEquals("IN_PRESENZA", salvato.get("accordo_modalita")),
                    () -> assertEquals(LUOGO, salvato.get("accordo_luogo")));
        } else {
            assertAll(
                    () -> assertEquals("RIFIUTATA", salvato.get("stato")),
                    () -> assertNull(salvato.get("accordo_modalita")));
        }
    }

    @Test
    @DisplayName("#116 - conferme concorrenti con dettagli diversi: resta solo l'accordo della conferma accettata")
    void confermeConcorrenti() throws Exception {
        UUID richiestaId = richiestaInProgrammazione(erogatrice);
        int comandi = 6;

        List<MvcResult> esiti = inParallelo(comandi, i -> sessioneBanco(i).post(
                "/api/richieste/{id}/conferma", json(con(inPresenza(), "luogo", "Sede " + i)), richiestaId));

        int vincitore = unicoAccettato(esiti);
        assertAll(
                () -> assertEquals("CONFERMATA", dati.accordoPersistito(richiestaId).get("stato")),
                () -> assertEquals("Sede " + vincitore, dati.accordoPersistito(richiestaId).get("accordo_luogo")));
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

    /** Sessioni distinte dello stesso account Banco, come più postazioni aperte insieme. */
    private final Map<Integer, SessioneDiProva> sessioniBanco = new HashMap<>();

    private SessioneDiProva sessioneBanco(int indice) {
        return sessioniBanco.get(indice);
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
        long misuraRemota = dati.creaMisuraADistanza();
        UUID centroPicus = dati.creaOperatoreCentroPicus("Centro PICUS", misuraRemota, dati.creaComuneIsolato());
        UUID richiestaId = inviaEPrendiInCarico(misuraRemota);
        assegna(richiestaId, centroPicus, "CENTRO_PICUS_A_DISTANZA");
        return new ScenarioADistanza(richiestaId, centroPicus, misuraRemota);
    }

    /**
     * Richiesta portata nello stato indicato con le operazioni disponibili. Solo l'annullamento,
     * che appartiene alla #54, è forzato nel database a partire da una richiesta confermata.
     */
    private UUID richiestaNelloStato(String stato) throws Exception {
        return switch (stato) {
            case "CREATA" -> invia(misuraId);
            case "PRESA_IN_CARICO" -> inviaEPrendiInCarico(misuraId);
            case "CONFERMATA", "ANNULLATA" -> {
                UUID id = richiestaInProgrammazione(erogatrice);
                conferma(id, json(inPresenza())).andExpect(status().isOk());
                if (stato.equals("ANNULLATA")) {
                    dati.impostaStato(id, "ANNULLATA");
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

    // ------------------------------------------------------------------ concorrenza

    @FunctionalInterface
    private interface Comando {
        ResultActions esegui(int indice) throws Exception;
    }

    /** Esegue i comandi tutti insieme, ciascuno in una sessione Banco distinta, e ne raccoglie i risultati. */
    private List<MvcResult> inParallelo(int numero, Comando comando) throws Exception {
        for (int i = 0; i < numero; i++) {
            sessioniBanco.put(i, accesso.accedi(banco));
        }
        ExecutorService esecutore = Executors.newFixedThreadPool(numero);
        CountDownLatch via = new CountDownLatch(1);
        List<Future<MvcResult>> futuri = new ArrayList<>();
        try {
            for (int i = 0; i < numero; i++) {
                int indice = i;
                futuri.add(esecutore.submit(() -> {
                    via.await();
                    return comando.esegui(indice).andReturn();
                }));
            }
            via.countDown();
            List<MvcResult> esiti = new ArrayList<>();
            for (Future<MvcResult> futuro : futuri) {
                esiti.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return esiti;
        } finally {
            esecutore.shutdownNow();
        }
    }

    /** Verifica che un solo comando sia stato accettato e gli altri respinti con 409; restituisce l'indice del primo. */
    private static int unicoAccettato(List<MvcResult> esiti) throws Exception {
        int vincitore = -1;
        int accettati = 0;
        int inConflitto = 0;
        for (int i = 0; i < esiti.size(); i++) {
            var risposta = esiti.get(i).getResponse();
            if (risposta.getStatus() == 200) {
                accettati++;
                vincitore = i;
            } else if (risposta.getStatus() == 409) {
                assertEquals("CONFLICT", JsonPath.read(risposta.getContentAsString(StandardCharsets.UTF_8), "$.codice"));
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

    /** 400 nel formato comune del modulo, con il campo da completare indicato in {@code campi}. */
    private static void assertDatiDaCompletare(ResultActions risposta, String campo) throws Exception {
        risposta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codice").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.campi", hasKey(campo)))
                .andExpect(jsonPath("$.campi." + campo).isNotEmpty());
    }

    private static void assertConflitto(ResultActions risposta) throws Exception {
        risposta.andExpect(status().isConflict())
                .andExpect(jsonPath("$.codice").value("CONFLICT"));
    }

    /**
     * Nessuna scrittura parziale: la pratica resta In programmazione con l'erogatore assegnato,
     * le colonne dell'accordo sono vuote e nessuno dei partecipanti vede un accordo confermato.
     */
    private void assertInProgrammazioneSenzaAccordo(UUID richiestaId, UUID erogatore) throws Exception {
        Map<String, Object> salvato = dati.accordoPersistito(richiestaId);
        assertEquals("IN_PROGRAMMAZIONE", salvato.get("stato"));
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

    private void assertRifiutataPerLaTitolare(UUID richiestaId) throws Exception {
        come(titolare).get("/api/richieste/{id}", richiestaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.richiesta.stato").value("RIFIUTATA"))
                .andExpect(jsonPath("$.accordo").doesNotExist());
        come(titolare).get("/api/richieste/mie")
                .andExpect(jsonPath("$[?(@.id == '" + richiestaId + "')].stato", hasItem("RIFIUTATA")));
        assertNull(dati.accordoPersistito(richiestaId).get("accordo_modalita"));
    }

    private void assertStatoPerLaTitolare(UUID richiestaId, String stato) throws Exception {
        come(titolare).get("/api/richieste/{id}", richiestaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.richiesta.stato").value(stato));
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
