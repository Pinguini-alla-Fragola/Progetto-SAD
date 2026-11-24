package it.unina.picus.monitoraggio.interno.applicazione;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaSintesi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeRendicontabili.VersioneInviata;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

class ServizioRiepilogoSchedeTest {

    private static final Trimestre PERIODO = new Trimestre(2025, 1);
    private static final IdentitaAutenticata BANCO = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.BANCO);
    private static final Set<String> CATEGORIE = Set.of("minori", "famiglie", "persone");

    @Test
    void sommaDueInviiAccettatiOltreIlLimiteDiLong() {
        var primo = new VersioneInviata(sintesi(PERIODO, "2025-04-01T10:00:00Z"), 1,
                Map.of("minori", Long.MAX_VALUE), CATEGORIE);
        var secondo = new VersioneInviata(sintesi(PERIODO, "2025-04-02T10:00:00Z"), 1,
                Map.of("minori", 1L), CATEGORIE);
        var servizio = new ServizioRiepilogoSchede(trimestre -> List.of(primo, secondo));

        var riepilogo = servizio.riepilogaSchede(BANCO, PERIODO);

        assertThat(riepilogo.conteggi()).isEqualTo(Map.of("minori", new BigInteger("9223372036854775808")));
    }

    @Test
    void cumulaSoloLeUltimeVersioniDelPeriodoConservandoLoZeroDichiarato() {
        var primaSintesi = sintesi(PERIODO, "2025-04-01T10:00:00Z");
        var primaVersione = new VersioneInviata(primaSintesi, 1, Map.of("minori", 10L, "persone", 4L), CATEGORIE);
        var aggiornata = new SchedaSintesi(primaSintesi.id(), primaSintesi.operatoreId(), PERIODO,
                Instant.parse("2025-04-03T10:00:00Z"));
        var secondaVersione = new VersioneInviata(aggiornata, 2, Map.of("minori", 2L, "famiglie", 0L), CATEGORIE);
        var altroOperatore = new VersioneInviata(sintesi(PERIODO, "2025-04-02T10:00:00Z"), 1,
                Map.of("minori", 3L), CATEGORIE);
        var altroTrimestre = new VersioneInviata(sintesi(new Trimestre(2025, 2), "2025-07-01T10:00:00Z"), 1,
                Map.of("minori", 100L), CATEGORIE);
        var servizio = new ServizioRiepilogoSchede(
                trimestre -> List.of(secondaVersione, altroTrimestre, altroOperatore, primaVersione));

        var riepilogo = servizio.riepilogaSchede(BANCO, PERIODO);

        assertThat(riepilogo.schede()).containsExactly(altroOperatore.sintesi(), aggiornata);
        assertThat(riepilogo.conteggi()).containsOnlyKeys("minori", "famiglie");
        assertThat(riepilogo.conteggi().get("minori")).hasToString("5");
        assertThat(riepilogo.conteggi().get("famiglie")).hasToString("0");
    }

    private static SchedaSintesi sintesi(Trimestre periodo, String inviataIl) {
        return new SchedaSintesi(UUID.randomUUID(), UUID.randomUUID(), periodo, Instant.parse(inviataIl));
    }
}
