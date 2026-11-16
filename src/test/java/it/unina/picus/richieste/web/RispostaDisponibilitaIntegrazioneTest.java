package it.unina.picus.richieste.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.AccessoDiProva;
import it.unina.picus.DatiDiProva;
import it.unina.picus.SessioneDiProva;
import it.unina.picus.TestIntegrazionePostgres;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Test di integrazione della user story #51 «Risposta alla richiesta di disponibilità»,
 * richiesti dalla #92: endpoint HTTP reali, login reale e PostgreSQL.
 *
 * Ogni test riporta nel nome l'Acceptance Criterion della #51 che verifica (AC1..AC4).
 * I test marcati «#92» coprono i criteri di completamento della task che la user story non
 * esprime: doppio invio, stato non consentito, sessione. La migrazione è in
 * {@code MigrazioneContattiDisponibilitaTest}.
 *
 * Lo scenario nasce dal flusso dell'applicazione: la OPT titolare invia la richiesta, il
 * Banco la prende in carico e interpella un candidato (#48, #49). Ogni attore accede con il
 * login reale (#25) e opera nella propria sessione. Si inseriscono nel database soltanto
 * account, profili, offerte e misure, che l'applicazione non permette ancora di creare.
 */
@TestIntegrazionePostgres
@DisplayName("#51 Risposta alla richiesta di disponibilità: integrazione su HTTP e PostgreSQL")
@Tag("issue-51")
class RispostaDisponibilitaIntegrazioneTest {

    private static final String DESCRIZIONE_RISERVATA = "Descrizione riservata a titolare e Banco";

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
    private PasswordEncoder encoder;

    private DatiDiProva dati;
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

    /**
     * Given comune: una richiesta inviata dalla OPT titolare, presa in carico dal Banco, con una
     * richiesta di disponibilità in attesa indirizzata alla OPT destinataria.
     */
    @BeforeEach
    void preparaRichiestaDiDisponibilita() throws Exception {
        dati = new DatiDiProva(jdbc);
        accesso = new AccessoDiProva(mvc, jdbc, encoder);
        sessioni.clear();

        misuraId = dati.creaMisura();
        banco = dati.creaAccount("BANCO", "Banco di prova");
        titolare = dati.creaOptConProfilo("OPT titolare");
        destinatario = dati.creaOptErogatrice("OPT destinataria", misuraId);
        altraCandidata = dati.creaOptErogatrice("OPT candidata non interpellata", misuraId);
        optEstranea = dati.creaOptConProfilo("OPT estranea");
        operatoreEstraneo = dati.creaOperatore("Operatore estraneo");

        richiestaId = inviaEPrendiInCarico();
        contattoId = interpella(richiestaId, destinatario);
    }

    // ------------------------------------------------------------------ AC1

    @Test
    @DisplayName("AC1 - la OPT interpellata apre la notifica e consulta i soli dati necessari")
    void ac1_optApreLaNotificaEConsultaIDati() throws Exception {
        come(destinatario).get("/api/avvisi")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.contattoId == '" + contattoId + "')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.contattoId == '" + contattoId + "')].tipo", hasItem("RICHIESTA_DISPONIBILITA")))
                .andExpect(jsonPath("$[?(@.contattoId == '" + contattoId + "')].richiestaId",
                        hasItem(richiestaId.toString())));

        come(destinatario).get("/api/contatti-disponibilita/{id}", contattoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contatto.id").value(contattoId.toString()))
                .andExpect(jsonPath("$.contatto.richiestaId").value(richiestaId.toString()))
                .andExpect(jsonPath("$.contatto.erogatoreId").value(destinatario.toString()))
                .andExpect(jsonPath("$.contatto.ambito").value("STESSO_COMUNE"))
                .andExpect(jsonPath("$.contatto.esito").value("IN_ATTESA"))
                .andExpect(jsonPath("$.misuraId").value(Math.toIntExact(misuraId)))
                .andExpect(jsonPath("$.nomeMisura").value(DatiDiProva.NOME_MISURA_DI_PROVA))
                .andExpect(jsonPath("$.tipoDestinatario").value("PERSONA"))
                .andExpect(jsonPath("$.comuneCodice").value("napoli"))
                // nient'altro: né descrizione riservata né riferimento alla OPT titolare
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.contatto.*", hasSize(5)))
                .andExpect(content().string(not(containsString(DESCRIZIONE_RISERVATA))))
                .andExpect(content().string(not(containsString(titolare.toString()))));
    }

    @Test
    @DisplayName("AC1 - l'operatore del Banco interpellato apre la notifica dal proprio account personale")
    void ac1_operatoreApreLaNotificaEConsultaIDati() throws Exception {
        UUID operatore = dati.creaOperatoreErogatore("Operatore destinatario", misuraId);
        UUID altraRichiesta = inviaEPrendiInCarico();
        UUID contattoOperatore = interpella(altraRichiesta, operatore);

        come(operatore).get("/api/avvisi")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.contattoId == '" + contattoOperatore + "')]", hasSize(1)));
        come(operatore).get("/api/contatti-disponibilita/{id}", contattoOperatore)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contatto.erogatoreId").value(operatore.toString()))
                .andExpect(jsonPath("$.misuraId").value(Math.toIntExact(misuraId)))
                .andExpect(jsonPath("$.comuneCodice").value("napoli"));
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
        assertEquals("DISPONIBILE", esitoVistoDalBanco(contattoId));
        assertRichiestaPresaInCaricoSenzaErogatore();
    }

    @Test
    @DisplayName("AC2 - la richiesta lascia Presa in carico solo con l'assegnazione del Banco (#49)")
    void ac2_soloLAssegnazioneFaAvanzareLaRichiesta() throws Exception {
        rispondi(come(destinatario), contattoId, true).andExpect(status().isOk());
        assertRichiestaPresaInCaricoSenzaErogatore();

        come(banco).post("/api/contatti-disponibilita/{id}/assegnazione", null, contattoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("IN_PROGRAMMAZIONE"));

        come(banco).get("/api/richieste/{id}", richiestaId)
                .andExpect(jsonPath("$.richiesta.stato").value("IN_PROGRAMMAZIONE"))
                .andExpect(jsonPath("$.erogatoreAssegnatoId").value(destinatario.toString()));
    }

    @Test
    @DisplayName("AC2 - risponde anche l'operatore del Banco interpellato")
    void ac2_disponibilitaDellOperatore() throws Exception {
        UUID operatore = dati.creaOperatoreErogatore("Operatore destinatario", misuraId);
        UUID altraRichiesta = inviaEPrendiInCarico();
        UUID contattoOperatore = interpella(altraRichiesta, operatore);

        rispondi(come(operatore), contattoOperatore, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esito").value("DISPONIBILE"));

        come(banco).get("/api/richieste/{id}/contatti", altraRichiesta)
                .andExpect(jsonPath("$[0].esito").value("DISPONIBILE"));
        come(banco).get("/api/richieste/{id}", altraRichiesta)
                .andExpect(jsonPath("$.richiesta.stato").value("PRESA_IN_CARICO"));
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
        assertRichiestaPresaInCaricoSenzaErogatore();
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
        assertRichiestaPresaInCaricoSenzaErogatore();
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
                .andExpect(status().isForbidden());

        assertContattoInAttesaERichiestaPresaInCarico();
        // il diniego non ha consumato il contatto: il destinatario può ancora rispondere
        rispondi(come(destinatario), contattoId, true).andExpect(status().isOk());
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

        dati.revoca(destinatario);

        rispondi(sessioneDelDestinatario, contattoId, true).andExpect(status().isUnauthorized());
        sessioneDelDestinatario.get("/api/contatti-disponibilita/{id}", contattoId)
                .andExpect(status().isUnauthorized());
        assertContattoInAttesaERichiestaPresaInCarico();
    }

    // ------------------------------------------------------------------ #92: doppio invio e stato

    @Test
    @DisplayName("#92 - una seconda risposta riceve 409 e resta valido il primo esito")
    void doppioInvioInSequenza() throws Exception {
        rispondi(come(destinatario), contattoId, false).andExpect(status().isOk());

        rispondi(come(destinatario), contattoId, true).andExpect(status().isConflict());
        rispondi(come(destinatario), contattoId, false).andExpect(status().isConflict());

        assertEquals("NON_DISPONIBILE", esitoVistoDalBanco(contattoId));
        come(banco).get("/api/richieste/{id}/contatti", richiestaId).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("#92 - risposte concorrenti: una sola riceve 200, le altre 409, un solo esito persistito")
    void doppioInvioConcorrente() throws Exception {
        int invii = 8;
        // una sessione per invio, come lo stesso account aperto su più dispositivi
        List<SessioneDiProva> sessioniDelDestinatario = new ArrayList<>();
        for (int i = 0; i < invii; i++) {
            sessioniDelDestinatario.add(accesso.accedi(destinatario));
        }

        ExecutorService esecutore = Executors.newFixedThreadPool(invii);
        CountDownLatch via = new CountDownLatch(1);
        List<Future<Integer>> esiti = new ArrayList<>();
        try {
            for (int i = 0; i < invii; i++) {
                SessioneDiProva sessione = sessioniDelDestinatario.get(i);
                boolean disponibile = i % 2 == 0;
                esiti.add(esecutore.submit(() -> {
                    via.await();
                    return rispondi(sessione, contattoId, disponibile).andReturn().getResponse().getStatus();
                }));
            }
            via.countDown();

            int accettate = 0;
            int inConflitto = 0;
            boolean disponibileAccettato = false;
            for (int i = 0; i < invii; i++) {
                int stato = esiti.get(i).get(30, TimeUnit.SECONDS);
                if (stato == 200) {
                    accettate++;
                    disponibileAccettato = i % 2 == 0;
                } else if (stato == 409) {
                    inConflitto++;
                }
            }

            assertEquals(1, accettate, "una sola risposta deve essere accettata");
            assertEquals(invii - 1, inConflitto, "tutte le altre devono ricevere 409 Conflict");
            assertEquals(
                    disponibileAccettato ? "DISPONIBILE" : "NON_DISPONIBILE",
                    esitoVistoDalBanco(contattoId),
                    "l'esito persistito deve essere quello della risposta accettata");
        } finally {
            esecutore.shutdownNow();
        }
        come(banco).get("/api/richieste/{id}/contatti", richiestaId).andExpect(jsonPath("$", hasSize(1)));
        assertRichiestaPresaInCaricoSenzaErogatore();
    }

    @Test
    @DisplayName("#92 - dopo l'assegnazione una nuova risposta riceve 409 e l'esito non cambia")
    void rispostaDopoLAssegnazione() throws Exception {
        rispondi(come(destinatario), contattoId, true).andExpect(status().isOk());
        come(banco).post("/api/contatti-disponibilita/{id}/assegnazione", null, contattoId)
                .andExpect(status().isOk());

        rispondi(come(destinatario), contattoId, false).andExpect(status().isConflict());

        assertEquals("DISPONIBILE", esitoVistoDalBanco(contattoId));
    }

    @ParameterizedTest(name = "#92 - con la richiesta in stato {0} la risposta riceve 409 e il contatto resta in attesa")
    @ValueSource(strings = {"CREATA", "IN_PROGRAMMAZIONE", "CONFERMATA", "RIFIUTATA", "ANNULLATA"})
    void rispostaFuoriDalloStatoConsentito(String stato) throws Exception {
        // Un contatto in attesa su una richiesta non Presa in carico non è raggiungibile dalle
        // operazioni disponibili: lo stato viene forzato nel database per provare la difesa del server.
        dati.impostaStato(richiestaId, stato);

        rispondi(come(destinatario), contattoId, true).andExpect(status().isConflict());

        assertAll(
                () -> assertEquals("IN_ATTESA", dati.esitoContatto(contattoId)),
                () -> assertEquals(stato, dati.statoRichiesta(richiestaId)));
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

    // ------------------------------------------------------------------ verifiche ricorrenti

    /** Esito del contatto come lo vede il Banco dall'applicazione. */
    private String esitoVistoDalBanco(UUID contatto) throws Exception {
        String corpo = come(banco).get("/api/richieste/{id}/contatti/{contatto}", richiestaId, contatto)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.esito");
    }

    private void assertRichiestaPresaInCaricoSenzaErogatore() throws Exception {
        come(banco).get("/api/richieste/{id}", richiestaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.richiesta.stato").value("PRESA_IN_CARICO"))
                .andExpect(jsonPath("$.erogatoreAssegnatoId").doesNotExist());
    }

    private void assertContattoInAttesaERichiestaPresaInCarico() throws Exception {
        assertEquals("IN_ATTESA", esitoVistoDalBanco(contattoId));
        assertRichiestaPresaInCaricoSenzaErogatore();
    }
}