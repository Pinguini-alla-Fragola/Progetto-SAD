package it.unina.picus.comunicazioni.web;

import static it.unina.picus.comunicazioni.web.ComunicazioniHttp.assertCampiDaCompletare;
import static it.unina.picus.comunicazioni.web.ComunicazioniHttp.campi;
import static it.unina.picus.comunicazioni.web.ComunicazioniHttp.inviata;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;
import it.unina.picus.comunicazioni.web.ComunicazioniHttp.Inviata;
import it.unina.picus.comunicazioni.web.ComunicazioniHttp.Modalita;
import it.unina.picus.shared.ComunicazioniBlackBoxTest;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * CA-04 della #55: una comunicazione senza testo o senza destinatari non viene inviata e il sistema indica i dati
 * da completare.
 *
 * <p>Tecniche: <b>tabella decisionale</b> su testo (presente, assente, vuoto, soli spazi), tipo di destinatari
 * (presente, assente, inesistente) e gruppo (vuoto o assente per un tipo a gruppo), in entrambe le modalità di
 * invio (JSON e parte {@code dati} del multipart); <b>valori limite</b> sul testo ({@code ""} e
 * {@code "   "}); <b>controllo positivo</b>: gli stessi dati, completati, vengono inviati.</p>
 *
 * <p>Ogni rifiuto è un {@code 400} con i soli campi attesi e nessun valore inserito; «nessun invio» vuol dire
 * nessun elemento della prima pagina delle inviate del Banco con il marcatore nel titolo o nel contenuto, e, per la
 * OPT e l'operatore creati dal test che sarebbero stati destinatari, {@code totale} delle ricevute e
 * {@code /da-confermare} invariati.</p>
 */
@DisplayName("#55 CA-04 · Invio senza testo o senza destinatari")
class DatiIncompletiComunicazioneTest extends ComunicazioniBlackBoxTest {

    /** Segnaposto sostituito dal marcatore del test. */
    private static final String M = "{m}";

    /**
     * Riga della tabella decisionale.
     *
     * @param regola descrizione della combinazione
     * @param contenuto testo inviato; {@code null} se assente
     * @param tipo tipo di destinatari; {@code null} se assente
     * @param gruppo gruppo inviato; {@code null} se assente
     * @param attesi campi da completare
     */
    record Caso(String regola, String contenuto, String tipo, List<String> gruppo, Set<String> attesi) {

        @Override
        public String toString() {
            return regola;
        }
    }

    private static final String TESTO = "Testo sintetico " + M;
    private static final String OPT = TipoDestinatari.TUTTE_LE_OPT.name();

    static Stream<Arguments> tabella() {
        List<Caso> casi = List.of(
                new Caso("contenuto assente", null, OPT, null, Set.of("contenuto")),
                new Caso("contenuto vuoto", "", OPT, null, Set.of("contenuto")),
                new Caso("contenuto di soli spazi", "   ", OPT, null, Set.of("contenuto")),
                new Caso("destinatariTipo assente", TESTO, null, null, Set.of("destinatariTipo")),
                new Caso("destinatariTipo inesistente", TESTO, "SCONOSCIUTO_" + M, null, Set.of("destinatariTipo")),
                new Caso("GRUPPO_OPT con gruppo vuoto", TESTO, TipoDestinatari.GRUPPO_OPT.name(), List.of(),
                        Set.of("gruppo")),
                new Caso("GRUPPO_OPT senza gruppo", TESTO, TipoDestinatari.GRUPPO_OPT.name(), null,
                        Set.of("gruppo")),
                new Caso("GRUPPO_OPERATORI con gruppo vuoto", TESTO, TipoDestinatari.GRUPPO_OPERATORI.name(),
                        List.of(), Set.of("gruppo")),
                new Caso("testo e destinatariTipo assenti", null, null, null,
                        Set.of("contenuto", "destinatariTipo")));
        return casi.stream().flatMap(caso -> Stream.of(Modalita.values()).map(modo -> Arguments.of(caso, modo)));
    }

    private ComunicazioniHttp comunicazioni;

    @BeforeEach
    void preparaClient() {
        comunicazioni = new ComunicazioniHttp(http);
    }

    @ParameterizedTest(name = "{0} via {1}")
    @MethodSource("tabella")
    @DisplayName("CA-04 · dati incompleti: 400 sui campi da completare e nessun invio; completati, inviata")
    void datiIncompleti(Caso caso, Modalita modalita) throws Exception {
        Account banco = scenario.banco();
        Account opt = scenario.optRichiedente();
        Account operatore = scenario.operatore(TipoErogatore.ESTERNO_PAGATO_BANCO);
        String marcatore = marcatore();
        String titolo = "Oggetto incompleto " + marcatore;
        long ricevuteOptPrima = comunicazioni.ricevute(opt).totale();
        long ricevuteOperatorePrima = comunicazioni.ricevute(operatore).totale();
        long optPrima = comunicazioni.daConfermare(opt);
        long operatorePrima = comunicazioni.daConfermare(operatore);

        var risposta = comunicazioni.invia(banco, modalita,
                campi(titolo, sostituisci(caso.contenuto(), marcatore), sostituisci(caso.tipo(), marcatore),
                        caso.gruppo()));

        assertCampiDaCompletare(risposta, caso.attesi(), marcatore);
        assertFalse(comunicazioni.inviate(banco).contieneMarcatore(marcatore), "nessuna comunicazione inviata");
        assertEquals(ricevuteOptPrima, comunicazioni.ricevute(opt).totale(), "nessuna ricevuta per la OPT");
        assertEquals(ricevuteOperatorePrima, comunicazioni.ricevute(operatore).totale(),
                "nessuna ricevuta per l'operatore");
        assertEquals(optPrima, comunicazioni.daConfermare(opt), "nessuna notifica");
        assertEquals(operatorePrima, comunicazioni.daConfermare(operatore), "nessuna notifica");

        // Controllo positivo: gli stessi dati, con i campi mancanti completati, vengono inviati.
        String contenuto = caso.attesi().contains("contenuto") ? "Testo completato " + marcatore
                : sostituisci(caso.contenuto(), marcatore);
        String tipo = caso.attesi().contains("destinatariTipo") ? OPT : caso.tipo();
        List<?> gruppo = null;
        Account destinatario = tipo.contains("OPERATORI") ? operatore : opt;
        if (tipo.startsWith("GRUPPO")) {
            gruppo = List.of(comunicazioni.scegli(banco, tipo, destinatario));
        }
        Inviata inviata = inviata(comunicazioni.invia(banco, modalita, campi(titolo, contenuto, tipo, gruppo)));
        assertEquals(tipo, inviata.destinatariTipo());
        assertTrue(comunicazioni.inviate(banco).contiene(inviata.id()), "completati i dati, la comunicazione è inviata");
        assertTrue(comunicazioni.ricevute(destinatario).contiene(inviata.id()),
                "completati i dati, il destinatario la riceve");
    }

    private static String sostituisci(String valore, String marcatore) {
        return valore == null ? null : valore.replace(M, marcatore);
    }
}
