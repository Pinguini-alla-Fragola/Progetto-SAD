package it.unina.picus.monitoraggio.interno.applicazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaSintesi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeRendicontabili.VersioneInviata;
import it.unina.picus.monitoraggio.interno.dominio.CategorieSchedeRendicontabili;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

class ServizioRiepilogoSchedeTest {

    private static final Trimestre PERIODO = new Trimestre(2025, 1);
    private static final IdentitaAutenticata BANCO = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.BANCO);
    private static final CategorieSchedeRendicontabili CONFIGURATE =
            trimestre -> Optional.of(Set.of("minori", "famiglie", "persone", "mai_dichiarata"));

    @Test
    void sommaDueInviiAccettatiOltreIlLimiteDiLong() {
        var primo = new VersioneInviata(sintesi(PERIODO, "2025-04-01T10:00:00Z"), 1,
                Map.of("minori", Long.MAX_VALUE));
        var secondo = new VersioneInviata(sintesi(PERIODO, "2025-04-02T10:00:00Z"), 1,
                Map.of("minori", 1L));
        var servizio = new ServizioRiepilogoSchede(trimestre -> List.of(primo, secondo), CONFIGURATE);

        var riepilogo = servizio.riepilogaSchede(BANCO, PERIODO);

        assertThat(riepilogo.conteggi()).isEqualTo(Map.of("minori", new BigInteger("9223372036854775808")));
    }

    @Test
    void cumulaSoloLeUltimeVersioniDelPeriodoConservandoLoZeroDichiarato() {
        var primaSintesi = sintesi(PERIODO, "2025-04-01T10:00:00Z");
        var primaVersione = new VersioneInviata(primaSintesi, 1, Map.of("minori", 10L, "persone", 4L));
        var aggiornata = new SchedaSintesi(primaSintesi.id(), primaSintesi.operatoreId(), PERIODO,
                Instant.parse("2025-04-03T10:00:00Z"));
        var secondaVersione = new VersioneInviata(aggiornata, 2, Map.of("minori", 2L, "famiglie", 0L));
        var altroOperatore = new VersioneInviata(sintesi(PERIODO, "2025-04-02T10:00:00Z"), 1,
                Map.of("minori", 3L));
        var altroTrimestre = new VersioneInviata(sintesi(new Trimestre(2025, 2), "2025-07-01T10:00:00Z"), 1,
                Map.of("minori", 100L));
        var servizio = new ServizioRiepilogoSchede(
                trimestre -> List.of(secondaVersione, altroTrimestre, altroOperatore, primaVersione), CONFIGURATE);

        var riepilogo = servizio.riepilogaSchede(BANCO, PERIODO);

        assertThat(riepilogo.schede()).containsExactly(altroOperatore.sintesi(), aggiornata);
        assertThat(riepilogo.conteggi()).containsOnlyKeys("minori", "famiglie");
        assertThat(riepilogo.conteggi().get("minori")).hasToString("5");
        assertThat(riepilogo.conteggi().get("famiglie")).hasToString("0");
    }

    @Test
    void escludeVociNonConfigurateConservandoLeSchedeNelRiepilogo() {
        var prima = new VersioneInviata(sintesi(PERIODO, "2025-04-01T10:00:00Z"), 1,
                Map.of("minori", 3L, "fuori_catalogo", 99L));
        var soloFuori = new VersioneInviata(sintesi(PERIODO, "2025-04-02T10:00:00Z"), 1,
                Map.of("disattivata", 12L));
        var servizio = new ServizioRiepilogoSchede(trimestre -> List.of(prima, soloFuori), CONFIGURATE);

        var riepilogo = servizio.riepilogaSchede(BANCO, PERIODO);

        assertThat(riepilogo.schede()).containsExactly(prima.sintesi(), soloFuori.sintesi());
        assertThat(riepilogo.conteggi()).isEqualTo(Map.of("minori", BigInteger.valueOf(3)));
    }

    @Test
    void escludeTutteLeVociSeOgniCategoriaCensitaEDisattivata() {
        var scheda = new VersioneInviata(sintesi(PERIODO, "2025-04-01T10:00:00Z"), 1, Map.of("minori", 5L));
        var servizio = new ServizioRiepilogoSchede(trimestre -> List.of(scheda), trimestre -> Optional.of(Set.of()));

        var riepilogo = servizio.riepilogaSchede(BANCO, PERIODO);

        assertThat(riepilogo.schede()).containsExactly(scheda.sintesi());
        assertThat(riepilogo.conteggi()).isEmpty();
    }

    @Test
    void applicaIlTracciatoProvvisorioSoloQuandoMancaQualsiasiConfigurazione() {
        var scheda = new VersioneInviata(sintesi(PERIODO, "2025-04-01T10:00:00Z"), 1, Map.of("donne", 4L));
        var servizio = new ServizioRiepilogoSchede(trimestre -> List.of(scheda), trimestre -> Optional.empty());

        var riepilogo = servizio.riepilogaSchede(BANCO, PERIODO);

        assertThat(riepilogo.conteggi()).isEqualTo(Map.of("donne", BigInteger.valueOf(4)));
    }

    @Test
    void rifiutaProgressivoDuplicatoNelMedesimoPeriodo() {
        var operatore = UUID.randomUUID();
        var s1 = new SchedaSintesi(UUID.randomUUID(), operatore, PERIODO, Instant.parse("2025-04-01T10:00:00Z"));
        var s2 = new SchedaSintesi(UUID.randomUUID(), operatore, PERIODO, Instant.parse("2025-04-02T10:00:00Z"));
        var servizio = new ServizioRiepilogoSchede(trimestre -> List.of(
                new VersioneInviata(s1, 1, Map.of("minori", 1L)),
                new VersioneInviata(s2, 1, Map.of("minori", 2L))), CONFIGURATE);

        assertThatThrownBy(() -> servizio.riepilogaSchede(BANCO, PERIODO))
                .isInstanceOf(IllegalStateException.class);
    }

    private static SchedaSintesi sintesi(Trimestre periodo, String inviataIl) {
        return new SchedaSintesi(UUID.randomUUID(), UUID.randomUUID(), periodo, Instant.parse(inviataIl));
    }
}
