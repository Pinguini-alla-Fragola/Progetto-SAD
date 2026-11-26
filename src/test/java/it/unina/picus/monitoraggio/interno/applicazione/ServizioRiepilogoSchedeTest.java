package it.unina.picus.monitoraggio.interno.applicazione;

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
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServizioRiepilogoSchedeTest {

    private static final IdentitaAutenticata BANCO = new IdentitaAutenticata(UUID.randomUUID(), RuoloUtente.BANCO);
    private static final Trimestre T2 = new Trimestre(2025, 2);
    private static final Trimestre T3 = new Trimestre(2025, 3);
    private static final Instant ISTANTE = Instant.parse("2025-04-01T10:00:00Z");

    private static final VersioneCategorie V_T2 = new VersioneCategorie(
            UUID.randomUUID(), TracciatoMonitoraggio.TM_OP_01, "v1-t2",
            StatoVersioneCategorie.PUBBLICATA, "Versione T2", ISTANTE, ISTANTE,
            List.of(
                    new VoceCategoriaVersione("minori", "Minori", "Minori presi in carico", 0),
                    new VoceCategoriaVersione("famiglie", "Famiglie", "Nuclei familiari", 1)));

    private static final VersioneCategorie V_T3 = new VersioneCategorie(
            UUID.randomUUID(), TracciatoMonitoraggio.TM_OP_01, "v2-t3",
            StatoVersioneCategorie.PUBBLICATA, "Versione T3", ISTANTE, ISTANTE,
            List.of(new VoceCategoriaVersione("famiglie", "Famiglie", "Nuclei familiari", 0)));

    private static final CategorieSchedeRendicontabili PER_TRIMESTRE = trimestre -> switch (trimestre.numero()) {
        case 2 -> new ConfigurazioneTrimestre.Pubblicata(trimestre, V_T2, ISTANTE);
        case 3 -> new ConfigurazioneTrimestre.Pubblicata(trimestre, V_T3, ISTANTE);
        default -> new ConfigurazioneTrimestre.NonConfigurata(trimestre);
    };

    private static final CategorieSchedeRendicontabili LEGACY_V01 = trimestre ->
            new ConfigurazioneTrimestre.LegacyProvvisoria(
                    trimestre,
                    new VersioneCategorie(
                            CategorieSchedeRendicontabili.VERSIONE_LEGACY_V01_ID,
                            TracciatoMonitoraggio.TM_OP_01,
                            "v0.1",
                            StatoVersioneCategorie.LEGACY,
                            "Provvisoria",
                            ISTANTE,
                            ISTANTE,
                            List.of()),
                    ISTANTE);

    @Test
    void usaUltimaVersioneAccettataConLaConfigurazioneDelTrimestreRichiesto() {
        var operatore = UUID.randomUUID();
        var primaVersione = new VersioneInviata(
                new SchedaSintesi(UUID.randomUUID(), operatore, T2, Instant.parse("2025-04-01T09:00:00Z")),
                1, Map.of("minori", 7L, "famiglie", 4L));
        var correzione = new VersioneInviata(
                new SchedaSintesi(primaVersione.sintesi().id(), operatore, T2, Instant.parse("2025-04-05T11:30:00Z")),
                2, Map.of("minori", 9L, "famiglie", 0L));
        var altroOperatore = new VersioneInviata(
                sintesi(T2, "2025-04-02T08:00:00Z"),
                1, Map.of("minori", 0L));
        // Simula invio T2 + correzione dopo la pubblicazione di T3 (che ha ritirato "minori").
        var servizio = new ServizioRiepilogoSchede(trimestre -> List.of(correzione, altroOperatore, primaVersione), PER_TRIMESTRE);

        var riepilogoT2 = servizio.riepilogaSchede(BANCO, T2);

        assertThat(riepilogoT2.schede()).containsExactly(altroOperatore.sintesi(), correzione.sintesi());
        assertThat(riepilogoT2.conteggi()).containsOnlyKeys("minori", "famiglie");
        assertThat(riepilogoT2.conteggi().get("minori")).hasToString("9");
        assertThat(riepilogoT2.conteggi().get("famiglie")).hasToString("0");
    }

    @Test
    void conservaZeroEsplicitoSuCategoriaRitirataDalTrimestreSuccessivo() {
        var soleZero = new VersioneInviata(sintesi(T2, "2025-04-01T10:00:00Z"), 1, Map.of("minori", 0L));
        var soloT3 = new VersioneInviata(sintesi(T3, "2025-07-01T10:00:00Z"), 1, Map.of("minori", 8L, "famiglie", 3L));
        var servizio = new ServizioRiepilogoSchede(
                trimestre -> trimestre.equals(T2) ? List.of(soleZero) : List.of(soloT3),
                PER_TRIMESTRE);

        var riepilogoT2 = servizio.riepilogaSchede(BANCO, T2);
        var riepilogoT3 = servizio.riepilogaSchede(BANCO, T3);

        assertThat(riepilogoT2.conteggi()).isEqualTo(Map.of("minori", BigInteger.ZERO));
        assertThat(riepilogoT3.conteggi()).isEqualTo(Map.of("famiglie", BigInteger.valueOf(3)));
    }

    @Test
    void escludeVociNonConfigurateNelTrimestreConservandoLeSchede() {
        var prima = new VersioneInviata(sintesi(T2, "2025-04-01T10:00:00Z"), 1,
                Map.of("minori", 3L, "fuori_catalogo", 99L));
        var soloFuori = new VersioneInviata(sintesi(T2, "2025-04-02T10:00:00Z"), 1,
                Map.of("disattivata", 12L));
        var servizio = new ServizioRiepilogoSchede(trimestre -> List.of(prima, soloFuori), PER_TRIMESTRE);

        var riepilogo = servizio.riepilogaSchede(BANCO, T2);

        assertThat(riepilogo.schede()).containsExactly(prima.sintesi(), soloFuori.sintesi());
        assertThat(riepilogo.conteggi()).isEqualTo(Map.of("minori", BigInteger.valueOf(3)));
    }

    @Test
    void trimestreNonConfiguratoNonTornaImplicitamentePermissivo() {
        var scheda = new VersioneInviata(sintesi(new Trimestre(2025, 4), "2025-10-01T10:00:00Z"), 1, Map.of("minori", 5L));
        var servizio = new ServizioRiepilogoSchede(
                trimestre -> List.of(scheda),
                ConfigurazioneTrimestre.NonConfigurata::new);

        var riepilogo = servizio.riepilogaSchede(BANCO, new Trimestre(2025, 4));

        assertThat(riepilogo.schede()).containsExactly(scheda.sintesi());
        assertThat(riepilogo.conteggi()).isEmpty();
    }

    @Test
    void associazioneEsplicitaLegacyV01ConservaVociSintatticamenteValide() {
        var scheda = new VersioneInviata(sintesi(T2, "2025-04-01T10:00:00Z"), 1, Map.of("donne", 4L));
        var servizio = new ServizioRiepilogoSchede(trimestre -> List.of(scheda), LEGACY_V01);

        var riepilogo = servizio.riepilogaSchede(BANCO, T2);

        assertThat(riepilogo.conteggi()).isEqualTo(Map.of("donne", BigInteger.valueOf(4)));
    }

    @Test
    void rifiutaProgressivoDuplicatoNelMedesimoPeriodo() {
        var operatore = UUID.randomUUID();
        var s1 = new SchedaSintesi(UUID.randomUUID(), operatore, T2, Instant.parse("2025-04-01T10:00:00Z"));
        var s2 = new SchedaSintesi(UUID.randomUUID(), operatore, T2, Instant.parse("2025-04-02T10:00:00Z"));
        var servizio = new ServizioRiepilogoSchede(trimestre -> List.of(
                new VersioneInviata(s1, 1, Map.of("minori", 1L)),
                new VersioneInviata(s2, 1, Map.of("minori", 2L))), PER_TRIMESTRE);

        assertThatThrownBy(() -> servizio.riepilogaSchede(BANCO, T2))
                .isInstanceOf(IllegalStateException.class);
    }

    private static SchedaSintesi sintesi(Trimestre periodo, String inviataIl) {
        return new SchedaSintesi(UUID.randomUUID(), UUID.randomUUID(), periodo, Instant.parse(inviataIl));
    }
}
