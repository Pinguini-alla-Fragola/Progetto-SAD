package it.unina.picus.utenze.web;

import static it.unina.picus.utenze.web.OperatoriDiProva.CF_A;
import static it.unina.picus.utenze.web.OperatoriDiProva.CF_B;
import static it.unina.picus.utenze.web.OperatoriDiProva.ESTERNO;
import static it.unina.picus.utenze.web.OperatoriDiProva.INTERNO;
import static it.unina.picus.utenze.web.OperatoriDiProva.MESSAGGIO_NUOVO_LINK;
import static it.unina.picus.utenze.web.OperatoriDiProva.NOME_VISUALIZZATO;
import static it.unina.picus.utenze.web.OperatoriDiProva.PASSWORD;
import static it.unina.picus.utenze.web.OperatoriDiProva.attendi;
import static it.unina.picus.utenze.web.OperatoriDiProva.attendiReinviiConclusi;
import static it.unina.picus.utenze.web.OperatoriDiProva.impronta;
import static it.unina.picus.utenze.web.OperatoriDiProva.ultimoLink;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.interno.applicazione.ReinvioLinkSuRichiesta;
import it.unina.picus.utenze.web.OperatoriDiProva.OperatoreDiProva;

/**
 * Verifica via HTTP, su PostgreSQL 17 in Docker, dei criteri 7 e 8 della storia #27 (task #154): nuovo link di
 * attivazione chiesto con la propria email da un Operatore in attesa, che rende inutilizzabile il precedente, e
 * stessa risposta senza alcun link per indirizzi non registrati o di account già attivi o revocati.
 *
 * <p>La richiesta senza sessione risponde subito e il server prepara e spedisce il link in secondo piano: i test
 * attendono l'email sullo spy di {@link InvioEmail} e poi lo stato dei link nel database; quando la richiesta non
 * deve avere effetti, attendono che tutte le richieste accodate siano concluse prima di verificarlo. Ogni test usa
 * indirizzi propri, perché il limite di tre richieste ogni cinque minuti per indirizzo vale per tutto il contesto. Il
 * reinvio da parte del Banco, che condivide le stesse regole sui link, è verificato insieme al criterio 7.</p>
 */
@IntegrazionePostgresql
@DisplayName("Nuovo link di attivazione per l'Operatore via HTTP su PostgreSQL (#154, storia #27)")
class ReinvioAttivazioneOperatoreTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReinvioLinkSuRichiesta reinvio;

    @MockitoSpyBean
    private InvioEmail posta;

    private DatiSintetici dati;
    private OperatoriDiProva operatori;
    private UUID banco;
    private long m01;

    @BeforeEach
    void prepara() {
        // Nessun lavoro in secondo piano di un test precedente deve arrivare in questo: si attende che sia concluso
        // e si azzerano le chiamate registrate dallo spy, così i conteggi delle email riguardano solo questo test.
        attendiReinviiConclusi(reinvio);
        clearInvocations(posta);
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        operatori = new OperatoriDiProva(mvc, jdbc);
        m01 = dati.misura("M01");
        banco = dati.banco("Banco di coordinamento");
    }

    @Nested
    @DisplayName("Criterio 7: chi è in attesa chiede un nuovo link con la propria email, il precedente non vale più")
    class NuovoLinkPerAccountInAttesa {

        @ParameterizedTest(name = "tipologia {0}")
        @ValueSource(strings = { INTERNO, ESTERNO })
        @DisplayName("Email scritta con maiuscole e spazi: nuovo link a quell'indirizzo, il vecchio è invalidato")
        void nuovoLinkSostituisceIlPrecedente(String tipologia) throws Exception {
            String email = indirizzo("attesa");
            OperatoreDiProva operatore = operatori.creaValido(banco, posta, email, CF_A, tipologia, m01);

            assertConferma(operatori.richiediNuovoLink("  " + email.toUpperCase() + " ").andReturn());

            String nuovo = ultimoLink(posta, email, 2);
            attendi("il link precedente risulta usato", () -> operatori.usato(operatore.link()));
            assertThat(nuovo).isNotEqualTo(operatore.link());
            // Destinatario: l'indirizzo salvato dell'account, nella forma normalizzata, e nessun altro.
            verify(posta).inviaLinkAttivazione(email, NOME_VISUALIZZATO, nuovo);
            verify(posta, times(2)).inviaLinkAttivazione(eq(email), eq(NOME_VISUALIZZATO), anyString());
            verify(posta, times(2)).inviaLinkAttivazione(anyString(), anyString(), anyString());
            assertThat(operatori.impronteAttive(operatore.id())).containsExactly(impronta(nuovo));
            assertThat(operatori.token(nuovo).get("account_id")).isEqualTo(operatore.id());

            operatori.attiva(operatore.link(), PASSWORD).andExpect(status().isBadRequest());
            assertThat(operatori.stato(operatore.id())).isEqualTo("IN_ATTESA_DI_ATTIVAZIONE");
            operatori.attiva(nuovo, PASSWORD).andExpect(status().isOk());
            assertThat(operatori.stato(operatore.id())).isEqualTo("ATTIVO");
        }

        @Test
        @DisplayName("Con due Operatori in attesa riceve il link solo chi è indicato; l'altro conserva il suo link")
        void soloLIndirizzoIndicatoRiceveIlLink() throws Exception {
            String emailA = indirizzo("destinatario");
            String emailB = indirizzo("estraneo");
            OperatoreDiProva a = operatori.creaValido(banco, posta, emailA, CF_A, INTERNO, m01);
            OperatoreDiProva b = operatori.creaValido(banco, posta, emailB, CF_B, ESTERNO, m01);

            assertConferma(operatori.richiediNuovoLink(emailA).andReturn());

            String nuovo = ultimoLink(posta, emailA, 2);
            attendi("il link precedente di A risulta usato", () -> operatori.usato(a.link()));
            verify(posta).inviaLinkAttivazione(emailA, NOME_VISUALIZZATO, nuovo);
            verify(posta, times(1)).inviaLinkAttivazione(eq(emailB), anyString(), anyString());
            verify(posta, times(3)).inviaLinkAttivazione(anyString(), anyString(), anyString());
            assertThat(operatori.token(nuovo).get("account_id")).isEqualTo(a.id());
            assertThat(operatori.impronteAttive(b.id())).containsExactly(impronta(b.link()));
            operatori.attiva(b.link(), PASSWORD).andExpect(status().isOk());
        }

        @Test
        @DisplayName("Due richieste di seguito: vale solo l'ultimo link, gli altri due sono invalidati")
        void dueRichiesteDiSeguito() throws Exception {
            String email = indirizzo("due-richieste");
            OperatoreDiProva operatore = operatori.creaValido(banco, posta, email, CF_A, INTERNO, m01);

            assertConferma(operatori.richiediNuovoLink(email).andReturn());
            String secondo = ultimoLink(posta, email, 2);
            attendi("il primo link risulta usato", () -> operatori.usato(operatore.link()));
            assertConferma(operatori.richiediNuovoLink(email).andReturn());
            String terzo = ultimoLink(posta, email, 3);
            attendi("il secondo link risulta usato", () -> operatori.usato(secondo));

            assertThat(List.of(operatore.link(), secondo, terzo)).doesNotHaveDuplicates();
            assertThat(operatori.impronteAttive(operatore.id())).containsExactly(impronta(terzo));
            operatori.attiva(secondo, PASSWORD).andExpect(status().isBadRequest());
            operatori.attiva(terzo, PASSWORD).andExpect(status().isOk());
        }

        @Test
        @DisplayName("Se la posta rifiuta il nuovo link, il link nuovo è scartato e resta valido quello già ricevuto")
        void invioFallitoConservaIlPrecedente() throws Exception {
            String email = indirizzo("smtp");
            OperatoreDiProva operatore = operatori.creaValido(banco, posta, email, CF_A, INTERNO, m01);
            doThrow(new IllegalStateException("SMTP non raggiungibile"))
                    .when(posta).inviaLinkAttivazione(eq(email), anyString(), anyString());

            assertConferma(operatori.richiediNuovoLink(email).andReturn());

            String nonConsegnato = ultimoLink(posta, email, 2);
            attendi("il link non consegnato risulta scartato", () -> operatori.usato(nonConsegnato));
            assertThat(operatori.impronteAttive(operatore.id())).containsExactly(impronta(operatore.link()));
            operatori.attiva(operatore.link(), PASSWORD).andExpect(status().isOk());
        }

        @Test
        @DisplayName("Reinvio del Banco: nuovo link all'Operatore in attesa, il precedente non vale più")
        void reinvioDelBanco() throws Exception {
            doReturn(false).when(posta).isSimulato();
            String email = indirizzo("banco");
            OperatoreDiProva operatore = operatori.creaValido(banco, posta, email, CF_A, ESTERNO, m01);

            operatori.reinvioDelBanco(banco, "BANCO", operatore.id())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountId").value(operatore.id().toString()))
                    .andExpect(jsonPath("$.invitoInviato").value(true))
                    .andExpect(jsonPath("$.erroreInvio").value(false))
                    .andExpect(jsonPath("$.messaggio").value("Nuovo link di attivazione inviato via email."));

            String nuovo = ultimoLink(posta, email, 2);
            assertThat(nuovo).isNotEqualTo(operatore.link());
            // Il link va all'Operatore, non al Banco che ha chiesto il reinvio, e a nessun altro.
            verify(posta).inviaLinkAttivazione(email, NOME_VISUALIZZATO, nuovo);
            verify(posta, never()).inviaLinkAttivazione(eq(banco + "@picus.test"), anyString(), anyString());
            verify(posta, times(2)).inviaLinkAttivazione(anyString(), anyString(), anyString());
            assertThat(operatori.usato(operatore.link())).isTrue();
            assertThat(operatori.impronteAttive(operatore.id())).containsExactly(impronta(nuovo));
        }

        @Test
        @DisplayName("Reinvio del Banco non consegnato: 502, resta valido il link già ricevuto")
        void reinvioDelBancoNonConsegnato() throws Exception {
            String email = indirizzo("banco-smtp");
            OperatoreDiProva operatore = operatori.creaValido(banco, posta, email, CF_A, INTERNO, m01);
            doThrow(new IllegalStateException("SMTP non raggiungibile"))
                    .when(posta).inviaLinkAttivazione(eq(email), anyString(), anyString());

            operatori.reinvioDelBanco(banco, "BANCO", operatore.id())
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.invitoInviato").value(false))
                    .andExpect(jsonPath("$.erroreInvio").value(true));

            String nonConsegnato = ultimoLink(posta, email, 2);
            assertThat(operatori.usato(nonConsegnato)).isTrue();
            assertThat(operatori.impronteAttive(operatore.id())).containsExactly(impronta(operatore.link()));
        }

        @Test
        @DisplayName("Reinvio del Banco per account inesistente (404), già attivo o non Operatore (409): nessun link")
        void reinvioDelBancoNonApplicabile() throws Exception {
            UUID attivo = operatori.inserisciAccount(indirizzo("attivo"), "OPERATORE", "ATTIVO");
            UUID opt = operatori.inserisciAccount(indirizzo("opt"), "OPT", "IN_ATTESA_DI_ATTIVAZIONE");

            operatori.reinvioDelBanco(banco, "BANCO", UUID.randomUUID()).andExpect(status().isNotFound());
            operatori.reinvioDelBanco(banco, "BANCO", attivo).andExpect(status().isConflict());
            operatori.reinvioDelBanco(banco, "BANCO", opt).andExpect(status().isConflict());

            assertThat(operatori.conta("select count(*) from utenze_token_attivazione")).isZero();
            verify(posta, never()).inviaLinkAttivazione(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Reinvio chiesto da OPT o Operatore (403) o senza sessione (401): nessun nuovo link")
        void reinvioDelBancoNegato() throws Exception {
            String email = indirizzo("negato");
            OperatoreDiProva operatore = operatori.creaValido(banco, posta, email, CF_A, INTERNO, m01);
            UUID opt = dati.opt("OPT richiedente", "napoli");
            UUID altro = dati.operatore("Operatore richiedente", INTERNO, "napoli", false, m01);

            operatori.reinvioDelBanco(opt, "OPT", operatore.id()).andExpect(status().isForbidden());
            operatori.reinvioDelBanco(altro, "OPERATORE", operatore.id()).andExpect(status().isForbidden());
            mvc.perform(post("/api/operatori/" + operatore.id() + "/reinvia-invito").with(csrf()))
                    .andExpect(status().isUnauthorized());

            assertThat(operatori.tokenEmessi(operatore.id())).isEqualTo(1);
            assertThat(operatori.impronteAttive(operatore.id())).containsExactly(impronta(operatore.link()));
            verify(posta, times(1)).inviaLinkAttivazione(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Criterio 8: per un'email non registrata, di un account attivo o revocato non parte nessun link e la risposta è la stessa")
    class NessunLinkStessaRisposta {

        @Test
        @DisplayName("Email non registrata: stessa risposta di un account in attesa, nessun account e nessun link")
        void emailNonRegistrata() throws Exception {
            String sconosciuta = indirizzo("sconosciuta");

            assertStessaRispostaSenzaLink(sconosciuta, 0);

            assertThat(operatori.conta("select count(*) from utenze_account where email = ?", sconosciuta)).isZero();
        }

        @Test
        @DisplayName("Operatore già attivo: stessa risposta, nessun nuovo link e password invariata")
        void accountAttivo() throws Exception {
            String email = indirizzo("attivo");
            OperatoreDiProva operatore = operatori.creaValido(banco, posta, email, CF_A, INTERNO, m01);
            operatori.attiva(operatore.link(), PASSWORD).andExpect(status().isOk());
            String hash = operatori.passwordHash(operatore.id());

            assertStessaRispostaSenzaLink(email, 1);

            assertThat(operatori.tokenEmessi(operatore.id())).isEqualTo(1);
            assertThat(operatori.impronteAttive(operatore.id())).isEmpty();
            assertThat(operatori.stato(operatore.id())).isEqualTo("ATTIVO");
            assertThat(operatori.passwordHash(operatore.id())).isEqualTo(hash);
        }

        @Test
        @DisplayName("Operatore revocato: stessa risposta, nessun nuovo link e l'account resta revocato")
        void accountRevocato() throws Exception {
            String email = indirizzo("revocato");
            OperatoreDiProva operatore = operatori.creaValido(banco, posta, email, CF_A, ESTERNO, m01);
            dati.impostaStatoAccount(operatore.id(), "REVOCATO");

            assertStessaRispostaSenzaLink(email, 1);

            assertThat(operatori.tokenEmessi(operatore.id())).isEqualTo(1);
            assertThat(operatori.stato(operatore.id())).isEqualTo("REVOCATO");
        }

        /**
         * Confronta la risposta per l'indirizzo con quella per un Operatore in attesa, chiesta subito dopo. Poi
         * attende che tutte le richieste accodate siano concluse, cioè che anche la richiesta per l'indirizzo
         * sotto test sia stata elaborata fino in fondo, e solo allora verifica email e link: all'indirizzo non è
         * partito nient'altro oltre alle email già ricevute e l'unico link nuovo è quello dell'Operatore in attesa.
         */
        private void assertStessaRispostaSenzaLink(String email, int emailGiaRicevute) throws Exception {
            String inAttesa = indirizzo("riferimento");
            operatori.creaValido(banco, posta, inAttesa, CF_B, INTERNO, m01);
            int tokenPrima = operatori.conta("select count(*) from utenze_token_attivazione");

            MvcResult risposta = operatori.richiediNuovoLink(email).andReturn();
            MvcResult riferimento = operatori.richiediNuovoLink(inAttesa).andReturn();

            assertConferma(riferimento);
            assertThat(risposta.getResponse().getStatus()).isEqualTo(riferimento.getResponse().getStatus());
            assertThat(risposta.getResponse().getContentAsString())
                    .isEqualTo(riferimento.getResponse().getContentAsString());
            assertThat(risposta.getResponse().getContentType()).isEqualTo(riferimento.getResponse().getContentType());

            attendiReinviiConclusi(reinvio);
            verify(posta, times(emailGiaRicevute)).inviaLinkAttivazione(eq(email), anyString(), anyString());
            verify(posta, times(2)).inviaLinkAttivazione(eq(inAttesa), anyString(), anyString());
            // L'unico link in più è quello dell'Operatore in attesa.
            assertThat(operatori.conta("select count(*) from utenze_token_attivazione")).isEqualTo(tokenPrima + 1);
        }
    }

    /** La risposta pubblica è sempre {@code 200} con il solo messaggio di conferma. */
    private static void assertConferma(MvcResult risposta) throws Exception {
        assertThat(risposta.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> corpo = JsonPath.read(risposta.getResponse().getContentAsString(), "$");
        assertThat(corpo).containsExactly(Map.entry("messaggio", MESSAGGIO_NUOVO_LINK));
    }

    /** Indirizzo nuovo a ogni chiamata: il limite delle richieste per indirizzo dura più di un test. */
    private static String indirizzo(String prefisso) {
        return prefisso + "-" + UUID.randomUUID().toString().substring(0, 8) + "@picus.test";
    }
}
