package it.unina.picus.utenze.interno.applicazione;

import static it.unina.picus.utenze.web.OperatoriDiProva.CF_A;
import static it.unina.picus.utenze.web.OperatoriDiProva.CF_B;
import static it.unina.picus.utenze.web.OperatoriDiProva.CF_C;
import static it.unina.picus.utenze.web.OperatoriDiProva.COGNOME;
import static it.unina.picus.utenze.web.OperatoriDiProva.COMUNE;
import static it.unina.picus.utenze.web.OperatoriDiProva.ESTERNO;
import static it.unina.picus.utenze.web.OperatoriDiProva.INTERNO;
import static it.unina.picus.utenze.web.OperatoriDiProva.NOME;
import static it.unina.picus.utenze.web.OperatoriDiProva.PASSWORD;
import static it.unina.picus.utenze.web.OperatoriDiProva.TELEFONO;
import static it.unina.picus.utenze.web.OperatoriDiProva.VALIDITA_LINK;
import static it.unina.picus.utenze.web.OperatoriDiProva.impronta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.interno.persistenza.AccountRepository;
import it.unina.picus.utenze.interno.persistenza.TokenAttivazioneRepository;
import it.unina.picus.utenze.web.OperatoriDiProva;

/**
 * Limite delle 72 ore del link di attivazione di un Operatore (task #154, storia #27, criteri 5 e 6), verificato
 * al millisecondo su PostgreSQL 17 in Docker.
 *
 * <p>Il servizio reale usa l'orologio di sistema; qui se ne costruiscono copie con un orologio fisso, sugli stessi
 * repository e sullo stesso database, dentro una transazione come farebbe il proxy di Spring. Il link è emesso
 * all'istante {@link #EMISSIONE} e viene usato appena prima e esattamente allo scadere delle 72 ore.</p>
 */
@IntegrazionePostgresql
@DisplayName("Scadenza a 72 ore del link di attivazione dell'Operatore su PostgreSQL (#154, storia #27)")
class AttivazioneOperatoreScadenzaTest {

    private static final Instant EMISSIONE = Instant.parse("2026-11-20T09:00:00Z");
    private static final Instant SCADENZA = EMISSIONE.plus(VALIDITA_LINK);
    private static final Duration UN_MILLISECONDO = Duration.ofMillis(1);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CreazioneOperatori creazione;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private TokenAttivazioneRepository tokens;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private ApplicationEventPublisher eventi;

    @Autowired
    private TransactionTemplate transazioni;

    @MockitoSpyBean
    private InvioEmail posta;

    private OperatoriDiProva operatori;
    private IdentitaAutenticata banco;
    private long m01;

    @BeforeEach
    void prepara() {
        DatiSintetici dati = new DatiSintetici(jdbc);
        dati.svuota();
        operatori = new OperatoriDiProva(mvc, jdbc);
        m01 = dati.misura("M01");
        banco = new IdentitaAutenticata(dati.banco("Banco di coordinamento"), RuoloUtente.BANCO);
    }

    @Nested
    @DisplayName("Criterio 5: entro 72 ore dall'emissione il link attiva l'account")
    class EntroLeSettantadueOre {

        @Test
        @DisplayName("Il link salvato vale esattamente 72 ore dall'istante di emissione")
        void durataSalvata() {
            UUID id = creaOperatore("durata@picus.test", CF_A, INTERNO);
            String link = emettiAlle(id, EMISSIONE);

            Map<String, Object> token = operatori.token(link);
            assertThat(((Timestamp) token.get("creato_il")).toInstant()).isEqualTo(EMISSIONE);
            assertThat(((Timestamp) token.get("scadenza")).toInstant()).isEqualTo(SCADENZA);
            assertThat(operatori.impronteAttive(id)).containsExactly(impronta(link));
        }

        @ParameterizedTest(name = "tipologia {0}")
        @ValueSource(strings = { INTERNO, ESTERNO })
        @DisplayName("Un millisecondo prima della scadenza il link è valido e attiva l'account")
        void appenaPrimaDellaScadenza(String tipologia) {
            UUID id = creaOperatore("prima-" + tipologia.toLowerCase() + "@picus.test", CF_B, tipologia);
            String link = emettiAlle(id, EMISSIONE);
            Instant istante = SCADENZA.minus(UN_MILLISECONDO);

            assertThat(alle(istante).isTokenValido(link)).isTrue();
            transazioni.executeWithoutResult(stato -> alle(istante).attivaAccount(link, PASSWORD));

            assertThat(operatori.stato(id)).isEqualTo("ATTIVO");
            assertThat(encoder.matches(PASSWORD, operatori.passwordHash(id))).isTrue();
            assertThat(((Timestamp) operatori.token(link).get("usato_il")).toInstant()).isEqualTo(istante);
        }
    }

    @Nested
    @DisplayName("Criterio 6: allo scadere delle 72 ore il link non permette più di impostare la password")
    class AlloScadere {

        @Test
        @DisplayName("Allo scadere esatto e un millisecondo dopo il link non è valido")
        void nonValidoDallaScadenza() {
            UUID id = creaOperatore("validita@picus.test", CF_A, INTERNO);
            String link = emettiAlle(id, EMISSIONE);

            assertThat(alle(SCADENZA.minus(UN_MILLISECONDO)).isTokenValido(link)).isTrue();
            assertThat(alle(SCADENZA).isTokenValido(link)).isFalse();
            assertThat(alle(SCADENZA.plus(UN_MILLISECONDO)).isTokenValido(link)).isFalse();
        }

        @Test
        @DisplayName("Allo scadere esatto l'attivazione è rifiutata: account in attesa, password e link invariati")
        void attivazioneRifiutataAlloScadere() {
            UUID id = creaOperatore("scade@picus.test", CF_C, ESTERNO);
            String link = emettiAlle(id, EMISSIONE);
            String hashPrima = operatori.passwordHash(id);

            assertThatThrownBy(() -> transazioni.executeWithoutResult(
                    stato -> alle(SCADENZA).attivaAccount(link, PASSWORD)))
                    .isExactlyInstanceOf(TokenAttivazioneNonValidoException.class)
                    .hasMessage("Il link di attivazione è scaduto o è già stato utilizzato.");

            assertThat(operatori.stato(id)).isEqualTo("IN_ATTESA_DI_ATTIVAZIONE");
            assertThat(operatori.passwordHash(id)).isEqualTo(hashPrima);
            assertThat(operatori.usato(link)).isFalse();
        }
    }

    private UUID creaOperatore(String email, String codiceFiscale, String tipologia) {
        return creazione.crea(banco, new ComandoCreaOperatore(NOME, COGNOME, codiceFiscale, email, TELEFONO, COMUNE,
                tipologia, List.of(m01), false)).id();
    }

    /** Sostituisce il link della creazione con uno emesso all'istante indicato e lo restituisce in chiaro. */
    private String emettiAlle(UUID account, Instant istante) {
        return transazioni.execute(stato ->
                alle(istante).rigeneraTokenPerAccount(account, RuoloUtente.OPERATORE).tokenInChiaro());
    }

    /** Copia del servizio con l'orologio fermo all'istante indicato e la durata di produzione. */
    private AttivazioneUtenzaService alle(Instant istante) {
        return new AttivazioneUtenzaService(accounts, tokens, encoder, eventi, VALIDITA_LINK,
                Clock.fixed(istante, ZoneOffset.UTC));
    }
}
