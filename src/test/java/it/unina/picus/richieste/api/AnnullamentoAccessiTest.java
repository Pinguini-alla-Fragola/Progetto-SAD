package it.unina.picus.richieste.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoAvviso;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * #28 — CA-10 … CA-15: chi può annullare, in quali stati, con quale regola sul motivo, chi viene
 * avvisato e cosa accade ripetendo il comando.
 *
 * <p>Ambito per account, dalla tabella di #28: Banco di coordinamento tutte; OPT richiedente quelle
 * inviate; OPT erogatrice quelle assegnate alla propria OPT, anche tramite professionista esterno;
 * operatore interno o esterno quelle assegnate al proprio account. Il motivo è facoltativo solo da
 * Creata. Il solo contatto come candidato non autorizza.</p>
 *
 * <p>Tecniche: tabella decisionale attore × stato × motivo con tutte le combinazioni; valori limite sul
 * motivo; transizioni di stato fino ad Annullata e ripetizione del comando.</p>
 *
 * <p>Scelte da confermare con il team:</p>
 * <ul>
 *   <li>un motivo vuoto o di soli spazi è trattato come mancante (CA-12);</li>
 *   <li>CA-14 non dice se chi annulla riceve anche l'avviso: i test lo verificano per gli altri
 *       coinvolti e non per l'autore;</li>
 *   <li>la matrice di annullamento di {@code regole-funzionali.md} limita ancora l'annullamento a
 *       Confermata: i test seguono #28, che lo ammette in ogni stato, anche da Rifiutata.</li>
 * </ul>
 */
@DisplayName("#28 CA-10…CA-15 · Annullamento per ruolo, stato e assegnazione")
class AnnullamentoAccessiTest extends AccessiBlackBoxTest {

    private static final String MOTIVO = "Evento eccezionale di prova";

    /** Valori limite del motivo mancante: assente, vuoto, soli spazi. */
    private static final String[] MOTIVI_MANCANTI = {null, "", "   "};

    /** Stati da cui si può annullare. */
    private static final List<StatoRichiesta> STATI_ANNULLABILI = List.of(StatoRichiesta.CREATA,
            StatoRichiesta.PRESA_IN_CARICO, StatoRichiesta.IN_PROGRAMMAZIONE, StatoRichiesta.CONFERMATA,
            StatoRichiesta.RIFIUTATA);

    /** Stati in cui un erogatore può risultare assegnato. */
    private static final List<StatoRichiesta> STATI_CON_ASSEGNATO = List.of(StatoRichiesta.IN_PROGRAMMAZIONE,
            StatoRichiesta.CONFERMATA, StatoRichiesta.RIFIUTATA);

    /** Account che possono annullare qualunque richiesta del proprio ambito, assegnata o meno. */
    enum Coordinatore { BANCO, OPT_RICHIEDENTE }

    /** Soggetti da avvisare dopo un annullamento. */
    enum Soggetto { BANCO, OPT_RICHIEDENTE, EROGATORE_ASSEGNATO }

    private Account attore(Coordinatore coordinatore, Account titolare) {
        return coordinatore == Coordinatore.BANCO ? scenario.banco() : titolare;
    }

    // ------------------------------------------------------------------ CA-10

    static Stream<Arguments> coordinatoreEStato() {
        return Stream.of(Coordinatore.values())
                .flatMap(c -> STATI_ANNULLABILI.stream().map(s -> Arguments.of(c, s)));
    }

    @ParameterizedTest(name = "CA-10 · {0} annulla con motivo da {1}")
    @MethodSource("coordinatoreEStato")
    void bancoERichiedenteAnnullanoInQualsiasiStato(Coordinatore coordinatore, StatoRichiesta stato) {
        Account titolare = scenario.optRichiedente();
        UUID id = scenario.richiestaInStato(stato, titolare);
        RichiestaDettaglio prima = scenario.istantanea(id);

        RichiestaSintesi esito = scenario.richieste()
                .annullaRichiesta(attore(coordinatore, titolare).identita(), id, MOTIVO);

        assertEquals(StatoRichiesta.ANNULLATA, esito.stato());
        assertDatiConservati(prima, scenario.istantanea(id));
    }

    static Stream<Arguments> erogatoreEStato() {
        return Stream.of(TipoErogatore.values())
                .flatMap(t -> STATI_CON_ASSEGNATO.stream().map(s -> Arguments.of(t, s)));
    }

    @ParameterizedTest(name = "CA-10 · {0} assegnato annulla con motivo da {1}")
    @MethodSource("erogatoreEStato")
    void erogatoreAssegnatoAnnullaInQualsiasiStato(TipoErogatore tipo, StatoRichiesta stato) {
        Account titolare = scenario.optRichiedente();
        Account assegnato = scenario.erogatore(tipo);
        UUID id = scenario.richiestaAssegnataInStato(stato, titolare, assegnato);
        RichiestaDettaglio prima = scenario.istantanea(id);

        RichiestaSintesi esito = scenario.richieste().annullaRichiesta(assegnato.identita(), id, MOTIVO);

        assertEquals(StatoRichiesta.ANNULLATA, esito.stato());
        RichiestaDettaglio dopo = scenario.istantanea(id);
        assertDatiConservati(prima, dopo);
        assertEquals(assegnato.id(), dopo.erogatoreAssegnatoId());
    }

    @ParameterizedTest(name = "CA-10 · OPT erogatrice tramite professionista esterno annulla da {0}")
    @EnumSource(value = StatoRichiesta.class, names = {"IN_PROGRAMMAZIONE", "CONFERMATA", "RIFIUTATA"})
    void optTramiteProfessionistaAnnulla(StatoRichiesta stato) {
        Account titolare = scenario.optRichiedente();
        Account optErogatrice = scenario.erogatoreOptTramiteProfessionista();
        UUID id = scenario.richiestaAssegnataInStato(stato, titolare, optErogatrice);
        RichiestaDettaglio prima = scenario.istantanea(id);

        RichiestaSintesi esito = scenario.richieste().annullaRichiesta(optErogatrice.identita(), id, MOTIVO);

        assertEquals(StatoRichiesta.ANNULLATA, esito.stato());
        assertDatiConservati(prima, scenario.istantanea(id));
    }

    // ------------------------------------------------------------------ CA-11 / CA-12

    @ParameterizedTest(name = "CA-11 · da Creata {0} annulla senza motivo")
    @EnumSource(Coordinatore.class)
    void creataAnnullabileSenzaMotivo(Coordinatore coordinatore) {
        Account titolare = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);

        RichiestaSintesi esito = scenario.richieste()
                .annullaRichiesta(attore(coordinatore, titolare).identita(), id, null);

        assertEquals(StatoRichiesta.ANNULLATA, esito.stato());
        assertEquals(StatoRichiesta.ANNULLATA, scenario.istantanea(id).richiesta().stato());
    }

    static Stream<Arguments> senzaMotivoFuoriDaCreata() {
        return Stream.of(Coordinatore.values()).flatMap(c -> STATI_ANNULLABILI.stream()
                .filter(s -> s != StatoRichiesta.CREATA)
                .flatMap(s -> Stream.of(MOTIVI_MANCANTI).map(m -> Arguments.of(c, s, m))));
    }

    @ParameterizedTest(name = "CA-12 · da {1} {0} senza motivo [{2}] è respinto e nulla cambia")
    @MethodSource("senzaMotivoFuoriDaCreata")
    void senzaMotivoRespintoFuoriDaCreata(Coordinatore coordinatore, StatoRichiesta stato, String motivo) {
        Account titolare = scenario.optRichiedente();
        UUID id = scenario.richiestaInStato(stato, titolare);
        RichiestaDettaglio prima = scenario.istantanea(id);

        assertThrows(IllegalArgumentException.class, () -> scenario.richieste()
                .annullaRichiesta(attore(coordinatore, titolare).identita(), id, motivo));
        assertEquals(prima, scenario.istantanea(id), "stato e dati devono restare invariati");
    }

    @ParameterizedTest(name = "CA-12 · {0} assegnato senza motivo da {1} è respinto")
    @MethodSource("erogatoreEStato")
    void erogatoreSenzaMotivoRespinto(TipoErogatore tipo, StatoRichiesta stato) {
        Account assegnato = scenario.erogatore(tipo);
        UUID id = scenario.richiestaAssegnataInStato(stato, scenario.optRichiedente(), assegnato);
        RichiestaDettaglio prima = scenario.istantanea(id);

        assertThrows(IllegalArgumentException.class,
                () -> scenario.richieste().annullaRichiesta(assegnato.identita(), id, null));
        assertEquals(prima, scenario.istantanea(id));
    }

    // ------------------------------------------------------------------ CA-13

    @Test
    @DisplayName("CA-13 · una OPT estranea riceve FORBIDDEN in ogni stato")
    void optEstraneaNonAnnulla() {
        Account estranea = scenario.optRichiedente();
        for (StatoRichiesta stato : STATI_ANNULLABILI) {
            UUID id = scenario.richiestaInStato(stato, scenario.optRichiedente());
            assertNegatoSenzaModifiche(id,
                    () -> scenario.richieste().annullaRichiesta(estranea.identita(), id, MOTIVO));
        }
    }

    @ParameterizedTest(name = "CA-13 · {0} soltanto interpellato riceve FORBIDDEN")
    @EnumSource(TipoErogatore.class)
    void candidatoSoltantoInterpellatoNonAnnulla(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(tipo);
        Account assegnato = scenario.erogatore(tipo);
        UUID id = scenario.richiestaPresaInCarico(titolare);

        // con il contatto ancora in attesa
        ContattoDisponibilita contatto = scenario.interpella(id, interpellato);
        assertNegatoSenzaModifiche(id,
                () -> scenario.richieste().annullaRichiesta(interpellato.identita(), id, MOTIVO));

        // dopo il rifiuto del candidato e l'assegnazione a un altro erogatore
        scenario.richieste().rispondiDisponibilita(interpellato.identita(), contatto.id(), false);
        scenario.assegna(id, assegnato);
        scenario.richieste().confermaAccordo(scenario.banco().identita(), id, ScenarioAccessi.ACCORDO);
        assertNegatoSenzaModifiche(id,
                () -> scenario.richieste().annullaRichiesta(interpellato.identita(), id, MOTIVO));
    }

    @ParameterizedTest(name = "CA-13 · un operatore {0} non assegnato riceve FORBIDDEN")
    @EnumSource(value = TipoErogatore.class, names = {"INTERNO_BANCO", "ESTERNO_PAGATO_BANCO"})
    void operatoreNonAssegnatoNonAnnulla(TipoErogatore tipologia) {
        Account titolare = scenario.optRichiedente();
        Account collega = scenario.operatore(tipologia);
        UUID assegnataAlCollega = scenario.richiestaConfermata(titolare, scenario.erogatore(tipologia));
        UUID assegnataAOpt = scenario.richiestaConfermata(titolare, scenario.erogatore(TipoErogatore.OPT));

        assertNegatoSenzaModifiche(assegnataAlCollega,
                () -> scenario.richieste().annullaRichiesta(collega.identita(), assegnataAlCollega, MOTIVO));
        assertNegatoSenzaModifiche(assegnataAOpt,
                () -> scenario.richieste().annullaRichiesta(collega.identita(), assegnataAOpt, MOTIVO));
    }

    @ParameterizedTest(name = "CA-13 · {0} prima soltanto interpellato annulla quando diventa assegnato")
    @EnumSource(TipoErogatore.class)
    void interpellatoPoiAssegnatoAnnulla(TipoErogatore tipo) {
        Account erogatore = scenario.erogatore(tipo);
        UUID id = scenario.richiestaPresaInCarico(scenario.optRichiedente());

        // il solo contatto non autorizza
        ContattoDisponibilita contatto = scenario.interpella(id, erogatore);
        assertNegatoSenzaModifiche(id,
                () -> scenario.richieste().annullaRichiesta(erogatore.identita(), id, MOTIVO));

        // l'assegnazione è un'altra relazione autorizzante
        scenario.richieste().rispondiDisponibilita(erogatore.identita(), contatto.id(), true);
        scenario.richieste().assegnaErogatore(scenario.banco().identita(), contatto.id());
        RichiestaSintesi esito = scenario.richieste().annullaRichiesta(erogatore.identita(), id, MOTIVO);

        assertEquals(StatoRichiesta.ANNULLATA, esito.stato());
    }

    // ------------------------------------------------------------------ CA-14

    static Stream<Arguments> annullatoreEErogatore() {
        return Stream.of(Soggetto.values())
                .flatMap(s -> Stream.of(TipoErogatore.values()).map(t -> Arguments.of(s, t)));
    }

    @ParameterizedTest(name = "CA-14 · annullamento di {0} da In programmazione con {1} assegnato: avvisi e dati")
    @MethodSource("annullatoreEErogatore")
    void annullamentoValidoAvvisaICoinvolti(Soggetto annullatore, TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account assegnato = scenario.erogatore(tipo);
        Account estranea = scenario.optRichiedente();
        UUID id = scenario.richiestaInProgrammazione(titolare, assegnato);
        RichiestaDettaglio prima = scenario.istantanea(id);
        List<ContattoDisponibilita> contattiPrima = contatti(id);
        Account attore = switch (annullatore) {
            case BANCO -> scenario.banco();
            case OPT_RICHIEDENTE -> titolare;
            case EROGATORE_ASSEGNATO -> assegnato;
        };

        scenario.richieste().annullaRichiesta(attore.identita(), id, MOTIVO);

        assertDatiConservati(prima, scenario.istantanea(id));
        assertEquals(contattiPrima, contatti(id), "i contatti di disponibilità registrati sono conservati");
        for (Account coinvolto : List.of(scenario.banco(), titolare, assegnato)) {
            if (!coinvolto.equals(attore)) {
                assertEquals(1, avvisiDiAnnullamento(coinvolto, id),
                        "avviso di annullamento atteso per " + coinvolto.ruolo());
            }
        }
        assertEquals(0, avvisiDiAnnullamento(estranea, id), "nessun avviso a chi non è coinvolto");
    }

    @Test
    @DisplayName("CA-14 · da Creata, senza erogatore individuato, il Banco viene avvisato")
    void annullamentoDaCreataAvvisaIlBanco() {
        Account titolare = scenario.optRichiedente();
        UUID id = scenario.richiestaCreata(titolare);

        scenario.richieste().annullaRichiesta(titolare.identita(), id, null);

        assertEquals(1, avvisiDiAnnullamento(scenario.banco(), id));
    }

    @ParameterizedTest(name = "CA-14 · da Presa in carico il candidato {0} soltanto interpellato non viene avvisato")
    @EnumSource(TipoErogatore.class)
    void candidatoInterpellatoNonAvvisato(TipoErogatore tipo) {
        Account titolare = scenario.optRichiedente();
        Account interpellato = scenario.erogatore(tipo);
        UUID id = scenario.richiestaPresaInCarico(titolare);
        scenario.interpella(id, interpellato);
        RichiestaDettaglio prima = scenario.istantanea(id);
        List<ContattoDisponibilita> contattiPrima = contatti(id);

        scenario.richieste().annullaRichiesta(titolare.identita(), id, MOTIVO);

        assertDatiConservati(prima, scenario.istantanea(id));
        assertEquals(contattiPrima, contatti(id), "i contatti di disponibilità registrati sono conservati");
        assertEquals(1, avvisiDiAnnullamento(scenario.banco(), id), "il Banco viene avvisato");
        assertEquals(0, avvisiDiAnnullamento(interpellato, id),
                "nessun erogatore è ancora individuato: il candidato non riceve l'avviso");
    }

    // ------------------------------------------------------------------ CA-15

    @ParameterizedTest(name = "CA-15 · ripetendo l'annullamento ({0}) la richiesta resta Annullata e la ricerca chiusa")
    @EnumSource(Coordinatore.class)
    void ripetizioneNonRiapreLaRicerca(Coordinatore coordinatore) {
        Account titolare = scenario.optRichiedente();
        Account assegnato = scenario.erogatore(TipoErogatore.OPT);
        UUID id = scenario.richiestaInProgrammazione(titolare, assegnato);
        Account attore = attore(coordinatore, titolare);
        scenario.richieste().annullaRichiesta(attore.identita(), id, MOTIVO);
        RichiestaDettaglio dopoIlPrimo = scenario.istantanea(id);

        RichiestaSintesi ripetuto = scenario.richieste().annullaRichiesta(attore.identita(), id, MOTIVO);

        assertEquals(StatoRichiesta.ANNULLATA, ripetuto.stato());
        assertEquals(dopoIlPrimo, scenario.istantanea(id), "il secondo comando non cambia dati");
        var banco = scenario.banco().identita();
        assertFalse(scenario.richieste().codaBanco(banco).stream().map(RichiestaSintesi::id).anyMatch(id::equals),
                "la richiesta non torna in coda");
        assertTrue(scenario.richieste().archivioBanco(banco).stream().map(RichiestaSintesi::id).anyMatch(id::equals));
        assertThrows(IllegalStateException.class, () -> scenario.richieste().cercaErogatoriOrdinati(banco, id),
                "la ricerca resta chiusa");
        for (Account coinvolto : List.of(scenario.banco(), titolare, assegnato)) {
            if (!coinvolto.equals(attore)) {
                assertEquals(1, avvisiDiAnnullamento(coinvolto, id),
                        "un solo avviso per destinatario: " + coinvolto.ruolo());
            }
        }
    }

    // ------------------------------------------------------------------ supporto

    /** Stato Annullata con titolare, erogatore, dati della richiesta e accordo invariati. */
    private static void assertDatiConservati(RichiestaDettaglio prima, RichiestaDettaglio dopo) {
        assertEquals(StatoRichiesta.ANNULLATA, dopo.richiesta().stato());
        assertEquals(prima.richiesta().id(), dopo.richiesta().id());
        assertEquals(prima.richiesta().inviataIl(), dopo.richiesta().inviataIl());
        assertEquals(prima.richiesta().misuraId(), dopo.richiesta().misuraId());
        assertEquals(prima.titolareId(), dopo.titolareId());
        assertEquals(prima.tipoDestinatario(), dopo.tipoDestinatario());
        assertEquals(prima.comuneCodice(), dopo.comuneCodice());
        assertEquals(prima.description(), dopo.description());
        assertEquals(prima.erogatoreAssegnatoId(), dopo.erogatoreAssegnatoId());
        assertEquals(prima.accordo(), dopo.accordo());
    }

    /** Contatti di disponibilità della richiesta letti dal Banco. */
    private List<ContattoDisponibilita> contatti(UUID richiestaId) {
        return scenario.richieste().elencoContatti(scenario.banco().identita(), richiestaId);
    }

    private long avvisiDiAnnullamento(Account account, UUID richiestaId) {
        return scenario.comunicazioni().elencoAvvisiOperativi(account.identita()).stream()
                .filter(a -> a.tipo() == TipoAvviso.RICHIESTA_ANNULLATA && a.richiestaId().equals(richiestaId))
                .count();
    }
}
