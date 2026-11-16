package it.unina.picus.monitoraggio.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import it.unina.picus.shared.AccessiBlackBoxTest;
import it.unina.picus.shared.ScenarioAccessi;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.DatiSchedaOperatore;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaOperatore;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaSintesi;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * #28 — CA-04: ogni operatore opera solo sulla propria scheda personale; la OPT non legge
 * né invia schede personali.
 *
 * <p>Tecniche: coppie complete di tipologie operatore/collega (interno ed esterno), classe OPT e un
 * caso di parametri alterati (CA-09): la scheda con i dati di un collega resta di chi la invia.</p>
 */
@DisplayName("#28 CA-04 · Schede personali degli operatori del Banco")
class SchedePersonaliAccessiTest extends AccessiBlackBoxTest {

    @ParameterizedTest(name = "CA-04 · l'operatore {0} invia e consulta la propria scheda")
    @EnumSource(value = TipoErogatore.class, names = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    void titolareOperaSullaPropriaScheda(TipoErogatore tipologia) {
        Account operatore = scenario.operatore(tipologia);
        DatiSchedaOperatore dati = ScenarioAccessi.datiScheda("Operatore", "PRVOPR80A01F839A");

        UUID schedaId = scenario.monitoraggio().inviaSchedaOperatore(operatore.identita(), ScenarioAccessi.TRIMESTRE, dati);

        SchedaOperatore scheda = scenario.monitoraggio().consultaSchedaOperatore(operatore.identita(), schedaId);
        assertEquals(operatore.id(), scheda.sintesi().operatoreId());
        assertEquals(dati, scheda.dati());
        assertTrue(ids(scenario.monitoraggio().elencoSchedeOperatore(operatore.identita())).contains(schedaId));
    }

    static Stream<Arguments> coppieDiColleghi() {
        var interno = TipoErogatore.INTERNO_BANCO;
        var esterno = TipoErogatore.ESTERNO_PAGATO_BANCO;
        return Stream.of(Arguments.of(interno, esterno), Arguments.of(esterno, interno),
                Arguments.of(interno, interno), Arguments.of(esterno, esterno));
    }

    @ParameterizedTest(name = "CA-04 · la scheda di un collega {1} resta riservata all'operatore {0}")
    @MethodSource("coppieDiColleghi")
    void schedaDelCollegaRiservata(TipoErogatore tipologia, TipoErogatore tipologiaCollega) {
        Account operatore = scenario.operatore(tipologia);
        Account collega = scenario.operatore(tipologiaCollega);
        scenario.schedaInviata(operatore);
        UUID schedaCollega = scenario.schedaInviata(collega);

        assertNegato(() -> scenario.monitoraggio().consultaSchedaOperatore(operatore.identita(), schedaCollega));
        assertFalse(ids(scenario.monitoraggio().elencoSchedeOperatore(operatore.identita())).contains(schedaCollega));
    }

    @Test
    @DisplayName("CA-04/CA-09 · una scheda inviata con i dati di un collega resta dell'account che la invia")
    void titolareDellaSchedaRicavatoDallaSessione() {
        Account operatore = scenario.operatore(TipoErogatore.INTERNO_BANCO);
        Account collega = scenario.operatore(TipoErogatore.ESTERNO_PAGATO_BANCO);

        UUID schedaId = scenario.monitoraggio().inviaSchedaOperatore(operatore.identita(),
                ScenarioAccessi.TRIMESTRE, ScenarioAccessi.datiScheda("Collega", "PRVCLG80A01F839B"));

        assertEquals(operatore.id(),
                scenario.monitoraggio().consultaSchedaOperatore(operatore.identita(), schedaId).sintesi().operatoreId());
        assertFalse(ids(scenario.monitoraggio().elencoSchedeOperatore(collega.identita())).contains(schedaId));
        assertNegato(() -> scenario.monitoraggio().consultaSchedaOperatore(collega.identita(), schedaId));
    }

    @Test
    @DisplayName("CA-04 · la OPT non legge una scheda personale, neppure con un identificativo valido")
    void optNonLeggeLaScheda() {
        Account opt = scenario.optRichiedente();
        UUID scheda = scenario.schedaInviata(scenario.operatore(TipoErogatore.ESTERNO_PAGATO_BANCO));

        assertNegato(() -> scenario.monitoraggio().consultaSchedaOperatore(opt.identita(), scheda));
        assertNegato(() -> scenario.monitoraggio().elencoSchedeOperatore(opt.identita()));
    }

    @Test
    @DisplayName("CA-04 · la OPT non invia schede personali e nulla viene registrato")
    void optNonInviaScheda() {
        Account opt = scenario.optRichiedente();
        var trimestre = ScenarioAccessi.TRIMESTRE;
        List<SchedaSintesi> prima = scenario.monitoraggio().riepilogaSchede(scenario.banco().identita(), trimestre).schede();

        assertNegato(() -> scenario.monitoraggio().inviaSchedaOperatore(opt.identita(), trimestre,
                ScenarioAccessi.datiScheda("OPT", "PRVOPT80A01F839C")));

        List<SchedaSintesi> dopo = scenario.monitoraggio().riepilogaSchede(scenario.banco().identita(), trimestre).schede();
        assertEquals(prima.size(), dopo.size(), "il diniego non deve registrare schede");
        assertFalse(dopo.stream().anyMatch(s -> s.operatoreId().equals(opt.id())));
    }

    private static List<UUID> ids(List<SchedaSintesi> schede) {
        return schede.stream().map(SchedaSintesi::id).toList();
    }
}
