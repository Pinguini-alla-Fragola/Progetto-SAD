package it.unina.picus.utenze.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*
Limite delle richieste di recupero password per indirizzo (US #33, regole-accessi.md): al massimo 3 richieste in
5 minuti, soglia ancora da validare con il PO. Test unitari senza Spring, con un orologio finto scritto a mano.
Le soglie sono costruite qui con gli stessi valori del bean di produzione.
*/
@DisplayName("Limitatore delle richieste di recupero password")
class LimitatoreRichiesteTest {

    private static final int MAX = 3;
    private static final Duration FINESTRA = Duration.ofMinutes(5);
    private static final Instant T0 = Instant.parse("2026-11-24T09:00:00Z");
    private static final Duration UN_MS = Duration.ofMillis(1);
    private static final String EMAIL = "utente@picus.test";

    private OrologioFinto orologio;
    private LimitatoreRichieste limitatore;

    @BeforeEach
    void prepara() {
        orologio = new OrologioFinto(T0);
        limitatore = new LimitatoreRichieste(orologio, MAX, FINESTRA, 10_000);
    }

    @Test
    @DisplayName("Le prime tre richieste sono ammesse, la quarta no")
    void quartaRichiestaRifiutata() {
        for (int i = 0; i < MAX; i++) {
            assertTrue(limitatore.ammetti(EMAIL).isPresent(), "richiesta " + (i + 1));
        }
        assertTrue(limitatore.ammetti(EMAIL).isEmpty());
    }

    @Test
    @DisplayName("Una richiesta conta per 5 minuti esatti: a 5 minuti è ancora nel limite, 1 ms dopo non più")
    void confineDellaFinestra() {
        for (int i = 0; i < MAX; i++) {
            limitatore.ammetti(EMAIL);
        }

        orologio.imposta(T0.plus(FINESTRA));
        assertTrue(limitatore.ammetti(EMAIL).isEmpty(), "a 5 minuti esatti");

        orologio.imposta(T0.plus(FINESTRA).plus(UN_MS));
        assertTrue(limitatore.ammetti(EMAIL).isPresent(), "1 ms dopo la finestra");
    }

    @Test
    @DisplayName("I rifiuti non allungano l'attesa")
    void rifiutiNonContano() {
        for (int i = 0; i < MAX; i++) {
            limitatore.ammetti(EMAIL);
        }
        orologio.imposta(T0.plus(Duration.ofMinutes(4)));
        assertTrue(limitatore.ammetti(EMAIL).isEmpty());

        orologio.imposta(T0.plus(FINESTRA).plus(UN_MS));
        assertTrue(limitatore.ammetti(EMAIL).isPresent());
    }

    @Test
    @DisplayName("Il limite è per indirizzo: un altro indirizzo non ne risente")
    void indirizziIndipendenti() {
        for (int i = 0; i < MAX; i++) {
            limitatore.ammetti(EMAIL);
        }
        assertTrue(limitatore.ammetti("altro@picus.test").isPresent());
    }

    @Test
    @DisplayName("Rilasciare un'ammissione libera esattamente quel posto, non quelli ammessi dopo")
    void rilascioEsatto() {
        Optional<LimitatoreRichieste.Ammissione> prima = limitatore.ammetti(EMAIL);
        limitatore.ammetti(EMAIL);
        limitatore.ammetti(EMAIL);
        assertTrue(limitatore.ammetti(EMAIL).isEmpty());

        limitatore.rilascia(prima.orElseThrow());
        limitatore.rilascia(prima.orElseThrow()); // un secondo rilascio della stessa non libera altro

        assertTrue(limitatore.ammetti(EMAIL).isPresent());
        assertTrue(limitatore.ammetti(EMAIL).isEmpty());
    }

    @Test
    @DisplayName("A mappa piena dimentica l'indirizzo usato meno di recente e non rifiuta mai un indirizzo nuovo")
    void mappaPiena() {
        LimitatoreRichieste piccolo = new LimitatoreRichieste(orologio, MAX, FINESTRA, 2);
        for (int i = 0; i < MAX; i++) {
            piccolo.ammetti(EMAIL);
        }
        assertTrue(piccolo.ammetti(EMAIL).isEmpty());

        assertTrue(piccolo.ammetti("b@picus.test").isPresent());
        assertTrue(piccolo.ammetti("c@picus.test").isPresent(), "indirizzo nuovo a mappa piena");

        // EMAIL era il meno recente: è stato dimenticato e riparte da zero
        assertTrue(piccolo.ammetti(EMAIL).isPresent());
    }

    @Test
    @DisplayName("Venti richieste concorrenti sullo stesso indirizzo: ne passano esattamente tre")
    void richiesteConcorrenti() throws Exception {
        int richieste = 20;
        List<Callable<Boolean>> compiti = new ArrayList<>();
        for (int i = 0; i < richieste; i++) {
            compiti.add(() -> limitatore.ammetti(EMAIL).isPresent());
        }
        ExecutorService pool = Executors.newFixedThreadPool(richieste);
        try {
            CountDownLatch pronti = new CountDownLatch(richieste);
            CountDownLatch via = new CountDownLatch(1);
            List<Future<Boolean>> futuri = new ArrayList<>();
            for (Callable<Boolean> compito : compiti) {
                futuri.add(pool.submit(() -> {
                    pronti.countDown();
                    via.await();
                    return compito.call();
                }));
            }
            assertTrue(pronti.await(10, TimeUnit.SECONDS));
            via.countDown();
            int ammesse = 0;
            for (Future<Boolean> futuro : futuri) {
                if (futuro.get(30, TimeUnit.SECONDS)) {
                    ammesse++;
                }
            }
            assertEquals(MAX, ammesse);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Il bean di produzione applica 3 richieste in 5 minuti")
    void sogliaDelBeanDiProduzione() {
        LimitatoreRichieste bean = new ConfigurazioneLimitatori().limitatoreRecuperoPassword(orologio);
        for (int i = 0; i < MAX; i++) {
            assertTrue(bean.ammetti(EMAIL).isPresent());
        }
        assertTrue(bean.ammetti(EMAIL).isEmpty());
        orologio.imposta(T0.plus(FINESTRA).plus(UN_MS));
        assertTrue(bean.ammetti(EMAIL).isPresent());
    }

    /** Fake manuale dell'orologio: l'istante cambia solo quando il test lo decide. */
    private static final class OrologioFinto extends Clock {
        private volatile Instant istante;

        OrologioFinto(Instant iniziale) {
            this.istante = iniziale;
        }

        void imposta(Instant nuovo) {
            this.istante = nuovo;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return istante;
        }
    }
}