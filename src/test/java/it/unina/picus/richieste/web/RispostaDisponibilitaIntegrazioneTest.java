package it.unina.picus.richieste.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
 * Test di integrazione della user story #51 «Risposta alla richiesta di disponibilità»,
 * richiesti dalla #92: endpoint HTTP reali, login reale e PostgreSQL.
 *
 * <p>Ogni test riporta nel nome l'Acceptance Criteria della #51 che verifica (AC1..AC4).
 * I test marcati «#92» coprono i criteri di completamento della task che la user story non
 * esprime: doppio invio, stato non consentito, sessione e percorso della UI. La migrazione è
 * in {@code MigrazioneContattiDisponibilitaTest}.</p>
 *
 * <p>Lo scenario nasce dal flusso dell'applicazione: la OPT titolare invia la richiesta, il
 * Banco la prende in carico e interpella un candidato (#48, #49). Ogni attore accede con il
 * login reale (#25) e opera nella propria sessione. Nel database si inseriscono con
 * {@link DatiSintetici} soltanto account, profili e offerte, che l'applicazione non permette
 * ancora di creare.</p>
 *
 * <p>Visibilità e persistenza hanno verifiche distinte: l'esito mostrato al Banco si legge
 * dalle sue API, quello registrato si legge dalla riga su PostgreSQL. Ogni {@code 409} viene
 * verificato anche nel corpo: dalla #124 il modulo Richieste risponde con l'errore JSON comune
 * ({@code codice}, {@code messaggio}, {@code campi}) e senza dati della pratica.</p>
 */
@IntegrazionePostgresql
@DisplayName("#51 Risposta alla richiesta di disponibilità: integrazione su HTTP e PostgreSQL")
@Tag("issue-51")
class RispostaDisponibilitaIntegrazioneTest {

    private static final String DESCRIZIONE_RISERVATA = "Descrizione riservata a titolare e Banco";

    /**
     * Risposte inviate insieme nel test di concorrenza. Durante l'attesa del lock ognuna occupa
     * una connessione: con quella del test e quella del controllo restano sotto il pool
     * predefinito di Hikari (10).
     */
    private static final int INVII_CONCORRENTI = 6;

    /** Account diversi dal destinatario del contatto. */
    enum Estraneo {
        OPT_TITOLARE,
        OPT_ESTRANEA,
        OPT_CANDIDATA_NON_INTERPELLATA,
        OPERATORE_ESTRANEO,
        BANCO
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
    private UUID destinatario;
    private UUID altraCandidata;
    private UUID optEstranea;
    private UUID operatoreEstraneo;
    private UUID richiestaId;
    private UUID contattoId;
    private String nomeMisura;

    /**
     * Given comune: una richiesta inviata dalla OPT titolare, presa in carico dal Banco, con una
     * richiesta di disponibilità in attesa indirizzata alla OPT destinataria.
     */
    @BeforeEach
    void preparaRichiestaDiDisponibilita() throws Exception {
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        accesso = new AccessoDiProva(mvc, jdbc, encoder);
        sessioni.clear();

        misuraId = dati.misura("M01");
        nomeMisura = jdbc.queryForObject("select nome from catalogo_misure where id = ?", String.class, misuraId);
        banco = dati.banco("Banco di prova");
        titolare = dati.opt("OPT titolare", "napoli");
        destinatario = optErogatrice("OPT destinataria");
        altraCandidata = optErogatrice("OPT candidata non interpellata");
        optEstranea = dati.opt("OPT estranea", "napoli");
        operatoreEstraneo = dati.operatore("Operatore estraneo", "INTERNO_BANCO", "napoli", false);

        richiestaId = inviaEPrendiInCarico();
        contattoId = interpella(richiestaId, destinatario);
    }

    // ------------------------------------------------------------------ AC1

    @Test
    @DisplayName("AC1 - la OPT interpellata apre la notifica e consulta i soli dati necessari")
    void ac1_optApreLaNotificaEConsultaIDati() throws Exception {
        assertNotificaRicevuta(come(destinatario), contattoId, richiestaId);
        assertDettaglioEssenziale(come(destinatario), contattoId, richiestaId, destinatario);
    }

    @ParameterizedTest(name = "AC1 - l''operatore {0} interpellato apre la notifica e consulta i soli dati necessari")
    @ValueSource(strings = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    void ac1_operatoreApreLaNotificaEConsultaIDati(String tipologia) throws Exception {
        UUID operatore = dati.operatore("Operatore destinatario", tipologia, "napoli", false, misuraId);
        UUID altraRichiesta = inviaEPrendiInCarico();
        UUID contattoOperatore = interpella(altraRichiesta, operatore);

        assertNotificaRicevuta(come(operatore), contattoOperatore, altraRichiesta);
        assertDettaglioEssenziale(come(operatore), contattoOperatore, altraRichiesta, operatore);
    }

    @Test
    @DisplayName("AC1 - la notifica arriva al solo account interpellato")
    void ac1_notificaSoloAlDestinatario() throws Exception {
        for (UUID nonInterpellato : List.of(altraCandidata, optEstranea, titolare, operatoreEstraneo)) {
            come(nonInterpellato).get("/api/avvisi")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.contattoId == '" + contattoId + "')]", hasSize(0)));
        }
    }

    @Test
    @DisplayName("AC1 - aprire notifica e contatto non equivale a rispondere")
    void ac1_laConsultazioneNonRegistraUnaRisposta() throws Exception {
        come(destinatario).get("/api/avvisi").andExpect(status().isOk());
        come(destinatario).get("/api/contatti-disponibilita/{id}", contattoId).andExpect(status().isOk());

        assertContattoInAttesaERichiestaPresaInCarico();
    }

    // ------------------------------------------------------------------ AC2

    @Test
    @DisplayName("AC2 - la disponibilità è registrata e mostrata al Banco; la richiesta resta Presa in carico")
    void ac2_disponibilitaRegistrataEMostrataAlBanco() throws Exception {
        rispondi(come(destinatario), contattoId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(contattoId.toString()))
                .andExpect(jsonPath("$.esito").value("DISPONIBILE"));

        come(banco).get("/api/richieste/{id}/contatti", richiestaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(contattoId.toString()))
                .andExpect(jsonPath("$[0].erogatoreId").value(destinatario.toString()))
                .andExpect(jsonPath("$[0].esito").value("DISPONIBILE"));
        assertEsitoVisibileEPersistito(richiestaId, contattoId, "DISPONIBILE");
        assertRichiestaPresaInCaricoSenzaErogatore(richiestaId);
    }

    @Test
    @DisplayName("AC2 - la richiesta lascia Presa in carico solo con l'assegnazione del Banco (#49)")
    void ac2_soloLAssegnazioneFaAvanzareLaRichiesta() throws Exception {
        rispondi(come(destinatario), contattoId, true).andExpect(status().isOk());
        assertRichiestaPresaInCaricoSenzaErogatore(richiestaId);

        come(banco).post("/api/contatti-disponibilita/{id}/assegnazione", null, contattoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("IN_PROGRAMMAZIONE"));

        come(banco).get("/api/richieste/{id}", richiestaId)
                .andExpect(jsonPath("$.richiesta.stato").value("IN_PROGRAMMAZIONE"))
                .andExpect(jsonPath("$.erogatoreAssegnatoId").value(destinatario.toString()));

        assertAll("richiesta su PostgreSQL",
                () -> assertEquals("IN_PROGRAMMAZIONE", dati.stato(richiestaId)),
                () -> assertEquals(destinatario, dati.erogatoreAssegnato(richiestaId)));
    }

    @ParameterizedTest(name = "AC2 - risponde anche l''operatore {0} interpellato")
    @ValueSource(strings = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    void ac2_disponibilitaDellOperatore(String tipologia) throws Exception {
        UUID operatore = dati.operatore("Operatore destinatario", tipologia, "napoli", false, misuraId);
        UUID altraRichiesta = inviaEPrendiInCarico();
        UUID contattoOperatore = interpella(altraRichiesta, operatore);

        rispondi(come(operatore), contattoOperatore, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esito").value("DISPONIBILE"));

        come(banco).get("/api/richieste/{id}/contatti", altraRichiesta)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].erogatoreId").value(operatore.toString()));
        assertEsitoVisibileEPersistito(altraRichiesta, contattoOperatore, "DISPONIBILE");
        assertRichiestaPresaInCaricoSenzaErogatore(altraRichiesta);
    }

    // ------------------------------------------------------------------ AC3

    @Test
    @DisplayName("AC3 - il Banco vede il rifiuto e la richiesta resta Presa in carico")
    void ac3_rifiutoMostratoAlBanco() throws Exception {
        rispondi(come(destinatario), contattoId, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esito").value("NON_DISPONIBILE"));

        come(banco).get("/api/richieste/{id}/contatti", richiestaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].esito").value("NON_DISPONIBILE"));
        assertEsitoVisibileEPersistito(richiestaId, contattoId, "NON_DISPONIBILE");
        assertRichiestaPresaInCaricoSenzaErogatore(richiestaId);
        // la OPT richiedente continua a vedere la richiesta originaria in lavorazione
        come(titolare).get("/api/richieste/{id}", richiestaId)
                .andExpect(jsonPath("$.richiesta.stato").value("PRESA_IN_CARICO"));
    }

    @Test
    @DisplayName("AC3 - dopo il rifiuto il Banco interpella un altro candidato, che può rispondere")
    void ac3_dopoIlRifiutoIlBancoInterpellaUnAltroCandidato() throws Exception {
        rispondi(come(destinatario), contattoId, false).andExpect(status().isOk());

        UUID secondoContatto = interpella(richiestaId, altraCandidata);

        come(altraCandidata).get("/api/contatti-disponibilita/{id}", secondoContatto)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contatto.esito").value("IN_ATTESA"));
        rispondi(come(altraCandidata), secondoContatto, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esito").value("DISPONIBILE"));

        // storico in ordine di invio: prima il rifiuto, poi la disponibilità
        come(banco).get("/api/richieste/{id}/contatti", richiestaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(contattoId.toString()))
                .andExpect(jsonPath("$[0].esito").value("NON_DISPONIBILE"))
                .andExpect(jsonPath("$[1].id").value(secondoContatto.toString()))
                .andExpect(jsonPath("$[1].esito").value("DISPONIBILE"));
        assertEsitoVisibileEPersistito(richiestaId, contattoId, "NON_DISPONIBILE");
        assertEsitoVisibileEPersistito(richiestaId, secondoContatto, "DISPONIBILE");
        assertRichiestaPresaInCaricoSenzaErogatore(richiestaId);
    }

    // ------------------------------------------------------------------ AC4

    @ParameterizedTest(name = "AC4 - {0} che consulta un contatto non suo riceve 403 senza dati")
    @EnumSource(Estraneo.class)
    void ac4_consultazioneNegataAChiNonEDestinatario(Estraneo estraneo) throws Exception {
        come(account(estraneo)).get("/api/contatti-disponibilita/{id}", contattoId)
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));

        assertContattoInAttesaERichiestaPresaInCarico();
    }

    @ParameterizedTest(name = "AC4 - {0} che risponde a un contatto non suo riceve 403 e nulla cambia")
    @EnumSource(Estraneo.class)
    void ac4_rispostaNegataAChiNonEDestinatario(Estraneo estraneo) throws Exception {
        rispondi(come(account(estraneo)), contattoId, true)
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
        rispondi(come(account(estraneo)), contattoId, false)
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));

        assertContattoInAttesaERichiestaPresaInCarico();
        // il diniego non ha consumato il contatto: il destinatario può ancora rispondere
        rispondi(come(destinatario), contattoId, true).andExpect(status().isOk());
        assertEsitoVisibileEPersistito(richiestaId, contattoId, "DISPONIBILE");
    }

    @Test
    @DisplayName("AC4 - lo storico delle risposte è riservato al Banco")
    void ac4_storicoNegatoAChiNonEIlBanco() throws Exception {
        for (UUID nonBanco : List.of(destinatario, titolare, optEstranea, operatoreEstraneo)) {
            come(nonBanco).get("/api/richieste/{id}/contatti", richiestaId)
                    .andExpect(status().isForbidden())
                    .andExpect(content().string(""));
        }
    }

    // ------------------------------------------------------------------ #92: sessione

    @Test
    @DisplayName("#92 - senza accesso, consultazione e risposta ricevono 401 e nulla cambia")
    void sessione_senzaAccesso() throws Exception {
        SessioneDiProva anonima = SessioneDiProva.anonima(mvc);

        anonima.get("/api/contatti-disponibilita/{id}", contattoId).andExpect(status().isUnauthorized());
        rispondi(anonima, contattoId, true).andExpect(status().isUnauthorized());
        anonima.get("/api/avvisi").andExpect(status().isUnauthorized());

        assertContattoInAttesaERichiestaPresaInCarico();
    }

    @Test
    @DisplayName("#92 - dopo la revoca dell'account la sessione del destinatario non può più rispondere")
    void sessione_accountRevocato() throws Exception {
        SessioneDiProva sessioneDelDestinatario = come(destinatario);
        sessioneDelDestinatario.get("/api/contatti-disponibilita/{id}", contattoId).andExpect(status().isOk());

        dati.impostaStatoAccount(destinatario, "REVOCATO");

        rispondi(sessioneDelDestinatario, contattoId, true).andExpect(status().isUnauthorized());
        sessioneDelDestinatario.get("/api/contatti-disponibilita/{id}", contattoId)
                .andExpect(status().isUnauthorized());
        assertContattoInAttesaERichiestaPresaInCarico();
    }

    // ------------------------------------------------------------------ #92: doppio invio e stato

    @Test
    @DisplayName("#92 - una seconda risposta riceve 409 con l'errore JSON e resta valido il primo esito")
    void doppioInvioInSequenza() throws Exception {
        rispondi(come(destinatario), contattoId, false).andExpect(status().isOk());

        assertConflittoSenzaDatiDellaPratica(rispondi(come(destinatario), contattoId, true));
        assertConflittoSenzaDatiDellaPratica(rispondi(come(destinatario), contattoId, false));

        assertEsitoVisibileEPersistito(richiestaId, contattoId, "NON_DISPONIBILE");
        assertEquals(1, dati.contatti(richiestaId), "nessun contatto in più su PostgreSQL");
        come(banco).get("/api/richieste/{id}/contatti", richiestaId).andExpect(jsonPath("$", hasSize(1)));
    }

    /**
     * Il test tiene il lock della riga della richiesta, lo stesso che il servizio acquisisce
     * prima di leggere l'esito del contatto. Le risposte partono e si fermano su quel lock: il
     * test attende di osservarle tutte in attesa su PostgreSQL, quindi contemporaneamente in
     * corso nel punto in cui si decide l'esito, e solo allora rilascia il lock.
     */
    @Test
    @DisplayName("#92 - risposte concorrenti ferme sul lock della richiesta: una sola riceve 200, le altre 409, un solo esito persistito")
    void doppioInvioConcorrente() throws Exception {
        // una sessione per invio, come lo stesso account aperto su più dispositivi
        List<SessioneDiProva> sessioniDelDestinatario = new ArrayList<>();
        for (int i = 0; i < INVII_CONCORRENTI; i++) {
            sessioniDelDestinatario.add(accesso.accedi(destinatario));
        }

        ExecutorService esecutore = Executors.newFixedThreadPool(INVII_CONCORRENTI);
        List<Future<MvcResult>> esiti = new ArrayList<>();
        try {
            try (Connection blocco = dataSource.getConnection()) {
                blocco.setAutoCommit(false);
                int processoDelBlocco = bloccaRichiesta(blocco, richiestaId);
                try {
                    for (int i = 0; i < INVII_CONCORRENTI; i++) {
                        SessioneDiProva sessione = sessioniDelDestinatario.get(i);
                        boolean disponibile = i % 2 == 0;
                        esiti.add(esecutore.submit(() -> rispondi(sessione, contattoId, disponibile).andReturn()));
                    }
                    attendiRisposteFermeSulLock(processoDelBlocco, INVII_CONCORRENTI);
                    assertEquals("IN_ATTESA", dati.esitoContatto(contattoId),
                            "nessuna risposta registrata prima del rilascio del lock");
                } finally {
                    blocco.rollback();
                }
            }
            int accettate = 0;
            int inConflitto = 0;
            boolean disponibileAccettato = false;
            for (int i = 0; i < INVII_CONCORRENTI; i++) {
                MvcResult esito = esiti.get(i).get(30, TimeUnit.SECONDS);
                int stato = esito.getResponse().getStatus();
                if (stato == 200) {
                    accettate++;
                    disponibileAccettato = i % 2 == 0;
                } else if (stato == 409) {
                    assertConflittoSenzaDatiDellaPratica(esito);
                    inConflitto++;
                }
            }

            assertEquals(1, accettate, "una sola risposta deve essere accettata");
            assertEquals(INVII_CONCORRENTI - 1, inConflitto, "tutte le altre devono ricevere 409 Conflict");
            assertEsitoVisibileEPersistito(richiestaId, contattoId,
                    disponibileAccettato ? "DISPONIBILE" : "NON_DISPONIBILE");
        } finally {
            esecutore.shutdownNow();
        }
        assertEquals(1, dati.contatti(richiestaId), "nessun contatto in più su PostgreSQL");
        come(banco).get("/api/richieste/{id}/contatti", richiestaId).andExpect(jsonPath("$", hasSize(1)));
        assertRichiestaPresaInCaricoSenzaErogatore(richiestaId);
    }

    @Test
    @DisplayName("#92 - dopo l'assegnazione una nuova risposta riceve 409 e l'esito non cambia")
    void rispostaDopoLAssegnazione() throws Exception {
        rispondi(come(destinatario), contattoId, true).andExpect(status().isOk());
        come(banco).post("/api/contatti-disponibilita/{id}/assegnazione", null, contattoId)
                .andExpect(status().isOk());

        assertConflittoSenzaDatiDellaPratica(rispondi(come(destinatario), contattoId, false));

        assertEsitoVisibileEPersistito(richiestaId, contattoId, "DISPONIBILE");
    }

    @ParameterizedTest(name = "#92 - con la richiesta in stato {0} la risposta riceve 409 e il contatto resta in attesa")
    @ValueSource(strings = {"CREATA", "IN_PROGRAMMAZIONE", "CONFERMATA", "RIFIUTATA", "ANNULLATA"})
    void rispostaFuoriDalloStatoConsentito(String stato) throws Exception {
        // Un contatto in attesa su una richiesta non Presa in carico non è raggiungibile dalle
        // operazioni disponibili: lo stato viene forzato nel database per provare la difesa del server.
        forzaStato(richiestaId, stato);

        assertConflittoSenzaDatiDellaPratica(rispondi(come(destinatario), contattoId, true));

        assertAll("dati su PostgreSQL",
                () -> assertEquals("IN_ATTESA", dati.esitoContatto(contattoId)),
                () -> assertEquals(stato, dati.stato(richiestaId)));
    }

    // ------------------------------------------------------------------ #92: percorso della UI

    /**
     * Criterio 5 della #92, lato server: le pagine e le chiamate che la UI della #91 esegue,
     * nell'ordine del percorso, con login reale. Il riquadro dell'area di lavoro carica gli
     * avvisi e il dettaglio di ciascun contatto; «Rispondi» apre la pagina del contatto; il
     * Banco apre lo storico della richiesta. Resa grafica e uso da mobile si verificano a mano
     * sulla demo.
     */
    @Test
    @DisplayName("#92 - percorso della UI: dalla notifica nell'area di lavoro alla risposta, fino allo storico del Banco")
    void percorsoDellaUiDallaNotificaAlloStoricoDelBanco() throws Exception {
        SessioneDiProva opt = come(destinatario);
        opt.get("/dashboard")
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/opt/dashboard"));
        opt.get("/opt/dashboard")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"assegnazioni\"")));
        assertNotificaRicevuta(opt, contattoId, richiestaId);
        opt.get("/api/contatti-disponibilita/{id}", contattoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contatto.esito").value("IN_ATTESA"));

        opt.get("/richieste/contatti/{id}", contattoId)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("data-contatto-id=\"" + contattoId + "\"")))
                .andExpect(content().string(containsString("data-contatti-disponibili=\"true\"")));
        rispondi(opt, contattoId, false).andExpect(status().isOk());

        come(banco).get("/banco/richieste/{id}/contatti", richiestaId)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("data-richiesta-id=\"" + richiestaId + "\"")))
                .andExpect(content().string(containsString("data-contatti-disponibili=\"true\"")));
        come(banco).get("/api/richieste/{id}/contatti", richiestaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].esito").value("NON_DISPONIBILE"));
        assertEsitoVisibileEPersistito(richiestaId, contattoId, "NON_DISPONIBILE");
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
    private UUID optErogatrice(String nome) {
        UUID opt = dati.opt(nome, "napoli");
        dati.offerta(opt, misuraId);
        return opt;
    }

    /** La OPT titolare invia una richiesta per la misura di prova e il Banco la prende in carico. */
    private UUID inviaEPrendiInCarico() throws Exception {
        String invio = "{\"misuraId\": " + misuraId + ", \"tipoDestinatario\": \"PERSONA\","
                + " \"comuneCodice\": \"napoli\", \"description\": \"" + DESCRIZIONE_RISERVATA + "\"}";
        String creata = come(titolare).post("/api/richieste", invio)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(creata, "$.id"));

        come(banco).post("/api/richieste/{id}/presa-in-carico", null, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("PRESA_IN_CARICO"));
        return id;
    }

    /** Il Banco chiede la disponibilità all'erogatore, candidato nello stesso comune della richiesta. */
    private UUID interpella(UUID richiesta, UUID erogatore) throws Exception {
        String creato = come(banco)
                .post("/api/richieste/{id}/contatti",
                        "{\"erogatoreId\": \"" + erogatore + "\", \"ambito\": \"STESSO_COMUNE\"}", richiesta)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.esito").value("IN_ATTESA"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(creato, "$.id"));
    }

    private static ResultActions rispondi(SessioneDiProva sessione, UUID contatto, boolean disponibile)
            throws Exception {
        return sessione.post(
                "/api/contatti-disponibilita/{id}/risposta", "{\"disponibile\": " + disponibile + "}", contatto);
    }

    private UUID account(Estraneo estraneo) {
        return switch (estraneo) {
            case OPT_TITOLARE -> titolare;
            case OPT_ESTRANEA -> optEstranea;
            case OPT_CANDIDATA_NON_INTERPELLATA -> altraCandidata;
            case OPERATORE_ESTRANEO -> operatoreEstraneo;
            case BANCO -> banco;
        };
    }

    /**
     * Forza lo stato della richiesta, per provare la difesa del server su stati che il flusso non
     * permette di raggiungere. Per {@code CONFERMATA} completa anche erogatore e accordo, che i
     * vincoli della V14 rendono obbligatori: come erogatore usa il destinatario del contatto.
     */
    private void forzaStato(UUID richiesta, String stato) {
        if (!"CONFERMATA".equals(stato)) {
            jdbc.update("update richieste set stato = ? where id = ?", stato, richiesta);
            return;
        }
        jdbc.update("""
                update richieste r set stato = 'CONFERMATA',
                       erogatore_id = coalesce(r.erogatore_id, (select c.erogatore_id
                                                                from richieste_contatti_disponibilita c
                                                                where c.richiesta_id = r.id
                                                                order by c.inviato_il desc limit 1)),
                       erogatore_ambito = coalesce(r.erogatore_ambito, 'STESSO_COMUNE'),
                       accordo_modalita = 'IN_PRESENZA', accordo_data_ora = now() + interval '30 days',
                       accordo_luogo = 'Sede di esempio', accordo_nome_referente = 'Nome',
                       accordo_cognome_referente = 'Cognome', accordo_telefono_referente = '+39 000 0000000',
                       accordo_link = null
                where r.id = ?
                """, richiesta);
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
     * Attende che le transazioni in attesa dietro il lock siano almeno quelle indicate. La
     * prima aspetta direttamente il processo del blocco, le successive si accodano dietro la
     * prima: per questo la catena delle attese si segue in modo ricorsivo.
     */
    private void attendiRisposteFermeSulLock(int processoDelBlocco, int attese) throws InterruptedException {
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
        fail("dopo 30 secondi solo " + inAttesa + " risposte su " + attese + " attendono il lock della richiesta");
    }

    // ------------------------------------------------------------------ verifiche ricorrenti

    private void assertNotificaRicevuta(SessioneDiProva sessione, UUID contatto, UUID richiesta) throws Exception {
        String filtro = "$[?(@.contattoId == '" + contatto + "')]";
        sessione.get("/api/avvisi")
                .andExpect(status().isOk())
                .andExpect(jsonPath(filtro, hasSize(1)))
                .andExpect(jsonPath(filtro + ".tipo", hasItem("RICHIESTA_DISPONIBILITA")))
                .andExpect(jsonPath(filtro + ".richiestaId", hasItem(richiesta.toString())));
    }

    /**
     * Il destinatario riceve soltanto contatto, misura, tipo di destinatario e comune: né la
     * descrizione riservata né il riferimento alla OPT titolare.
     */
    private void assertDettaglioEssenziale(SessioneDiProva sessione, UUID contatto, UUID richiesta, UUID erogatore)
            throws Exception {
        sessione.get("/api/contatti-disponibilita/{id}", contatto)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contatto.id").value(contatto.toString()))
                .andExpect(jsonPath("$.contatto.richiestaId").value(richiesta.toString()))
                .andExpect(jsonPath("$.contatto.erogatoreId").value(erogatore.toString()))
                .andExpect(jsonPath("$.contatto.ambito").value("STESSO_COMUNE"))
                .andExpect(jsonPath("$.contatto.esito").value("IN_ATTESA"))
                .andExpect(jsonPath("$.misuraId").value(Math.toIntExact(misuraId)))
                .andExpect(jsonPath("$.nomeMisura").value(nomeMisura))
                .andExpect(jsonPath("$.tipoDestinatario").value("PERSONA"))
                .andExpect(jsonPath("$.comuneCodice").value("napoli"))
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.contatto.*", hasSize(5)))
                .andExpect(content().string(not(containsString(DESCRIZIONE_RISERVATA))))
                .andExpect(content().string(not(containsString(titolare.toString()))));
    }

    /** Esito del contatto come lo vede il Banco dall'applicazione. */
    private String esitoVistoDalBanco(UUID richiesta, UUID contatto) throws Exception {
        String corpo = come(banco).get("/api/richieste/{id}/contatti/{contatto}", richiesta, contatto)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.esito");
    }

    /** Due verifiche distinte: l'esito mostrato al Banco e quello registrato su PostgreSQL. */
    private void assertEsitoVisibileEPersistito(UUID richiesta, UUID contatto, String esito) throws Exception {
        assertEquals(esito, esitoVistoDalBanco(richiesta, contatto), "esito mostrato al Banco");
        assertEquals(esito, dati.esitoContatto(contatto), "esito registrato su PostgreSQL");
    }

    /**
     * Verifica un conflitto nel formato comune del modulo Richieste (#124): stato {@code 409},
     * corpo JSON con i soli {@code codice}, {@code messaggio} e {@code campi}, nessun dato
     * della pratica (identificativi, partecipanti, descrizione).
     */
    private void assertConflittoSenzaDatiDellaPratica(ResultActions risposta) throws Exception {
        assertConflittoSenzaDatiDellaPratica(risposta.andReturn());
    }

    /** Stesse verifiche su una risposta già ricevuta, come quelle delle richieste concorrenti. */
    private void assertConflittoSenzaDatiDellaPratica(MvcResult risposta) throws Exception {
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
        for (String datoDellaPratica : List.of(richiestaId.toString(), contattoId.toString(),
                titolare.toString(), destinatario.toString(), DESCRIZIONE_RISERVATA)) {
            assertFalse(corpo.contains(datoDellaPratica), "il 409 non deve contenere " + datoDellaPratica);
        }
    }

    private void assertRichiestaPresaInCaricoSenzaErogatore(UUID richiesta) throws Exception {
        come(banco).get("/api/richieste/{id}", richiesta)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.richiesta.stato").value("PRESA_IN_CARICO"))
                .andExpect(jsonPath("$.erogatoreAssegnatoId").doesNotExist());
        assertAll("richiesta su PostgreSQL",
                () -> assertEquals("PRESA_IN_CARICO", dati.stato(richiesta)),
                () -> assertNull(dati.erogatoreAssegnato(richiesta)));
    }

    private void assertContattoInAttesaERichiestaPresaInCarico() throws Exception {
        assertEsitoVisibileEPersistito(richiestaId, contattoId, "IN_ATTESA");
        assertRichiestaPresaInCaricoSenzaErogatore(richiestaId);
    }
}