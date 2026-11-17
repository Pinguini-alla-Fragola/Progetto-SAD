package it.unina.picus.richieste.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.unina.picus.shared.AccessiBlackBoxTest;
import it.unina.picus.shared.ScenarioAccessi;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * #28 — CA-03 (l'assegnatario vede solo le proprie assegnazioni) e CA-06 (conferma e rifiuto finale
 * riservati al Banco di coordinamento).
 *
 * <p>Tecniche: classi di equivalenza assegnato/collega/titolare/operatore estraneo per ciascuna
 * tipologia di erogatore; i dati nascosti all'assegnatario (titolare e descrizione) seguono il contratto
 * {@code RichiestaDettaglio}. Il controllo positivo è la conferma da parte del Banco.</p>
 */
@DisplayName("#28 CA-03/CA-06 · Assegnazioni e conferma dell'accordo")
class AssegnazioniAccessiTest extends AccessiBlackBoxTest {

    @ParameterizedTest(name = "CA-03 · {0} assegnato consulta i dettagli della propria assegnazione")
    @EnumSource(TipoErogatore.class)
    void assegnatoConsultaLaPropriaAssegnazione(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account assegnato = scenario.erogatore(tipo);
        UUID id = scenario.richiestaConfermata(titolare, assegnato);

        RichiestaDettaglio dettaglio = scenario.invio().consultaRichiesta(assegnato.identita(), id);

        assertEquals(StatoRichiesta.CONFERMATA, dettaglio.richiesta().stato());
        assertEquals(assegnato.id(), dettaglio.erogatoreAssegnatoId());
        assertEquals(ScenarioAccessi.ACCORDO, dettaglio.accordo(), "l'assegnatario vede i dettagli dell'accordo");
        assertNull(dettaglio.titolareId(), "il titolare è visibile solo a titolare e Banco");
        assertNull(dettaglio.description(), "la descrizione è riservata a titolare e Banco");
    }

    @ParameterizedTest(name = "CA-03 · {0} assegnato non accede ad assegnazioni estranee")
    @EnumSource(TipoErogatore.class)
    void assegnatoNonAccedeAdAssegnazioniEstranee(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account assegnato = scenario.erogatore(tipo);
        Account collega = scenario.erogatore(tipo);
        scenario.richiestaConfermata(titolare, assegnato);
        UUID estranea = scenario.richiestaConfermata(titolare, collega);

        assertNegatoSenzaModifiche(estranea,
                () -> scenario.invio().consultaRichiesta(assegnato.identita(), estranea));
    }

    @ParameterizedTest(name = "CA-03 · {0} assegnato non accede alle altre pratiche della OPT richiedente")
    @EnumSource(TipoErogatore.class)
    void assegnatoNonAccedeAlleAltrePraticheDellaRichiedente(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account assegnato = scenario.erogatore(tipo);
        scenario.richiestaConfermata(titolare, assegnato);
        UUID altraCreata = scenario.richiestaCreata(titolare);
        UUID altraPresaInCarico = scenario.richiestaPresaInCarico(titolare);

        for (UUID altra : new UUID[] {altraCreata, altraPresaInCarico}) {
            assertNegatoSenzaModifiche(altra, () -> scenario.invio().consultaRichiesta(assegnato.identita(), altra));
        }
    }

    @ParameterizedTest(name = "CA-03 · l''elenco delle assegnazioni di {0} contiene solo le proprie")
    @EnumSource(TipoErogatore.class)
    void elencoAssegnazioniContieneSoloLeProprie(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account assegnato = scenario.erogatore(tipo);
        Account collega = scenario.erogatore(tipo);
        UUID propria = scenario.richiestaConfermata(titolare, assegnato);
        UUID delCollega = scenario.richiestaConfermata(titolare, collega);
        UUID altraDellaRichiedente = scenario.richiestaPresaInCarico(titolare);

        List<UUID> elenco = scenario.richieste().elencoAssegnazioni(assegnato.identita()).stream()
                .map(RichiestaSintesi::id).toList();

        assertTrue(elenco.contains(propria), "la propria assegnazione compare");
        assertFalse(elenco.contains(delCollega), "l'assegnazione del collega non compare");
        assertFalse(elenco.contains(altraDellaRichiedente), "le altre pratiche della richiedente non compaiono");
    }

    @ParameterizedTest(name = "CA-06 · {0} assegnato non conferma l'accordo")
    @EnumSource(TipoErogatore.class)
    void erogatoreAssegnatoNonConferma(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account assegnato = scenario.erogatore(tipo);
        UUID id = scenario.richiestaInProgrammazione(titolare, assegnato);

        assertNegatoSenzaModifiche(id, () -> scenario.coordinamento()
                .confermaAccordo(assegnato.identita(), id, ScenarioAccessi.ACCORDO));
        RichiestaDettaglio dopo = scenario.istantanea(id);
        assertEquals(StatoRichiesta.IN_PROGRAMMAZIONE, dopo.richiesta().stato());
        assertNull(dopo.accordo());
    }

    @ParameterizedTest(name = "CA-06 · né la OPT titolare né un operatore {0} estraneo confermano l'accordo")
    @EnumSource(value = TipoErogatore.class, names = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    void altriAccountNonConfermano(TipoErogatore tipologia) {
        Account titolare = scenario.optRichiedente();
        Account operatoreEstraneo = scenario.operatore(tipologia);
        UUID id = scenario.richiestaInProgrammazione(titolare, scenario.erogatore(TipoErogatore.OPT));

        assertNegatoSenzaModifiche(id, () -> scenario.coordinamento()
                .confermaAccordo(titolare.identita(), id, ScenarioAccessi.ACCORDO));
        assertNegatoSenzaModifiche(id, () -> scenario.coordinamento()
                .confermaAccordo(operatoreEstraneo.identita(), id, ScenarioAccessi.ACCORDO));
    }

    @ParameterizedTest(name = "CA-06 · {0} assegnato non registra il rifiuto finale")
    @EnumSource(TipoErogatore.class)
    void erogatoreNonRifiutaDefinitivamente(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account assegnato = scenario.erogatore(tipo);
        UUID id = scenario.richiestaInProgrammazione(titolare, assegnato);

        assertNegatoSenzaModifiche(id, () -> scenario.coordinamento().rifiutaRichiesta(assegnato.identita(), id));
    }

    @Test
    @DisplayName("CA-06 · controllo positivo: il Banco di coordinamento conferma l'accordo")
    void bancoConfermaLAccordo() {
        Account titolare = scenario.optRichiedente();
        UUID id = scenario.richiestaInProgrammazione(titolare, scenario.erogatore(TipoErogatore.OPT));

        RichiestaDettaglio confermata = scenario.coordinamento()
                .confermaAccordo(scenario.banco().identita(), id, ScenarioAccessi.ACCORDO);

        assertEquals(StatoRichiesta.CONFERMATA, confermata.richiesta().stato());
        assertEquals(ScenarioAccessi.ACCORDO, confermata.accordo());
    }
}
