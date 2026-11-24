package it.unina.picus.richieste.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import it.unina.picus.monitoraggio.api.ConteggiOptApi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi;
import it.unina.picus.richieste.api.RichiesteApi;
import it.unina.picus.shared.AccessiBlackBoxTest;
import it.unina.picus.shared.ScenarioAccessi;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.ConteggioOpt;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.VoceMisura;
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
 * <p>Tecniche: matrice azione × ruolo per le tre azioni di CA-06 (presa in carico, conferma
 * dell'accordo, rifiuto definitivo), ciascuna sul proprio stato minimo e con il controllo positivo del
 * Banco; la OPT titolare è provata a parte, perché è la classe di attori più vicina ai permessi del
 * Banco sulla propria richiesta.</p>
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

        assertTrue(scenario.invio().codaBanco(banco).stream().map(RichiestaSintesi::id).anyMatch(id::equals));
        RichiestaDettaglio dettaglio = scenario.invio().consultaRichiesta(banco, id);
        assertEquals(titolare.id(), dettaglio.titolareId());
        assertEquals(ScenarioAccessi.DESCRIZIONE, dettaglio.description());
    }

    @Test
    @DisplayName("CA-05 · il Banco consulta contatti e risposte di disponibilità")
    void bancoConsultaDisponibilita() {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(TipoErogatore.OPT);
        ContattoDisponibilita contatto = scenario.richiestaConContatto(titolare, interpellato);
        scenario.contatti().rispondiDisponibilita(interpellato, contatto.id(), false);

        var contatti = scenario.contatti().elencoContatti(scenario.banco(), contatto.richiestaId());

        assertTrue(contatti.stream().anyMatch(c -> c.id().equals(contatto.id())
                && c.erogatoreId().equals(interpellato.id()) && c.esito() == EsitoContatto.NON_DISPONIBILE));
    }

    @Test
    @DisplayName("CA-05 · il Banco consulta l'archivio delle richieste con esito")
    void bancoConsultaArchivio() {
        RichiesteApi richieste = scenario.richieste(); // prima di preparare dati: sospensione immediata
        UUID id = scenario.richiestaRifiutata(scenario.optRichiedente());

        assertTrue(richieste.archivioBanco(scenario.banco().identita()).stream()
                .map(RichiestaSintesi::id).anyMatch(id::equals));
    }

    @ParameterizedTest(name = "CA-05 · il Banco consulta la scheda di un operatore {0} e il riepilogo")
    @EnumSource(value = TipoErogatore.class, names = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    void bancoConsultaSchede(TipoErogatore tipologia) {
        MonitoraggioApi monitoraggio = scenario.monitoraggio(); // prima di preparare dati: sospensione immediata
        Account operatore = scenario.operatore(tipologia);
        UUID schedaId = scenario.schedaInviata(operatore);
        IdentitaAutenticata banco = scenario.banco().identita();

        assertEquals(operatore.id(),
                monitoraggio.consultaSchedaOperatore(banco, schedaId).sintesi().operatoreId());
        assertTrue(monitoraggio.riepilogaSchede(banco, ScenarioAccessi.TRIMESTRE).schede().stream()
                .anyMatch(s -> s.id().equals(schedaId)));
    }

    /**
     * Voce attiva del tracciato OPT inizializzata dalla migrazione V24.
     */
    private static final String VOCE_DI_PROVA = "PERSONE_RAGGIUNTE";

    @Test
    @DisplayName("CA-05 · il Banco consulta i conteggi di una OPT e di tutte le OPT, ciascuna con i propri valori")
    void bancoConsultaConteggi() {
        ConteggiOptApi conteggi = scenario.conteggiOpt(); // capacità consegnata: se manca il caso fallisce
        Account optA = scenario.optRichiedente();
        Account optB = scenario.optRichiedente();
        long misura = scenario.misura();
        var trimestre = ScenarioAccessi.TRIMESTRE;
        conteggi.registraConteggiOpt(optA.identita(), trimestre,
                List.of(new VoceMisura(misura, VOCE_DI_PROVA, 3)));
        conteggi.registraConteggiOpt(optB.identita(), trimestre,
                List.of(new VoceMisura(misura, VOCE_DI_PROVA, 7)));
        IdentitaAutenticata banco = scenario.banco().identita();

        List<ConteggioOpt> vistaA = conteggi.consultaConteggiOpt(banco, optA.id(), trimestre, trimestre);
        List<ConteggioOpt> vistaB = conteggi.consultaConteggiOpt(banco, optB.id(), trimestre, trimestre);
        List<ConteggioOpt> vistaTutte = conteggi.consultaConteggiOpt(banco, null, trimestre, trimestre);

        assertEquals(List.of(new ConteggioOpt(optA.id(), new VoceMisura(misura, VOCE_DI_PROVA, 3))), vistaA,
                "la vista di una OPT contiene solo i suoi conteggi");
        assertEquals(List.of(new ConteggioOpt(optB.id(), new VoceMisura(misura, VOCE_DI_PROVA, 7))), vistaB,
                "la vista di una OPT contiene solo i suoi conteggi");
        assertEquals(Set.of(new ConteggioOpt(optA.id(), new VoceMisura(misura, VOCE_DI_PROVA, 3)),
                        new ConteggioOpt(optB.id(), new VoceMisura(misura, VOCE_DI_PROVA, 7))),
                vistaTutte.stream().filter(c -> Set.of(optA.id(), optB.id()).contains(c.optId()))
                        .collect(Collectors.toSet()),
                "la vista complessiva contiene entrambe le OPT con i rispettivi valori");
    }

    /** Ruoli diversi dal Banco, inclusi gli operatori interni. */
    enum NonBanco { OPT, OPERATORE_INTERNO, OPERATORE_ESTERNO }

    /** Azioni riservate al Banco elencate da CA-06. */
    enum AzioneBanco { PRESA_IN_CARICO, CONFERMA_ACCORDO, RIFIUTO_DEFINITIVO }

    private Account account(NonBanco ruolo) {
        return switch (ruolo) {
            case OPT -> scenario.optRichiedente();
            case OPERATORE_INTERNO -> scenario.operatore(TipoErogatore.INTERNO_BANCO);
            case OPERATORE_ESTERNO -> scenario.operatore(TipoErogatore.ESTERNO_PAGATO_BANCO);
        };
    }

    /** Stato minimo da cui l'azione è eseguibile dal Banco. */
    private UUID statoMinimo(AzioneBanco azione) {
        Account titolare = scenario.optRichiedente();
        return switch (azione) {
            case PRESA_IN_CARICO -> scenario.richiestaCreata(titolare);
            case CONFERMA_ACCORDO -> scenario.richiestaInProgrammazione(titolare, scenario.erogatore(TipoErogatore.OPT));
            case RIFIUTO_DEFINITIVO -> scenario.richiestaPresaInCarico(titolare);
        };
    }

    private void esegui(AzioneBanco azione, IdentitaAutenticata attore, UUID richiestaId) {
        switch (azione) {
            case PRESA_IN_CARICO -> scenario.coordinamento().prendiInCarico(attore, richiestaId);
            case CONFERMA_ACCORDO -> scenario.coordinamento().confermaAccordo(attore, richiestaId, ScenarioAccessi.ACCORDO);
            case RIFIUTO_DEFINITIVO -> scenario.coordinamento().rifiutaRichiesta(attore, richiestaId);
        }
    }

    static Stream<Arguments> azioneERuolo() {
        return Stream.of(AzioneBanco.values())
                .flatMap(azione -> Stream.of(NonBanco.values()).map(ruolo -> Arguments.of(azione, ruolo)));
    }

    @ParameterizedTest(name = "CA-06 · {0} negata a {1}; il Banco la esegue sullo stesso stato")
    @MethodSource("azioneERuolo")
    void azioneRiservataAlBanco(AzioneBanco azione, NonBanco ruolo) {
        UUID id = statoMinimo(azione);
        IdentitaAutenticata attore = account(ruolo).identita();

        assertNegatoSenzaModifiche(id, () -> esegui(azione, attore, id));

        // controllo positivo: sullo stesso stato l'azione è consentita al Banco
        esegui(azione, scenario.banco().identita(), id);
    }

    @Test
    @DisplayName("CA-06 · la OPT titolare non prende in carico né rifiuta la propria richiesta")
    void titolareNonCoordinaLaPropriaRichiesta() {
        Account titolare = scenario.optRichiedente();
        UUID creata = scenario.richiestaCreata(titolare);
        UUID presaInCarico = scenario.richiestaPresaInCarico(titolare);
        UUID inProgrammazione = scenario.richiestaInProgrammazione(titolare, scenario.erogatore(TipoErogatore.OPT));
        IdentitaAutenticata identita = titolare.identita();

        assertNegatoSenzaModifiche(creata, () -> scenario.coordinamento().prendiInCarico(identita, creata));
        assertNegatoSenzaModifiche(presaInCarico, () -> scenario.coordinamento().rifiutaRichiesta(identita, presaInCarico));
        assertNegatoSenzaModifiche(inProgrammazione,
                () -> scenario.coordinamento().rifiutaRichiesta(identita, inProgrammazione));
    }

    @ParameterizedTest(name = "CA-06 · operatore {0} non assegnato non rifiuta una richiesta In programmazione")
    @EnumSource(value = TipoErogatore.class, names = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    void operatoreNonRifiutaInProgrammazione(TipoErogatore tipologia) {
        Account operatore = scenario.operatore(tipologia);
        UUID id = scenario.richiestaInProgrammazione(scenario.optRichiedente(), scenario.erogatore(TipoErogatore.OPT));

        assertNegatoSenzaModifiche(id, () -> scenario.coordinamento().rifiutaRichiesta(operatore.identita(), id));
    }
}
