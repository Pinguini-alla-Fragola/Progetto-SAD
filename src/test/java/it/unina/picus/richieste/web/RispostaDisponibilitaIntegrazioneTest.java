package it.unina.picus.richieste.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.unina.picus.DatiDiProva;
import it.unina.picus.TestIntegrazionePostgres;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Test di integrazione della risposta di disponibilità (#92, user story #51): endpoint HTTP
 * reali, catena di sicurezza attiva e PostgreSQL. I nomi dei test riportano il criterio di
 * completamento della #92 che verificano (C2, C3, C4). Il criterio C1 sulla migrazione è in
 * {@code MigrazioneContattiDisponibilitaTest}; C5 richiede login, notifiche e UI integrati.
 *
 * <p>Richiesta e contatto sono dati di prova inseriti nel database, perché l'apertura del
 * contatto appartiene alla #49. Gli utenti simulati hanno come nome l'UUID di un account di
 * prova e ogni POST porta un token CSRF valido: un {@code 403} dipende quindi soltanto dal
 * controllo dell'applicazione. L'utente simulato non è un {@code PrincipalePicus}, perciò
 * login e revoca della sessione non sono oggetto di questi test.</p>
 */
@TestIntegrazionePostgres
@DisplayName("#92 Risposta di disponibilità: integrazione su HTTP e PostgreSQL")
class RispostaDisponibilitaIntegrazioneTest {

    private static final String DESCRIZIONE_RISERVATA = "Descrizione riservata a titolare e Banco";

    /** Account diversi dal destinatario del contatto. */
    enum Estraneo {
        OPT_TITOLARE("OPT"),
        OPT_ESTRANEA("OPT"),
        OPERATORE_ESTRANEO("OPERATORE"),
        BANCO("BANCO");

        final String ruolo;

        Estraneo(String ruolo) {
            this.ruolo = ruolo;
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private DatiDiProva dati;
    private UUID banco;
    private UUID titolare;
    private UUID destinatario;
    private UUID optEstranea;
    private UUID operatoreEstraneo;
    private UUID richiestaId;
    private UUID contattoId;

    /** Given comune: richiesta Presa in carico con un contatto in attesa per la OPT destinataria. */
    @BeforeEach
    void preparaContattoInAttesa() {
        dati = new DatiDiProva(jdbc);
        banco = dati.creaAccount("BANCO", "Banco di prova");
        titolare = dati.creaOptConProfilo("OPT titolare");
        destinatario = dati.creaAccount("OPT", "OPT destinataria");
        optEstranea = dati.creaAccount("OPT", "OPT estranea");
        operatoreEstraneo = dati.creaAccount("OPERATORE", "Operatore estraneo");
        richiestaId = dati.creaRichiesta(titolare, "PRESA_IN_CARICO", DESCRIZIONE_RISERVATA);
        contattoId = dati.creaContatto(richiestaId, destinatario, 10);
    }

    // ------------------------------------------------------------------ C2: consultazione

    @Test
    @DisplayName("C2 - il destinatario consulta i soli dati necessari a valutare la disponibilità")
    void c2_destinatarioConsultaISoliDatiNecessari() throws Exception {
        mvc.perform(consulta(contattoId, destinatario, "OPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contatto.id").value(contattoId.toString()))
                .andExpect(jsonPath("$.contatto.richiestaId").value(richiestaId.toString()))
                .andExpect(jsonPath("$.contatto.erogatoreId").value(destinatario.toString()))
                .andExpect(jsonPath("$.contatto.ambito").value("STESSO_COMUNE"))
                .andExpect(jsonPath("$.contatto.esito").value("IN_ATTESA"))
                .andExpect(jsonPath("$.misuraId").isNumber())
                .andExpect(jsonPath("$.nomeMisura").value("Accoglienza e ascolto"))
                .andExpect(jsonPath("$.tipoDestinatario").value("PERSONA"))
                .andExpect(jsonPath("$.comuneCodice").value("napoli"))
                // nient'altro: né descrizione riservata né riferimento alla OPT titolare
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.contatto.*", hasSize(5)))
                .andExpect(content().string(not(containsString(DESCRIZIONE_RISERVATA))))
                .andExpect(content().string(not(containsString(titolare.toString()))));
    }

    @Test
    @DisplayName("C2 - anche un operatore del Banco consulta il contatto indirizzato al suo account personale")
    void c2_operatoreDestinatarioConsulta() throws Exception {
        UUID operatore = dati.creaAccount("OPERATORE", "Operatore destinatario");
        UUID contattoOperatore = dati.creaContatto(richiestaId, operatore, 5);

        mvc.perform(consulta(contattoOperatore, operatore, "OPERATORE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contatto.erogatoreId").value(operatore.toString()));
    }

    @Test
    @DisplayName("C2 - consultare il contatto non registra una risposta")
    void c2_laConsultazioneNonModificaIlContatto() throws Exception {
        mvc.perform(consulta(contattoId, destinatario, "OPT")).andExpect(status().isOk());

        assertContattoInvariato();
    }

    @ParameterizedTest(name = "C2 - {0} che legge il contatto di un altro account riceve 403 senza dati")
    @EnumSource(Estraneo.class)
    void c2_letturaDaAltroAccountNegata(Estraneo estraneo) throws Exception {
        mvc.perform(consulta(contattoId, account(estraneo), estraneo.ruolo))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));

        assertContattoInvariato();
    }

    @ParameterizedTest(name = "C2 - {0} che risponde al posto del destinatario riceve 403 e il contatto resta invariato")
    @EnumSource(Estraneo.class)
    void c2_rispostaDaAltroAccountNegata(Estraneo estraneo) throws Exception {
        mvc.perform(risposta(contattoId, account(estraneo), estraneo.ruolo, true))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
        mvc.perform(risposta(contattoId, account(estraneo), estraneo.ruolo, false))
                .andExpect(status().isForbidden());

        assertContattoInvariato();
        // il diniego non ha consumato il contatto: il destinatario può ancora rispondere
        mvc.perform(risposta(contattoId, destinatario, "OPT", true)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("C2 - senza autenticazione lettura e risposta ricevono 401 e il contatto resta invariato")
    void c2_senzaAutenticazione() throws Exception {
        mvc.perform(get("/api/contatti-disponibilita/{id}", contattoId))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/contatti-disponibilita/{id}/risposta", contattoId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disponibile\": true}"))
                .andExpect(status().isUnauthorized());

        assertContattoInvariato();
    }

    // ------------------------------------------------------------------ C3: persistenza e storico

    @Test
    @DisplayName("C3 - la conferma persiste, è visibile al Banco e la richiesta resta PRESA_IN_CARICO")
    void c3_confermaPersistitaEVisibileAlBanco() throws Exception {
        mvc.perform(risposta(contattoId, destinatario, "OPT", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(contattoId.toString()))
                .andExpect(jsonPath("$.esito").value("DISPONIBILE"));

        assertEquals("DISPONIBILE", dati.esitoContatto(contattoId));
        mvc.perform(storico(richiestaId, banco, "BANCO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(contattoId.toString()))
                .andExpect(jsonPath("$[0].erogatoreId").value(destinatario.toString()))
                .andExpect(jsonPath("$[0].esito").value("DISPONIBILE"));
        assertRichiestaAncoraPresaInCarico();
    }

    @Test
    @DisplayName("C3 - il rifiuto persiste, è visibile al Banco e la richiesta resta PRESA_IN_CARICO")
    void c3_rifiutoPersistitoEVisibileAlBanco() throws Exception {
        mvc.perform(risposta(contattoId, destinatario, "OPT", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esito").value("NON_DISPONIBILE"));

        assertEquals("NON_DISPONIBILE", dati.esitoContatto(contattoId));
        mvc.perform(storico(richiestaId, banco, "BANCO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].esito").value("NON_DISPONIBILE"));
        assertRichiestaAncoraPresaInCarico();
    }

    @Test
    @DisplayName("C3 - dopo un rifiuto un altro candidato può essere contattato e rispondere")
    void c3_dopoUnRifiutoSiContattaUnAltroCandidato() throws Exception {
        mvc.perform(risposta(contattoId, destinatario, "OPT", false)).andExpect(status().isOk());

        // L'apertura del nuovo contatto è della #49: qui viene inserita come dato di prova.
        UUID altroCandidato = dati.creaAccount("OPT", "OPT candidata alternativa");
        UUID secondoContatto = dati.creaContatto(richiestaId, altroCandidato, 0);

        mvc.perform(consulta(secondoContatto, altroCandidato, "OPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contatto.esito").value("IN_ATTESA"));
        mvc.perform(risposta(secondoContatto, altroCandidato, "OPT", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esito").value("DISPONIBILE"));

        // storico in ordine di invio: prima il rifiuto, poi la disponibilità
        mvc.perform(storico(richiestaId, banco, "BANCO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(contattoId.toString()))
                .andExpect(jsonPath("$[0].esito").value("NON_DISPONIBILE"))
                .andExpect(jsonPath("$[1].id").value(secondoContatto.toString()))
                .andExpect(jsonPath("$[1].esito").value("DISPONIBILE"));
        assertRichiestaAncoraPresaInCarico();
    }

    @Test
    @DisplayName("C3 - lo storico dei contatti è riservato al Banco")
    void c3_storicoNegatoAChiNonEIlBanco() throws Exception {
        mvc.perform(storico(richiestaId, destinatario, "OPT"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
        mvc.perform(storico(richiestaId, titolare, "OPT"))
                .andExpect(status().isForbidden());
        mvc.perform(storico(richiestaId, operatoreEstraneo, "OPERATORE"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ C4: doppio invio e stato

    @Test
    @DisplayName("C4 - una seconda risposta riceve 409 e resta valido il primo esito")
    void c4_doppioInvioInSequenza() throws Exception {
        mvc.perform(risposta(contattoId, destinatario, "OPT", false)).andExpect(status().isOk());

        mvc.perform(risposta(contattoId, destinatario, "OPT", true)).andExpect(status().isConflict());
        mvc.perform(risposta(contattoId, destinatario, "OPT", false)).andExpect(status().isConflict());

        assertEquals("NON_DISPONIBILE", dati.esitoContatto(contattoId));
        mvc.perform(storico(richiestaId, banco, "BANCO")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("C4 - risposte concorrenti: una sola riceve 200, le altre 409, un solo esito persistito")
    void c4_doppioInvioConcorrente() throws Exception {
        int invii = 8;
        ExecutorService esecutore = Executors.newFixedThreadPool(invii);
        CountDownLatch via = new CountDownLatch(1);
        List<Future<Integer>> esiti = new ArrayList<>();
        try {
            for (int i = 0; i < invii; i++) {
                boolean disponibile = i % 2 == 0;
                esiti.add(esecutore.submit(() -> {
                    via.await();
                    return mvc.perform(risposta(contattoId, destinatario, "OPT", disponibile))
                            .andReturn().getResponse().getStatus();
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
                    dati.esitoContatto(contattoId),
                    "l'esito persistito deve essere quello della risposta accettata");
        } finally {
            esecutore.shutdownNow();
        }
        mvc.perform(storico(richiestaId, banco, "BANCO")).andExpect(jsonPath("$", hasSize(1)));
        assertRichiestaAncoraPresaInCarico();
    }

    @ParameterizedTest(name = "C4 - con la richiesta in stato {0} la risposta riceve 409 e il contatto resta in attesa")
    @ValueSource(strings = {"CREATA", "IN_PROGRAMMAZIONE", "CONFERMATA", "RIFIUTATA", "ANNULLATA"})
    void c4_rispostaFuoriDalloStatoConsentito(String stato) throws Exception {
        dati.impostaStato(richiestaId, stato);

        mvc.perform(risposta(contattoId, destinatario, "OPT", true)).andExpect(status().isConflict());

        assertAll(
                () -> assertEquals("IN_ATTESA", dati.esitoContatto(contattoId)),
                () -> assertEquals(stato, dati.statoRichiesta(richiestaId)));
    }

    // ------------------------------------------------------------------ richieste HTTP

    private static MockHttpServletRequestBuilder consulta(UUID contatto, UUID account, String ruolo) {
        return get("/api/contatti-disponibilita/{id}", contatto)
                .with(user(account.toString()).roles(ruolo));
    }

    private static MockHttpServletRequestBuilder risposta(
            UUID contatto, UUID account, String ruolo, boolean disponibile) {
        return post("/api/contatti-disponibilita/{id}/risposta", contatto)
                .with(user(account.toString()).roles(ruolo))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"disponibile\": " + disponibile + "}");
    }

    private static MockHttpServletRequestBuilder storico(UUID richiesta, UUID account, String ruolo) {
        return get("/api/richieste/{id}/contatti", richiesta)
                .with(user(account.toString()).roles(ruolo));
    }

    // ------------------------------------------------------------------ verifiche ricorrenti

    private UUID account(Estraneo estraneo) {
        return switch (estraneo) {
            case OPT_TITOLARE -> titolare;
            case OPT_ESTRANEA -> optEstranea;
            case OPERATORE_ESTRANEO -> operatoreEstraneo;
            case BANCO -> banco;
        };
    }

    private void assertContattoInvariato() {
        assertAll(
                () -> assertEquals("IN_ATTESA", dati.esitoContatto(contattoId)),
                () -> assertRichiestaAncoraPresaInCarico());
    }

    private void assertRichiestaAncoraPresaInCarico() {
        assertAll(
                () -> assertEquals("PRESA_IN_CARICO", dati.statoRichiesta(richiestaId)),
                () -> assertNull(dati.erogatoreAssegnato(richiestaId), "l'assegnazione spetta al flusso della #49"));
    }
}