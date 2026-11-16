package it.unina.picus.richieste.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.unina.picus.shared.AccessiBlackBoxTest;
import it.unina.picus.shared.ScenarioAccessi;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.EsitoContatto;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * #28 — CA-05 e CA-06: il Banco di coordinamento consulta richieste, disponibilità e schede necessarie alle
 * funzioni autorizzate. Controllo complementare: le stesse funzioni sono negate a OPT e operatori,
 * perché l'account Operatore interno non equivale al ruolo Banco.
 *
 * <p>Tecniche: matrice dei permessi ruolo × operazione; la OPT titolare è provata a parte, perché è la
 * classe di attori più vicina ai permessi del Banco sulla propria richiesta.</p>
 *
 * <p>La classe sta in {@code richieste.api} perché verifica soprattutto coda, dettaglio, contatti e
 * archivio; contiene anche alcune verifiche su schede e conteggi di Monitoraggio.</p>
 */
@DisplayName("#28 CA-05/CA-06 · Funzioni di coordinamento e rendicontazione del Banco")
class BancoCoordinamentoAccessiTest extends AccessiBlackBoxTest {

    @Test
    @DisplayName("CA-05 · il Banco consulta la coda e il dettaglio completo della richiesta")
    void bancoConsultaCodaEDettaglio() {
        Account titolare = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);
        IdentitaAutenticata banco = scenario.banco().identita();

        assertTrue(scenario.richieste().codaBanco(banco).stream().map(RichiestaSintesi::id).anyMatch(id::equals));
        RichiestaDettaglio dettaglio = scenario.richieste().consultaRichiesta(banco, id);
        assertEquals(titolare.id(), dettaglio.titolareId());
        assertEquals(ScenarioAccessi.DESCRIZIONE, dettaglio.description());
    }

    @Test
    @DisplayName("CA-05 · il Banco consulta contatti e risposte di disponibilità")
    void bancoConsultaDisponibilita() {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(TipoErogatore.OPT);
        ContattoDisponibilita contatto = scenario.richiestaConContatto(titolare, interpellato);
        scenario.richieste().rispondiDisponibilita(interpellato.identita(), contatto.id(), false);

        var contatti = scenario.richieste().elencoContatti(scenario.banco().identita(), contatto.richiestaId());

        assertTrue(contatti.stream().anyMatch(c -> c.id().equals(contatto.id())
                && c.erogatoreId().equals(interpellato.id()) && c.esito() == EsitoContatto.NON_DISPONIBILE));
    }

    @Test
    @DisplayName("CA-05 · il Banco consulta l'archivio delle richieste con esito")
    void bancoConsultaArchivio() {
        UUID id = scenario.richiestaRifiutata(scenario.optRichiedente());

        assertTrue(scenario.richieste().archivioBanco(scenario.banco().identita()).stream()
                .map(RichiestaSintesi::id).anyMatch(id::equals));
    }

    @ParameterizedTest(name = "CA-05 · il Banco consulta la scheda di un operatore {0} e il riepilogo")
    @EnumSource(value = TipoErogatore.class, names = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    void bancoConsultaSchede(TipoErogatore tipologia) {
        Account operatore = scenario.operatore(tipologia);
        UUID schedaId = scenario.schedaInviata(operatore);
        IdentitaAutenticata banco = scenario.banco().identita();

        assertEquals(operatore.id(),
                scenario.monitoraggio().consultaSchedaOperatore(banco, schedaId).sintesi().operatoreId());
        assertTrue(scenario.monitoraggio().riepilogaSchede(banco, ScenarioAccessi.TRIMESTRE).schede().stream()
                .anyMatch(s -> s.id().equals(schedaId)));
    }

    @Test
    @DisplayName("CA-05 · il Banco consulta i conteggi di una OPT e di tutte le OPT")
    void bancoConsultaConteggi() {
        Account opt = scenario.optRichiedente();
        IdentitaAutenticata banco = scenario.banco().identita();
        var trimestre = ScenarioAccessi.TRIMESTRE;

        scenario.monitoraggio().consultaConteggiOpt(banco, opt.id(), trimestre, trimestre);
        scenario.monitoraggio().consultaConteggiOpt(banco, null, trimestre, trimestre);
    }

    /** Ruoli diversi dal Banco, inclusi gli operatori interni. */
    enum NonBanco { OPT, OPERATORE_INTERNO, OPERATORE_ESTERNO }

    private Account account(NonBanco ruolo) {
        return switch (ruolo) {
            case OPT -> scenario.optRichiedente();
            case OPERATORE_INTERNO -> scenario.operatore(TipoErogatore.INTERNO_BANCO);
            case OPERATORE_ESTERNO -> scenario.operatore(TipoErogatore.ESTERNO_PAGATO_BANCO);
        };
    }

    @ParameterizedTest(name = "CA-06 · presa in carico, ricerca, contatti e rifiuto definitivo negati a {0}")
    @EnumSource(NonBanco.class)
    void coordinamentoNegatoAgliAltriRuoli(NonBanco ruolo) {
        Account attore = account(ruolo);
        Account erogatore = scenario.erogatore(TipoErogatore.OPT);
        UUID creata = scenario.richiestaCreata(scenario.optRichiedente());
        UUID presaInCarico = scenario.richiestaPresaInCarico(scenario.optRichiedente());
        IdentitaAutenticata identita = attore.identita();

        assertNegato(() -> scenario.richieste().codaBanco(identita));
        assertNegato(() -> scenario.richieste().archivioBanco(identita));
        assertNegatoSenzaModifiche(creata, () -> scenario.richieste().prendiInCarico(identita, creata));
        assertNegato(() -> scenario.richieste().cercaErogatoriOrdinati(identita, presaInCarico));
        assertNegatoSenzaModifiche(presaInCarico, () -> scenario.richieste()
                .interpellaErogatore(identita, presaInCarico, erogatore.id(), AmbitoErogazione.COMUNE_LIMITROFO));
        assertNegato(() -> scenario.richieste().elencoContatti(identita, presaInCarico));
        assertNegatoSenzaModifiche(presaInCarico, () -> scenario.richieste().rifiutaRichiesta(identita, presaInCarico));
    }

    @Test
    @DisplayName("CA-06 · la OPT titolare non prende in carico né rifiuta la propria richiesta")
    void titolareNonCoordinaLaPropriaRichiesta() {
        Account titolare = scenario.optRichiedente();
        UUID creata = scenario.richiestaCreata(titolare);
        UUID presaInCarico = scenario.richiestaPresaInCarico(titolare);
        UUID inProgrammazione = scenario.richiestaInProgrammazione(titolare, scenario.erogatore(TipoErogatore.OPT));
        IdentitaAutenticata identita = titolare.identita();

        assertNegatoSenzaModifiche(creata, () -> scenario.richieste().prendiInCarico(identita, creata));
        assertNegatoSenzaModifiche(presaInCarico, () -> scenario.richieste().rifiutaRichiesta(identita, presaInCarico));
        assertNegatoSenzaModifiche(inProgrammazione,
                () -> scenario.richieste().rifiutaRichiesta(identita, inProgrammazione));
    }

    @ParameterizedTest(name = "CA-06 · operatore {0} non assegnato non rifiuta una richiesta In programmazione")
    @EnumSource(value = TipoErogatore.class, names = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    void operatoreNonRifiutaInProgrammazione(TipoErogatore tipologia) {
        Account operatore = scenario.operatore(tipologia);
        UUID id = scenario.richiestaInProgrammazione(scenario.optRichiedente(), scenario.erogatore(TipoErogatore.OPT));

        assertNegatoSenzaModifiche(id, () -> scenario.richieste().rifiutaRichiesta(operatore.identita(), id));
    }

    @ParameterizedTest(name = "CA-05 · rendicontazione e catalogo completo negati a {0}")
    @EnumSource(NonBanco.class)
    void rendicontazioneNegataAgliAltriRuoli(NonBanco ruolo) {
        IdentitaAutenticata identita = account(ruolo).identita();

        assertNegato(() -> scenario.monitoraggio().riepilogaSchede(identita, ScenarioAccessi.TRIMESTRE));
        assertNegato(() -> scenario.catalogo().elencoMisure(identita));
    }
}
