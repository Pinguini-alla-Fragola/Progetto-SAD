package it.unina.picus.utenze.interno.applicazione;

import static it.unina.picus.utenze.web.OperatoriDiProva.CF_A;
import static it.unina.picus.utenze.web.OperatoriDiProva.CF_B;
import static it.unina.picus.utenze.web.OperatoriDiProva.CODICE_FISCALE_IN_USO;
import static it.unina.picus.utenze.web.OperatoriDiProva.COGNOME;
import static it.unina.picus.utenze.web.OperatoriDiProva.COMUNE;
import static it.unina.picus.utenze.web.OperatoriDiProva.EMAIL_IN_USO;
import static it.unina.picus.utenze.web.OperatoriDiProva.INTERNO;
import static it.unina.picus.utenze.web.OperatoriDiProva.NOME;
import static it.unina.picus.utenze.web.OperatoriDiProva.TELEFONO;
import static it.unina.picus.utenze.web.OperatoriDiProva.impronta;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import it.unina.picus.shared.AccessoNegatoException;
import it.unina.picus.shared.DatiSintetici;
import it.unina.picus.shared.IntegrazionePostgresql;
import it.unina.picus.shared.InvioEmail;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.web.OperatoriDiProva;

/**
 * Verifica dei servizi di creazione e attivazione degli Operatori (task #154, storia #27) su PostgreSQL 17 in
 * Docker, dove contano i vincoli di unicità e i lock pessimistici: creazioni contemporanee con la stessa email o lo
 * stesso codice fiscale (criterio 4), uso contemporaneo dello stesso link monouso (criteri 5 e 6) e controllo del
 * ruolo anche quando il servizio è chiamato senza passare dal web (criterio 9).
 *
 * <p>Nelle gare due thread partono insieme da una barriera; dall'esito si ricava quale comando ha vinto e lo si
 * confronta esattamente con i dati salvati. Il perdente deve fallire con l'errore previsto, non con un errore
 * tecnico qualsiasi. I test sono ripetuti per aumentare la probabilità di attraversare entrambi i percorsi del
 * duplicato: controllo preventivo e vincolo del database al salvataggio.</p>
 */
@IntegrazionePostgresql
@DisplayName("Creazione e attivazione degli Operatori: concorrenza e ruolo nei servizi su PostgreSQL (#154, storia #27)")
class ServizioCreazioneOperatoriTest {

    private static final String PRIMA_PASSWORD = "Password-prima-154";
    private static final String SECONDA_PASSWORD = "Password-seconda-154";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CreazioneOperatori creazione;

    @Autowired
    private AttivazioneUtenzaService attivazione;

    @Autowired
    private PasswordEncoder encoder;

    @MockitoSpyBean
    private InvioEmail posta;

    private DatiSintetici dati;
    private OperatoriDiProva operatori;
    private IdentitaAutenticata banco;
    private long m01;

    @BeforeEach
    void prepara() {
        dati = new DatiSintetici(jdbc);
        dati.svuota();
        operatori = new OperatoriDiProva(mvc, jdbc);
        m01 = dati.misura("M01");
        banco = new IdentitaAutenticata(dati.banco("Banco di coordinamento"), RuoloUtente.BANCO);
    }

    @Nested
    @DisplayName("Criterio 4: creazioni contemporanee con la stessa email o lo stesso codice fiscale")
    class DuplicatiContemporanei {

        @RepeatedTest(value = 5, name = "ripetizione {currentRepetition} di {totalRepetitions}")
        @DisplayName("Stessa email: un solo account, l'altra creazione segnala l'email già in uso")
        void stessaEmail() throws Exception {
            List<Esito<OperatoreCreato>> esiti = inParallelo(
                    () -> creazione.crea(banco, comando("gara@picus.test", CF_A)),
                    () -> creazione.crea(banco, comando("gara@picus.test", CF_B)));

            int vincitore = indiceUnicoRiuscito(esiti);
            Esito<OperatoreCreato> perdente = esiti.get(1 - vincitore);
            assertThat(perdente.errore()).isExactlyInstanceOf(DatiOperatoreNonValidiException.class);
            assertThat(((DatiOperatoreNonValidiException) perdente.errore()).campi())
                    .containsExactly(Map.entry("email", EMAIL_IN_USO));

            OperatoreCreato creato = esiti.get(vincitore).valore();
            String cfVincitore = vincitore == 0 ? CF_A : CF_B;
            assertThat(operatori.conta("select count(*) from utenze_account where email = 'gara@picus.test'")).isEqualTo(1);
            assertThat(operatori.accountPerEmail("gara@picus.test")).isEqualTo(creato.id());
            assertThat(operatori.profilo(creato.id()).get("codice_fiscale")).isEqualTo(cfVincitore);
            assertThat(operatori.conta("select count(*) from utenze_profili_operatore")).isEqualTo(1);
            assertThat(operatori.impronteAttive(creato.id())).containsExactly(impronta(creato.invito().tokenInChiaro()));
            assertThat(operatori.conta("select count(*) from utenze_token_attivazione")).isEqualTo(1);
        }

        @RepeatedTest(value = 5, name = "ripetizione {currentRepetition} di {totalRepetitions}")
        @DisplayName("Stesso codice fiscale: un solo Operatore, l'account del perdente non resta salvato")
        void stessoCodiceFiscale() throws Exception {
            List<Esito<OperatoreCreato>> esiti = inParallelo(
                    () -> creazione.crea(banco, comando("primo-cf@picus.test", CF_A)),
                    () -> creazione.crea(banco, comando("secondo-cf@picus.test", CF_A)));

            int vincitore = indiceUnicoRiuscito(esiti);
            Esito<OperatoreCreato> perdente = esiti.get(1 - vincitore);
            assertThat(perdente.errore()).isExactlyInstanceOf(DatiOperatoreNonValidiException.class);
            assertThat(((DatiOperatoreNonValidiException) perdente.errore()).campi())
                    .containsExactly(Map.entry("codiceFiscale", CODICE_FISCALE_IN_USO));

            String emailVincitore = vincitore == 0 ? "primo-cf@picus.test" : "secondo-cf@picus.test";
            String emailPerdente = vincitore == 0 ? "secondo-cf@picus.test" : "primo-cf@picus.test";
            assertThat(operatori.accountPerEmail(emailVincitore)).isEqualTo(esiti.get(vincitore).valore().id());
            // Account, profilo e link del perdente sono annullati insieme.
            assertThat(operatori.conta("select count(*) from utenze_account where email = ?", emailPerdente)).isZero();
            assertThat(operatori.conta("select count(*) from utenze_account where ruolo = 'OPERATORE'")).isEqualTo(1);
            assertThat(operatori.conta("select count(*) from utenze_token_attivazione")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Criteri 5 e 6: il link è monouso anche con due attivazioni contemporanee")
    class AttivazioniContemporanee {

        @RepeatedTest(value = 3, name = "ripetizione {currentRepetition} di {totalRepetitions}")
        @DisplayName("Stesso link usato due volte insieme: vale solo la password di chi arriva prima")
        void stessoLinkDueVolte() throws Exception {
            OperatoreCreato creato = creazione.crea(banco, comando("doppia@picus.test", CF_A));
            String link = creato.invito().tokenInChiaro();

            List<Esito<Void>> esiti = inParallelo(
                    () -> {
                        attivazione.attivaAccount(link, PRIMA_PASSWORD);
                        return null;
                    },
                    () -> {
                        attivazione.attivaAccount(link, SECONDA_PASSWORD);
                        return null;
                    });

            int vincitore = indiceUnicoRiuscito(esiti);
            Throwable errore = esiti.get(1 - vincitore).errore();
            assertThat(errore).isExactlyInstanceOf(TokenAttivazioneNonValidoException.class)
                    .hasMessage("L'account non è più attivabile.");

            String hash = operatori.passwordHash(creato.id());
            assertThat(encoder.matches(vincitore == 0 ? PRIMA_PASSWORD : SECONDA_PASSWORD, hash)).isTrue();
            assertThat(encoder.matches(vincitore == 0 ? SECONDA_PASSWORD : PRIMA_PASSWORD, hash)).isFalse();
            assertThat(operatori.stato(creato.id())).isEqualTo("ATTIVO");
            assertThat(operatori.usato(link)).isTrue();
        }
    }

    @Nested
    @DisplayName("Criterio 9: il servizio rifiuta chi non è il Banco anche senza passare dal web")
    class RuoloNelServizio {

        @Test
        @DisplayName("OPT, Operatore o identità assente: accesso negato, nessun account e nessun link")
        void soloIlBanco() {
            List<IdentitaAutenticata> attori = new ArrayList<>();
            attori.add(new IdentitaAutenticata(dati.opt("OPT richiedente", "napoli"), RuoloUtente.OPT));
            attori.add(new IdentitaAutenticata(
                    dati.operatore("Operatore richiedente", INTERNO, "napoli", false, m01), RuoloUtente.OPERATORE));
            attori.add(new IdentitaAutenticata(null, RuoloUtente.BANCO));
            attori.add(null);
            int account = operatori.conta("select count(*) from utenze_account");

            for (IdentitaAutenticata attore : attori) {
                assertThatThrownBy(() -> creazione.crea(attore, comando("negato@picus.test", CF_A)))
                        .as("attore %s", attore)
                        .isInstanceOf(AccessoNegatoException.class);
            }

            assertThat(operatori.conta("select count(*) from utenze_account")).isEqualTo(account);
            assertThat(operatori.conta("select count(*) from utenze_token_attivazione")).isZero();
            verify(posta, never()).inviaLinkAttivazione(anyString(), anyString(), anyString());
        }
    }

    private ComandoCreaOperatore comando(String email, String codiceFiscale) {
        return new ComandoCreaOperatore(NOME, COGNOME, codiceFiscale, email, TELEFONO, COMUNE, INTERNO, List.of(m01),
                false);
    }

    /** Esito di un comando in gara: il valore restituito oppure l'errore lanciato. */
    private record Esito<T>(T valore, Throwable errore) {}

    /** Avvia i comandi insieme dietro una barriera e raccoglie gli esiti nell'ordine dei comandi. */
    @SafeVarargs
    private static <T> List<Esito<T>> inParallelo(Callable<T>... comandi) throws Exception {
        ExecutorService esecutore = Executors.newFixedThreadPool(comandi.length);
        CyclicBarrier partenza = new CyclicBarrier(comandi.length);
        try {
            List<Future<T>> futuri = new ArrayList<>();
            for (Callable<T> comando : comandi) {
                futuri.add(esecutore.submit(() -> {
                    partenza.await(10, SECONDS);
                    return comando.call();
                }));
            }
            List<Esito<T>> esiti = new ArrayList<>();
            for (Future<T> futuro : futuri) {
                try {
                    esiti.add(new Esito<>(futuro.get(30, SECONDS), null));
                } catch (ExecutionException e) {
                    esiti.add(new Esito<>(null, e.getCause()));
                }
            }
            return esiti;
        } finally {
            esecutore.shutdownNow();
        }
    }

    /** Verifica che esattamente un comando sia riuscito e ne restituisce la posizione. */
    private static <T> int indiceUnicoRiuscito(List<Esito<T>> esiti) {
        List<Integer> riusciti = new ArrayList<>();
        for (int i = 0; i < esiti.size(); i++) {
            if (esiti.get(i).errore() == null) {
                riusciti.add(i);
            }
        }
        assertThat(riusciti).as("comandi riusciti, esiti %s", esiti).hasSize(1);
        return riusciti.get(0);
    }
}
