package it.unina.picus.utenze.interno.applicazione;

import static it.unina.picus.utenze.web.OperatoriDiProva.CF_A;
import static it.unina.picus.utenze.web.OperatoriDiProva.INTERNO;
import static it.unina.picus.utenze.web.OperatoriDiProva.MESSAGGIO_NUOVO_LINK;
import static it.unina.picus.utenze.web.OperatoriDiProva.PASSWORD;
import static it.unina.picus.utenze.web.OperatoriDiProva.attendiReinviiConclusi;
import static it.unina.picus.utenze.web.OperatoriDiProva.impronta;
import static it.unina.picus.utenze.web.OperatoriDiProva.ultimoLink;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.DatiInvioAttivazione;
import it.unina.picus.utenze.web.OperatoriDiProva;
import it.unina.picus.utenze.web.OperatoriDiProva.OperatoreDiProva;

/**
 * Ripetizione della conclusione del reinvio del link di attivazione di un Operatore (task #154, storia #27, criterio
 * 7), come concordato nella review della #213, su PostgreSQL 17 in Docker.
 *
 * <p>Dopo l'invio dell'email il reinvio registra l'esito ({@link ConclusioneReinvio}): con un errore transitorio
 * dell'accesso ai dati si ripete solo la registrazione, senza rispedire l'email, fino a 3 tentativi; con un errore
 * permanente il tentativo è uno solo. Vale sia per il nuovo link chiesto con l'email, elaborato in secondo piano, sia
 * per il reinvio del Banco. Gli errori sono simulati da uno spy di {@link ConclusioneReinvio}, che per i tentativi
 * non simulati esegue il codice reale; il link inviato si legge dallo spy di {@link InvioEmail}.</p>
 *
 * <p>Lo spy in più rende diversa la configurazione: questa classe ha un contesto Spring, e un container, propri.
 * Prima di ogni verifica sul lavoro in secondo piano si attende che tutte le richieste accodate siano concluse.</p>
 */
@IntegrazionePostgresql
@DisplayName("Ripetizione della conclusione del reinvio del link dell'Operatore su PostgreSQL (#154, regressioni #213)")
class RiprovaConclusioneReinvioTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReinvioLinkSuRichiesta reinvio;

    @MockitoSpyBean
    private InvioEmail posta;

    @MockitoSpyBean
    private ConclusioneReinvio conclusione;

    private OperatoriDiProva operatori;
    private UUID banco;
    private long m01;

    @BeforeEach
    void prepara() {
        attendiReinviiConclusi(reinvio);
        clearInvocations(posta, conclusione);
        DatiSintetici dati = new DatiSintetici(jdbc);
        dati.svuota();
        operatori = new OperatoriDiProva(mvc, jdbc);
        m01 = dati.misura("M01");
        banco = dati.banco("Banco di coordinamento");
    }

    @Nested
    @DisplayName("Nuovo link chiesto con la propria email, elaborato in secondo piano")
    class ReinvioSuRichiesta {

        @Test
        @DisplayName("Un errore transitorio: la conclusione si ripete una volta, una sola email, vale solo il nuovo link")
        void erroreTransitorioUnaVolta() throws Exception {
            OperatoreDiProva operatore = creaOperatore();
            doThrow(transitorio()).doCallRealMethod()
                    .when(conclusione).concludi(eq(operatore.id()), any(), anyBoolean());

            richiediNuovoLink(operatore);

            String nuovo = ultimoLink(posta, operatore.email(), 2);
            verify(posta, times(2)).inviaLinkAttivazione(eq(operatore.email()), anyString(), anyString());
            assertConclusioniDelLink(operatore, nuovo, 2);
            assertSoloIlNuovoLinkValido(operatore, nuovo);
        }

        @Test
        @DisplayName("Errore permanente: un solo tentativo, una sola email, restano validi il link precedente e il nuovo")
        void errorePermanente() throws Exception {
            OperatoreDiProva operatore = creaOperatore();
            doThrow(permanente()).when(conclusione).concludi(eq(operatore.id()), any(), anyBoolean());

            richiediNuovoLink(operatore);

            String nuovo = ultimoLink(posta, operatore.email(), 2);
            verify(posta, times(2)).inviaLinkAttivazione(eq(operatore.email()), anyString(), anyString());
            assertConclusioniDelLink(operatore, nuovo, 1);
            assertEntrambiILinkValidi(operatore, nuovo);
        }

        @Test
        @DisplayName("Errori transitori continui: si ferma a 3 tentativi, una sola email, restano validi entrambi i link")
        void erroriTransitoriContinui() throws Exception {
            OperatoreDiProva operatore = creaOperatore();
            doThrow(transitorio()).when(conclusione).concludi(eq(operatore.id()), any(), anyBoolean());

            richiediNuovoLink(operatore);

            String nuovo = ultimoLink(posta, operatore.email(), 2);
            verify(posta, times(2)).inviaLinkAttivazione(eq(operatore.email()), anyString(), anyString());
            assertConclusioniDelLink(operatore, nuovo, 3);
            assertEntrambiILinkValidi(operatore, nuovo);
        }

        /** Chiede il nuovo link e attende che il lavoro in secondo piano, tentativi compresi, sia concluso. */
        private void richiediNuovoLink(OperatoreDiProva operatore) throws Exception {
            operatori.richiediNuovoLink(operatore.email())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.messaggio").value(MESSAGGIO_NUOVO_LINK));
            attendiReinviiConclusi(reinvio);
        }
    }

    @Nested
    @DisplayName("Reinvio del Banco all'Operatore")
    class ReinvioDelBanco {

        @Test
        @DisplayName("Un errore transitorio: 200, la conclusione si ripete una volta, una sola email, vale solo il nuovo link")
        void erroreTransitorioUnaVolta() throws Exception {
            OperatoreDiProva operatore = creaOperatore();
            doThrow(transitorio()).doCallRealMethod()
                    .when(conclusione).concludi(eq(operatore.id()), any(), anyBoolean());

            operatori.reinvioDelBanco(banco, "BANCO", operatore.id())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value(operatore.id().toString()))
                    .andExpect(jsonPath("$.erroreInvio").value(false));

            String nuovo = ultimoLink(posta, operatore.email(), 2);
            verify(posta, times(2)).inviaLinkAttivazione(eq(operatore.email()), anyString(), anyString());
            assertConclusioniDelLink(operatore, nuovo, 2);
            assertSoloIlNuovoLinkValido(operatore, nuovo);
        }

        @Test
        @DisplayName("Errore permanente: un solo tentativo e l'errore arriva al chiamante; una sola email, entrambi i link validi")
        void errorePermanente() throws Exception {
            OperatoreDiProva operatore = creaOperatore();
            doThrow(permanente()).when(conclusione).concludi(eq(operatore.id()), any(), anyBoolean());

            assertThatThrownBy(() -> operatori.reinvioDelBanco(banco, "BANCO", operatore.id()))
                    .hasRootCauseExactlyInstanceOf(DataIntegrityViolationException.class);

            String nuovo = ultimoLink(posta, operatore.email(), 2);
            verify(posta, times(2)).inviaLinkAttivazione(eq(operatore.email()), anyString(), anyString());
            assertConclusioniDelLink(operatore, nuovo, 1);
            assertEntrambiILinkValidi(operatore, nuovo);
        }

        @Test
        @DisplayName("Errori transitori continui: si ferma a 3 tentativi e l'errore arriva al chiamante; una sola email")
        void erroriTransitoriContinui() throws Exception {
            OperatoreDiProva operatore = creaOperatore();
            doThrow(transitorio()).when(conclusione).concludi(eq(operatore.id()), any(), anyBoolean());

            assertThatThrownBy(() -> operatori.reinvioDelBanco(banco, "BANCO", operatore.id()))
                    .hasRootCauseExactlyInstanceOf(TransientDataAccessResourceException.class);

            String nuovo = ultimoLink(posta, operatore.email(), 2);
            verify(posta, times(2)).inviaLinkAttivazione(eq(operatore.email()), anyString(), anyString());
            assertConclusioniDelLink(operatore, nuovo, 3);
            assertEntrambiILinkValidi(operatore, nuovo);
        }
    }

    /** Operatore in attesa creato via HTTP; la creazione invia la prima email e non passa dalla conclusione. */
    private OperatoreDiProva creaOperatore() throws Exception {
        String email = "riprova-" + UUID.randomUUID().toString().substring(0, 8) + "@picus.test";
        OperatoreDiProva operatore = operatori.creaValido(banco, posta, email, CF_A, INTERNO, m01);
        verify(conclusione, times(0)).concludi(any(), any(), anyBoolean());
        return operatore;
    }

    /** La conclusione è stata chiamata il numero di volte atteso, sempre per l'invio riuscito dello stesso link. */
    private void assertConclusioniDelLink(OperatoreDiProva operatore, String nuovo, int tentativi) {
        ArgumentCaptor<DatiInvioAttivazione> dati = ArgumentCaptor.forClass(DatiInvioAttivazione.class);
        verify(conclusione, times(tentativi)).concludi(eq(operatore.id()), dati.capture(), eq(true));
        assertThat(dati.getAllValues()).extracting(DatiInvioAttivazione::tokenInChiaro).containsOnly(nuovo);
        verify(conclusione, times(tentativi)).concludi(any(), any(), anyBoolean());
    }

    /** Il nuovo link ha sostituito il precedente e attiva l'account. */
    private void assertSoloIlNuovoLinkValido(OperatoreDiProva operatore, String nuovo) throws Exception {
        assertThat(nuovo).isNotEqualTo(operatore.link());
        assertThat(operatori.impronteAttive(operatore.id())).containsExactly(impronta(nuovo));
        operatori.attiva(operatore.link(), PASSWORD).andExpect(status().isBadRequest());
        operatori.attiva(nuovo, PASSWORD).andExpect(status().isOk());
        assertThat(operatori.stato(operatore.id())).isEqualTo("ATTIVO");
    }

    /** La conclusione non è avvenuta: restano validi sia il link già consegnato sia quello nuovo. */
    private void assertEntrambiILinkValidi(OperatoreDiProva operatore, String nuovo) {
        assertThat(nuovo).isNotEqualTo(operatore.link());
        assertThat(operatori.impronteAttive(operatore.id()))
                .containsExactly(impronta(operatore.link()), impronta(nuovo));
        assertThat(operatori.stato(operatore.id())).isEqualTo("IN_ATTESA_DI_ATTIVAZIONE");
    }

    private static TransientDataAccessResourceException transitorio() {
        return new TransientDataAccessResourceException("errore transitorio simulato");
    }

    private static DataIntegrityViolationException permanente() {
        return new DataIntegrityViolationException("errore permanente simulato");
    }
}
