package it.unina.picus.richieste.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * #28 — CA-01 (OPT titolare e OPT estranea) e CA-09 (identità e permessi ricavati dal server).
 *
 * <p>Tecniche: classi di equivalenza titolare/estranea sulla stessa risorsa (richiesta, elenco,
 * offerta, conteggi), accesso diretto con identificativo altrui e controllo positivo per la titolare.</p>
 */
@DisplayName("#28 CA-01/CA-09 · Titolarità delle richieste e dati della OPT")
class TitolaritaRichiesteAccessiTest extends AccessiBlackBoxTest {

    @Test
    @DisplayName("CA-01 · la OPT titolare consulta la propria richiesta")
    void titolareConsultaLaPropriaRichiesta() {
        Account titolare = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);

        RichiestaDettaglio dettaglio = scenario.richieste().consultaRichiesta(titolare.identita(), id);

        assertEquals(id, dettaglio.richiesta().id());
        assertEquals(titolare.id(), dettaglio.titolareId());
        assertEquals(StatoRichiesta.CREATA, dettaglio.richiesta().stato());
        assertEquals(ScenarioAccessi.DESCRIZIONE, dettaglio.description(), "la descrizione è visibile al titolare");
    }

    @Test
    @DisplayName("CA-01 · la OPT estranea che usa l'identificativo altrui riceve FORBIDDEN senza dati")
    void optEstraneaRiceveForbiddenSullaRichiestaAltrui() {
        Account titolare = scenario.optRichiedente();
        Account estranea = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);

        assertNegatoSenzaModifiche(id, () -> scenario.richieste().consultaRichiesta(estranea.identita(), id));
        // stessa risorsa, account titolare: resta consultabile
        assertEquals(id, scenario.richieste().consultaRichiesta(titolare.identita(), id).richiesta().id());
    }

    @Test
    @DisplayName("CA-01 · la richiesta compare solo nell'elenco della OPT titolare")
    void richiestaSoloNellElencoDelTitolare() {
        Account titolare = scenario.optRichiedente();
        Account estranea = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);

        assertTrue(scenario.richieste().elencoRichiesteOpt(titolare.identita()).stream()
                .map(RichiestaSintesi::id).anyMatch(id::equals));
        assertFalse(scenario.richieste().elencoRichiesteOpt(estranea.identita()).stream()
                .map(RichiestaSintesi::id).anyMatch(id::equals), "la OPT estranea non deve vedere la richiesta");
    }

    @Test
    @DisplayName("CA-01 · la OPT estranea non modifica la richiesta altrui")
    void optEstraneaNonAnnullaLaRichiestaAltrui() {
        Account titolare = scenario.optRichiedente();
        Account estranea = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);

        assertNegatoSenzaModifiche(id,
                () -> scenario.richieste().annullaRichiesta(estranea.identita(), id, "Motivo di prova"));
    }

    @Test
    @DisplayName("CA-01 · l'offerta di una OPT è consultabile dalla titolare e negata all'estranea")
    void offertaOptRiservataAllaTitolare() {
        Account titolare = scenario.erogatore(TipoErogatore.OPT);
        Account estranea = scenario.optRichiedente();

        assertFalse(scenario.catalogo().elencoOffertaOpt(titolare.identita(), titolare.id()).isEmpty());
        assertNegato(() -> scenario.catalogo().elencoOffertaOpt(estranea.identita(), titolare.id()));
    }

    @Test
    @DisplayName("CA-01 · i conteggi di una OPT sono negati all'estranea, anche chiedendo tutte le OPT")
    void conteggiOptRiservatiAllaTitolare() {
        Account titolare = scenario.optRichiedente();
        Account estranea = scenario.optRichiedente();
        var trimestre = ScenarioAccessi.TRIMESTRE;

        scenario.monitoraggio().consultaConteggiOpt(titolare.identita(), titolare.id(), trimestre, trimestre);
        assertNegato(() -> scenario.monitoraggio()
                .consultaConteggiOpt(estranea.identita(), titolare.id(), trimestre, trimestre));
        assertNegato(() -> scenario.monitoraggio()
                .consultaConteggiOpt(estranea.identita(), null, trimestre, trimestre));
    }

    @Test
    @DisplayName("CA-09 · il titolare della richiesta è l'account che la invia, non un dato del client")
    void titolareRicavatoDallIdentitaVerificata() {
        Account titolare = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);

        assertEquals(titolare.id(), scenario.istantanea(id).titolareId());
    }

    @ParameterizedTest(name = "CA-09 · ruolo {0} ricavato dai dati persistiti")
    @EnumSource(RuoloUtente.class)
    @DisplayName("CA-09 · la sessione è confermata con il ruolo persistito sul server")
    void ruoloRicavatoDalServer(RuoloUtente ruolo) {
        Account account = switch (ruolo) {
            case BANCO -> scenario.banco();
            case OPT -> scenario.optRichiedente();
            case OPERATORE -> scenario.operatore(TipoErogatore.INTERNO_BANCO);
        };

        IdentitaAutenticata sessione = scenario.utenze().verificaAccountSessione(account.id()).orElseThrow();

        assertEquals(new IdentitaAutenticata(account.id(), ruolo), sessione);
    }

    @Test
    @DisplayName("CA-09 · un identificativo di account inesistente non ottiene un'identità")
    void accountSconosciutoSenzaIdentita() {
        assertTrue(scenario.utenze().verificaAccountSessione(UUID.randomUUID()).isEmpty());
    }
}
