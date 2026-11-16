package it.unina.picus.richieste.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.unina.picus.shared.AccessiBlackBoxTest;
import it.unina.picus.shared.ScenarioAccessi;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.DettaglioContatto;
import it.unina.picus.richieste.api.RichiesteApi.EsitoContatto;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * #28 — CA-02: l'account interpellato (OPT o operatore del Banco) vede i soli dati minimi e risponde
 * solo al proprio contatto; le altre pratiche della richiedente restano riservate.
 *
 * <p>Tecniche: classi di equivalenza sull'attore (interpellato, altro candidato della stessa richiesta,
 * estraneo, OPT richiedente, Banco) per ciascuna tipologia di erogatore, con un controllo positivo.</p>
 *
 * <p>Da confermare con il team: QS-01A dice che la OPT interpellata «apre R1», mentre il contratto
 * {@code consultaRichiesta} ammette solo Banco, titolare e assegnato e l'interpellato usa
 * {@code consultaContatto}. I test seguono il contratto.</p>
 */
@DisplayName("#28 CA-02 · Erogatore interpellato e contatto di disponibilità")
class ContattiDisponibilitaAccessiTest extends AccessiBlackBoxTest {

    @ParameterizedTest(name = "CA-02 · {0} interpellato consulta i dati minimi del contatto")
    @EnumSource(TipoErogatore.class)
    void interpellatoConsultaDatiMinimi(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(tipo);
        ContattoDisponibilita contatto = scenario.richiestaConContatto(titolare, interpellato);

        DettaglioContatto dettaglio = scenario.richieste().consultaContatto(interpellato.identita(), contatto.id());

        assertEquals(contatto.id(), dettaglio.contatto().id());
        assertEquals(interpellato.id(), dettaglio.contatto().erogatoreId());
        assertEquals(EsitoContatto.IN_ATTESA, dettaglio.contatto().esito());
        assertEquals(scenario.misura(), dettaglio.misuraId());
        assertEquals(TipoDestinatario.PERSONA, dettaglio.tipoDestinatario());
        assertEquals(ScenarioAccessi.COMUNE_RICHIESTA, dettaglio.comuneCodice());
    }

    @Test
    @DisplayName("CA-02 · la proiezione del contatto non espone titolare, descrizione né accordo")
    void proiezioneContattoMinima() {
        Set<String> campi = Arrays.stream(DettaglioContatto.class.getRecordComponents())
                .map(c -> c.getName()).collect(Collectors.toSet());

        assertEquals(Set.of("contatto", "misuraId", "nomeMisura", "tipoDestinatario", "comuneCodice"), campi,
                "i dati minimi per valutare la disponibilità sono cambiati: rivedere QS-01A e #28");
    }

    @ParameterizedTest(name = "CA-02 · {0} interpellato risponde al proprio contatto")
    @EnumSource(TipoErogatore.class)
    void interpellatoRispondeAlProprioContatto(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(tipo);
        ContattoDisponibilita contatto = scenario.richiestaConContatto(titolare, interpellato);

        ContattoDisponibilita risposta =
                scenario.richieste().rispondiDisponibilita(interpellato.identita(), contatto.id(), true);

        assertEquals(EsitoContatto.DISPONIBILE, risposta.esito());
        assertEquals(StatoRichiesta.PRESA_IN_CARICO, scenario.istantanea(contatto.richiestaId()).richiesta().stato());
    }

    @ParameterizedTest(name = "CA-02 · {0} interpellato non apre il dettaglio completo della richiesta")
    @EnumSource(TipoErogatore.class)
    void interpellatoNonApreIlDettaglioCompleto(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(tipo);
        ContattoDisponibilita contatto = scenario.richiestaConContatto(titolare, interpellato);

        assertNegatoSenzaModifiche(contatto.richiestaId(),
                () -> scenario.richieste().consultaRichiesta(interpellato.identita(), contatto.richiestaId()));
    }

    @ParameterizedTest(name = "CA-02 · le altre pratiche della richiedente restano riservate a {0}")
    @EnumSource(TipoErogatore.class)
    void altrePraticheDellaRichiedenteRiservate(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(tipo);
        scenario.richiestaConContatto(titolare, interpellato);
        UUID altra = scenario.richiestaCreata(titolare);

        assertNegatoSenzaModifiche(altra,
                () -> scenario.richieste().consultaRichiesta(interpellato.identita(), altra));
        assertNegatoSenzaModifiche(altra,
                () -> scenario.richieste().annullaRichiesta(interpellato.identita(), altra, "Motivo di prova"));
    }

    @ParameterizedTest(name = "CA-02 · un account non interpellato ({0}) non consulta né risponde")
    @EnumSource(TipoErogatore.class)
    void accountNonInterpellatoNonConsultaNeRisponde(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(TipoErogatore.OPT);
        Account estraneo = tipo == TipoErogatore.OPT ? scenario.opt(ScenarioAccessi.COMUNE_LIMITROFO)
                : scenario.operatore(tipo);
        ContattoDisponibilita contatto = scenario.richiestaConContatto(titolare, interpellato);

        assertNegato(() -> scenario.richieste().consultaContatto(estraneo.identita(), contatto.id()));
        assertNegatoSenzaModifiche(contatto.richiestaId(),
                () -> scenario.richieste().rispondiDisponibilita(estraneo.identita(), contatto.id(), true));
        assertEquals(EsitoContatto.IN_ATTESA, esitoPerIlBanco(contatto));
    }

    @ParameterizedTest(name = "CA-02 · {0} interpellato non consulta né risponde al contatto di un altro candidato")
    @EnumSource(TipoErogatore.class)
    void interpellatoNonUsaIlContattoDiUnAltroCandidato(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account candidatoA = scenario.erogatore(tipo);
        Account candidatoB = scenario.erogatore(tipo);
        UUID id = scenario.richiestaPresaInCarico(titolare);
        ContattoDisponibilita contattoA = scenario.interpella(id, candidatoA);
        ContattoDisponibilita contattoB = scenario.interpella(id, candidatoB);

        // stessa richiesta, ma il contatto ricevuto da A non autorizza il contatto di B
        assertNegato(() -> scenario.richieste().consultaContatto(candidatoA.identita(), contattoB.id()));
        assertNegatoSenzaModifiche(id,
                () -> scenario.richieste().rispondiDisponibilita(candidatoA.identita(), contattoB.id(), true));
        assertEquals(EsitoContatto.IN_ATTESA, esitoPerIlBanco(contattoB));

        // controllo positivo: A risponde al proprio contatto
        scenario.richieste().rispondiDisponibilita(candidatoA.identita(), contattoA.id(), false);
        assertEquals(EsitoContatto.NON_DISPONIBILE, esitoPerIlBanco(contattoA));
        assertEquals(EsitoContatto.IN_ATTESA, esitoPerIlBanco(contattoB));
    }

    @Test
    @DisplayName("CA-02 · la OPT richiedente e il Banco non rispondono al posto dell'interpellato")
    void soloIlDestinatarioRisponde() {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(TipoErogatore.OPT);
        ContattoDisponibilita contatto = scenario.richiestaConContatto(titolare, interpellato);

        assertNegatoSenzaModifiche(contatto.richiestaId(),
                () -> scenario.richieste().rispondiDisponibilita(titolare.identita(), contatto.id(), true));
        assertNegatoSenzaModifiche(contatto.richiestaId(),
                () -> scenario.richieste().rispondiDisponibilita(scenario.banco().identita(), contatto.id(), true));
        assertEquals(EsitoContatto.IN_ATTESA, esitoPerIlBanco(contatto));
    }

    @Test
    @DisplayName("CA-02 · un contatto inesistente non rivela dati")
    void contattoInesistente() {
        Account opt = scenario.erogatore(TipoErogatore.OPT);

        Throwable errore = assertThrows(RuntimeException.class,
                () -> scenario.richieste().consultaContatto(opt.identita(), UUID.randomUUID()));
        assertTrue(errore instanceof SecurityException || errore instanceof NoSuchElementException,
                "atteso FORBIDDEN o risorsa inesistente, ricevuto " + errore);
    }

    private EsitoContatto esitoPerIlBanco(ContattoDisponibilita contatto) {
        return scenario.richieste().elencoContatti(scenario.banco().identita(), contatto.richiestaId()).stream()
                .filter(c -> c.id().equals(contatto.id()))
                .findFirst().orElseThrow().esito();
    }
}
