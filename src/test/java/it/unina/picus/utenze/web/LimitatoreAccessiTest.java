package it.unina.picus.utenze.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LimitatoreAccessiTest {

    private static final int MAX_RESIDUI = LimitatoreAccessi.MAX_FALLIMENTI - 1;

    private final OrologioModificabile orologio = new OrologioModificabile();
    private LimitatoreAccessi limitatore;

    @BeforeEach
    void setUp() {
        limitatore = new LimitatoreAccessi(orologio);
    }

    private void tentativi(String chiave, int n) {
        for (int i = 0; i < n; i++) {
            assertThat(limitatore.ammetti(chiave)).as("tentativo %d", i + 1).isTrue();
        }
    }

    @Test
    void ilSestoTentativoEntro60SecondiDalQuintoERifiutato() {
        tentativi("a@picus.test", 5);

        assertThat(limitatore.ammetti("a@picus.test")).isFalse();
        orologio.avanza(Duration.ofSeconds(59));
        assertThat(limitatore.ammetti("a@picus.test")).isFalse();
    }

    @Test
    void dopo60SecondiSiPuoRiprovare() {
        tentativi("a@picus.test", 5);

        orologio.avanza(Duration.ofSeconds(60));

        assertThat(limitatore.ammetti("a@picus.test")).isTrue();
    }

    @Test
    void ilRifiutoNonProlungaLAttesa() {
        tentativi("a@picus.test", 5);
        orologio.avanza(Duration.ofSeconds(40));
        assertThat(limitatore.ammetti("a@picus.test")).isFalse();

        orologio.avanza(Duration.ofSeconds(20)); // 60 s dal quinto
        assertThat(limitatore.ammetti("a@picus.test")).isTrue();
    }

    @Test
    void fallimentiPiuVecchiDiCinqueMinutiNonContano() {
        tentativi("a@picus.test", 4);
        orologio.avanza(Duration.ofMinutes(6));

        tentativi("a@picus.test", 5);
        assertThat(limitatore.ammetti("a@picus.test")).isFalse();
    }

    @Test
    void unAccessoRiuscitoAzzeraIlConteggio() {
        tentativi("a@picus.test", 4);
        limitatore.azzera("a@picus.test");

        tentativi("a@picus.test", 5);
    }

    @Test
    void conLaMappaPienaIlNumeroDiChiaviNonSuperaIlLimite() {
        // orologio fermo: nessuna chiave è scaduta, quindi nessuna può essere eliminata
        int ammesse = 0;
        for (int i = 0; i < LimitatoreAccessi.MAX_CHIAVI + 101; i++) {
            if (limitatore.ammetti("utente" + i + "@picus.test")) {
                ammesse++;
            }
        }

        assertThat(ammesse).isEqualTo(LimitatoreAccessi.MAX_CHIAVI);
        assertThat(limitatore.numeroChiavi()).isEqualTo(LimitatoreAccessi.MAX_CHIAVI);
    }

    @Test
    void conLaMappaPienaUnaChiaveGiaPresenteContinuaAdEssereLimitata() {
        for (int i = 0; i < LimitatoreAccessi.MAX_CHIAVI; i++) {
            limitatore.ammetti("utente" + i + "@picus.test");
        }

        // utente0 ha già 1 tentativo: ne restano 4 prima del blocco, anche a mappa piena
        for (int i = 0; i < MAX_RESIDUI; i++) {
            assertThat(limitatore.ammetti("utente0@picus.test")).isTrue();
        }
        assertThat(limitatore.ammetti("utente0@picus.test")).isFalse();
        assertThat(limitatore.ammetti("nuovo@picus.test")).isFalse();
    }

    @Test
    void passataLaFinestraLeChiaviScaduteLiberanoSpazio() {
        for (int i = 0; i < LimitatoreAccessi.MAX_CHIAVI; i++) {
            limitatore.ammetti("utente" + i + "@picus.test");
        }
        assertThat(limitatore.ammetti("nuovo@picus.test")).isFalse();

        orologio.avanza(Duration.ofMinutes(6));

        assertThat(limitatore.ammetti("nuovo@picus.test")).isTrue();
        assertThat(limitatore.numeroChiavi()).isEqualTo(1);
    }

    @Test
    void ilTentativoSullaCodaScadutaContaAncheDuranteLaPulizia() throws Exception {
        String bersaglio = "bersaglio@picus.test";
        tentativi(bersaglio, 1);
        for (int i = 1; i < LimitatoreAccessi.MAX_CHIAVI; i++) {
            tentativi("vecchia" + i + "@picus.test", 1);
        }
        orologio.avanza(LimitatoreAccessi.FINESTRA.plusSeconds(1));

        var code = (Map<?, ?>) ReflectionTestUtils.getField(limitatore, "tentativi");
        Object codaScaduta = code.get(bersaglio);
        assertThat(codaScaduta).isNotNull();

        var primoTentativo = new FutureTask<>(() -> limitatore.ammetti(bersaglio));
        var thread = Thread.ofPlatform().daemon().unstarted(primoTentativo);
        var threadMxBean = ManagementFactory.getThreadMXBean();
        long titolareMonitor = Thread.currentThread().threadId();
        // Con il precedente lock per coda, il tentativo resta sospeso dopo aver trovato la coda.
        // La pulizia può rimuoverla proprio prima che il tentativo vi scriva.
        synchronized (codaScaduta) {
            thread.start();
            await().atMost(Duration.ofSeconds(5)).until(() -> {
                var info = threadMxBean.getThreadInfo(thread.threadId());
                return primoTentativo.isDone() || (info != null && info.getLockOwnerId() == titolareMonitor);
            });
            assertThat(limitatore.ammetti("nuova@picus.test")).isTrue();
        }

        assertThat(primoTentativo.get(5, TimeUnit.SECONDS)).isTrue();
        tentativi(bersaglio, MAX_RESIDUI);
        assertThat(limitatore.ammetti(bersaglio)).isFalse();
    }

    @Test
    void ogniEmailHaIlSuoLimiteEMaiuscoleSpaziNonContano() {
        tentativi(LimitatoreAccessi.chiave("  A@Picus.test "), 5);

        assertThat(limitatore.ammetti(LimitatoreAccessi.chiave("a@picus.test"))).isFalse();
        assertThat(limitatore.ammetti(LimitatoreAccessi.chiave("b@picus.test"))).isTrue();
    }
}
