package it.unina.picus.utenze.web;

import static it.unina.picus.utenze.web.OperatoriDiProva.CF_A;
import static it.unina.picus.utenze.web.OperatoriDiProva.CF_B;
import static it.unina.picus.utenze.web.OperatoriDiProva.ESTERNO;
import static it.unina.picus.utenze.web.OperatoriDiProva.INTERNO;
import static it.unina.picus.utenze.web.OperatoriDiProva.PASSWORD;
import static it.unina.picus.utenze.web.OperatoriDiProva.impronta;
import static it.unina.picus.utenze.web.OperatoriDiProva.json;
import static it.unina.picus.utenze.web.OperatoriDiProva.modulo;
import static it.unina.picus.utenze.web.OperatoriDiProva.ultimoLink;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.web.OperatoriDiProva.OperatoreDiProva;

/**
 * Verifica via HTTP, su PostgreSQL 17 in Docker, dei criteri 5 e 6 della storia #27 (task #154): attivazione
 * dell'account Operatore con il link ricevuto alla creazione e rifiuto dei link scaduti, già usati o sostituiti.
 *
 * <p>Ogni Operatore è creato dal Banco via HTTP e il link è quello effettivamente consegnato all'email
 * ({@link InvioEmail} è uno spy). Dopo l'attivazione si entra con il vero login JSON e si verificano le
 * funzioni del ruolo Operatore e i dinieghi su quelle del coordinamento Banco. Il limite delle 72 ore al
 * millisecondo è verificato con un orologio fisso in {@code AttivazioneOperatoreScadenzaTest}.</p>
 */
@IntegrazionePostgresql
@DisplayName("Attivazione dell'account Operatore dal link via HTTP su PostgreSQL (#154, storia #27)")
class AttivazioneOperatoreTest {

    private static final String ALTRA_PASSWORD = "Altra-password-154";
    private static final String MESSAGGIO_ATTIVATO = "Account attivato con successo. Ora puoi accedere da Accedi.";
    private static final String LINK_SCADUTO_O_USATO = "Il link di attivazione è scaduto o è già stato utilizzato.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private LimitatoreAccessi limitatoreAccessi;

    @MockitoSpyBean
    private InvioEmail posta;

    private OperatoriDiProva operatori;
    private UUID banco;
    private long m01;

    @BeforeEach
    void prepara() {
        DatiSintetici dati = new DatiSintetici(jdbc);
        dati.svuota();
        limitatoreAccessi.azzeraTutto();
        operatori = new OperatoriDiProva(mvc, jdbc);
        m01 = dati.misura("M01");
        banco = dati.banco("Banco di coordinamento");
    }

    @Nested
    @DisplayName("Criterio 5: con un link mai usato entro 72 ore l'Operatore imposta la password e l'account diventa Attivo")
    class AttivazioneValida {

        @ParameterizedTest(name = "tipologia {0}")
        @ValueSource(strings = { INTERNO, ESTERNO })
        @DisplayName("Attiva, consuma il link, consente le funzioni dell'Operatore e nega quelle del coordinamento Banco")
        void attivaETrovaSoloLeFunzioniOperatore(String tipologia) throws Exception {
            String email = "attiva-" + tipologia.toLowerCase() + "@picus.test";
            OperatoreDiProva operatore = operatori.creaValido(banco, posta, email, CF_A, tipologia, m01);
            String hashIniziale = operatori.passwordHash(operatore.id());

            // In attesa di attivazione non si entra, neppure conoscendo la password che verrà scelta.
            assertThat(operatori.accedi(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);

            operatori.paginaAttivazione(operatore.link())
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("valido", true))
                    .andExpect(model().attribute("endpoint", "/api/auth/attiva/conferma"))
                    .andExpect(content().string(containsString("name=\"nuovaPassword\"")));

            operatori.attiva(operatore.link(), PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.messaggio").value(MESSAGGIO_ATTIVATO));

            assertThat(operatori.stato(operatore.id())).isEqualTo("ATTIVO");
            assertThat(operatori.account(operatore.id()).get("ruolo")).isEqualTo("OPERATORE");
            String hash = operatori.passwordHash(operatore.id());
            assertThat(hash).isNotEqualTo(hashIniziale).doesNotContain(PASSWORD);
            assertThat(encoder.matches(PASSWORD, hash)).isTrue();
            assertThat(operatori.usato(operatore.link())).isTrue();
            assertThat(operatori.impronteAttive(operatore.id())).isEmpty();

            MockHttpSession sessione = operatori.sessioneDopoAccesso(email, PASSWORD);
            mvc.perform(get("/").session(sessione))
                    .andExpect(redirectedUrl("/operatore/dashboard"));
            mvc.perform(get("/operatore/dashboard").session(sessione))
                    .andExpect(status().isOk())
                    .andExpect(view().name("operatore/dashboard"));

            // Nessun permesso di coordinamento Banco.
            mvc.perform(get("/banco/dashboard").session(sessione))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/richieste/coda").session(sessione))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/operatori/" + operatore.id() + "/reinvia-invito").session(sessione).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/utenze/operatori").session(sessione).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(modulo("creato-da-operatore@picus.test", CF_B, INTERNO, m01))))
                    .andExpect(status().isForbidden());
            assertThat(operatori.conta("select count(*) from utenze_account where ruolo = 'OPERATORE'")).isEqualTo(1);
        }

        @Test
        @DisplayName("Link che scade tra un minuto: è ancora entro le 72 ore e attiva l'account")
        void linkVicinoAllaScadenza() throws Exception {
            OperatoreDiProva operatore = operatori.creaValido(banco, posta, "quasi@picus.test", CF_A, INTERNO, m01);
            operatori.impostaScadenza(operatore.link(), Instant.now().plus(Duration.ofMinutes(1)));

            operatori.attiva(operatore.link(), PASSWORD).andExpect(status().isOk());

            assertThat(operatori.stato(operatore.id())).isEqualTo("ATTIVO");
        }

        @Test
        @DisplayName("Password troppo corta: 400, l'account resta in attesa e lo stesso link funziona con una password valida")
        void passwordNonConforme() throws Exception {
            OperatoreDiProva operatore = operatori.creaValido(banco, posta, "corta@picus.test", CF_A, INTERNO, m01);

            operatori.attiva(operatore.link(), "Corta-11car")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.messaggio").value("La password deve contenere almeno 12 caratteri."));

            assertThat(operatori.stato(operatore.id())).isEqualTo("IN_ATTESA_DI_ATTIVAZIONE");
            assertThat(operatori.usato(operatore.link())).isFalse();

            operatori.attiva(operatore.link(), PASSWORD).andExpect(status().isOk());
            assertThat(operatori.stato(operatore.id())).isEqualTo("ATTIVO");
        }
    }

    @Nested
    @DisplayName("Criterio 6: un link scaduto o già usato non permette di impostare la password e propone un nuovo link")
    class LinkNonValido {

        @Test
        @DisplayName("Link scaduto: la pagina propone un nuovo link e la conferma non cambia nulla")
        void scaduto() throws Exception {
            OperatoreDiProva operatore = operatori.creaValido(banco, posta, "scaduto@picus.test", CF_A, INTERNO, m01);
            operatori.impostaScadenza(operatore.link(), Instant.now().minusSeconds(1));
            String hashPrima = operatori.passwordHash(operatore.id());

            assertPropostaNuovoLink(operatori.paginaAttivazione(operatore.link()));
            operatori.attiva(operatore.link(), PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.messaggio").value(LINK_SCADUTO_O_USATO));

            assertThat(operatori.stato(operatore.id())).isEqualTo("IN_ATTESA_DI_ATTIVAZIONE");
            assertThat(operatori.passwordHash(operatore.id())).isEqualTo(hashPrima);
            assertThat(operatori.usato(operatore.link())).isFalse();
            assertThat(operatori.accedi("scaduto@picus.test", PASSWORD).getResponse().getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("Link già usato: la pagina propone un nuovo link e una seconda password non sostituisce la prima")
        void giaUsato() throws Exception {
            OperatoreDiProva operatore = operatori.creaValido(banco, posta, "usato@picus.test", CF_A, ESTERNO, m01);
            operatori.attiva(operatore.link(), PASSWORD).andExpect(status().isOk());
            String hashDopoAttivazione = operatori.passwordHash(operatore.id());

            assertPropostaNuovoLink(operatori.paginaAttivazione(operatore.link()));
            operatori.attiva(operatore.link(), ALTRA_PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.messaggio").value("L'account non è più attivabile."));

            assertThat(operatori.passwordHash(operatore.id())).isEqualTo(hashDopoAttivazione);
            assertThat(operatori.accedi("usato@picus.test", ALTRA_PASSWORD).getResponse().getStatus()).isEqualTo(401);
            assertThat(operatori.accedi("usato@picus.test", PASSWORD).getResponse().getStatus()).isEqualTo(204);
        }

        @Test
        @DisplayName("Link sostituito da un reinvio: non attiva più l'account, mentre il nuovo link sì")
        void sostituito() throws Exception {
            OperatoreDiProva operatore = operatori.creaValido(banco, posta, "sostituito@picus.test", CF_A, INTERNO, m01);
            operatori.reinvioDelBanco(banco, "BANCO", operatore.id()).andExpect(status().isOk());
            String nuovo = ultimoLink(posta, "sostituito@picus.test", 2);
            assertThat(nuovo).isNotEqualTo(operatore.link());

            assertPropostaNuovoLink(operatori.paginaAttivazione(operatore.link()));
            operatori.attiva(operatore.link(), PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.messaggio").value(LINK_SCADUTO_O_USATO));
            assertThat(operatori.stato(operatore.id())).isEqualTo("IN_ATTESA_DI_ATTIVAZIONE");
            assertThat(operatori.impronteAttive(operatore.id())).containsExactly(impronta(nuovo));

            operatori.attiva(nuovo, PASSWORD).andExpect(status().isOk());
            assertThat(operatori.stato(operatore.id())).isEqualTo("ATTIVO");
        }

        @Test
        @DisplayName("Link inesistente o assente: la pagina propone un nuovo link e la conferma è rifiutata senza effetti")
        void inesistenteOAssente() throws Exception {
            OperatoreDiProva operatore = operatori.creaValido(banco, posta, "estraneo@picus.test", CF_A, INTERNO, m01);
            String inventato = "link-inventato-" + UUID.randomUUID();

            assertPropostaNuovoLink(operatori.paginaAttivazione(null));
            assertPropostaNuovoLink(operatori.paginaAttivazione(inventato));
            operatori.attiva(inventato, PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.messaggio").value("Link di attivazione non valido."));
            operatori.attiva(null, PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.messaggio").value("Link di attivazione non valido o mancante."));

            assertThat(operatori.stato(operatore.id())).isEqualTo("IN_ATTESA_DI_ATTIVAZIONE");
            assertThat(operatori.impronteAttive(operatore.id())).containsExactly(impronta(operatore.link()));
        }

        /** Pagina senza il campo password, con il modulo che chiede un nuovo link indicando l'email. */
        private void assertPropostaNuovoLink(ResultActions pagina) throws Exception {
            pagina.andExpect(status().isOk())
                    .andExpect(model().attribute("valido", false))
                    .andExpect(model().attribute("endpoint", "/api/auth/attiva/reinvio"))
                    .andExpect(content().string(containsString("Richiedi un nuovo link")))
                    .andExpect(content().string(containsString("name=\"email\"")))
                    .andExpect(content().string(not(containsString("name=\"nuovaPassword\""))));
        }
    }
}
