package it.unina.picus.monitoraggio.interno.applicazione;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaSintesi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeRendicontabili.VersioneInviata;
import it.unina.picus.monitoraggio.interno.dominio.CategorieSchedeRendicontabili;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestre;
import it.unina.picus.monitoraggio.interno.dominio.StatoVersioneCategorie;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoMonitoraggio;
import it.unina.picus.monitoraggio.interno.dominio.VersioneCategorie;
import it.unina.picus.monitoraggio.interno.dominio.VoceCategoriaVersione;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

class ServizioRiepilogoSchedeTest {

    private static final Trimestre PERIODO = new Trimestre(2025, 1);
    private static final IdentitaAutenticata BANCO = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.BANCO);
    private static final Instant ISTANTE = Instant.parse("2025-04-01T00:00:00Z");
    private static final CategorieSchedeRendicontabili CATEGORIE = trimestre ->
            new ConfigurazioneTrimestre.Pubblicata(
                    trimestre,
                    new VersioneCategorie(
                            UUID.randomUUID(),
                            TracciatoMonitoraggio.TM_OP_01,
                            "v1",
                            StatoVersioneCategorie.PUBBLICATA,
                            "Categorie di prova",
                            ISTANTE,
                            ISTANTE,
                            List.of(
                                    new VoceCategoriaVersione("minori", "Minori", "Definizione", 0),
                                    new VoceCategoriaVersione("famiglie", "Famiglie", "Definizione", 1),
                                    new VoceCategoriaVersione("persone", "Persone", "Definizione", 2))),
                    ISTANTE);

    @Test
    void sommaDueInviiAccettatiOltreIlLimiteDiLong() {
        var primo = new VersioneInviata(sintesi(PERIODO, "2025-04-01T10:00:00Z"), 1,
                Map.of("minori", Long.MAX_VALUE));
        var secondo = new VersioneInviata(sintesi(PERIODO, "2025-04-02T10:00:00Z"), 1,
                Map.of("minori", 1L));
        var servizio = new ServizioRiepilogoSchede(trimestre -> List.of(primo, secondo), CATEGORIE);

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
                trimestre -> List.of(secondaVersione, altroTrimestre, altroOperatore, primaVersione), CATEGORIE);

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
