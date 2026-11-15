package it.unina.picus.utenze.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LimitatoreAccessiTest {

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
    void ogniEmailHaIlSuoLimiteEMaiuscoleSpaziNonContano() {
        tentativi(LimitatoreAccessi.chiave("  A@Picus.test "), 5);

        assertThat(limitatore.ammetti(LimitatoreAccessi.chiave("a@picus.test"))).isFalse();
        assertThat(limitatore.ammetti(LimitatoreAccessi.chiave("b@picus.test"))).isTrue();
    }
}
