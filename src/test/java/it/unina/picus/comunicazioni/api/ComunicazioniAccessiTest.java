package it.unina.picus.comunicazioni.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.unina.picus.shared.AccessiBlackBoxTest;
import it.unina.picus.shared.ScenarioAccessi;
import it.unina.picus.shared.ScenarioAccessi.Account;
import it.unina.picus.comunicazioni.api.ComunicazioniApi.Comunicazione;
import it.unina.picus.comunicazioni.api.ComunicazioniApi.DatiComunicazione;
import it.unina.picus.comunicazioni.api.ComunicazioniApi.FileAllegato;
import it.unina.picus.comunicazioni.api.ComunicazioniApi.Riscontro;
import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;
import it.unina.picus.utenze.api.UtenzeApi.TipoErogatore;

/**
 * #28 — CA-07: una comunicazione è visibile ai soli destinatari e al Banco; gli altri account
 * ricevono {@code FORBIDDEN}. Ogni destinatario conferma soltanto per sé.
 *
 * <p>Tecniche: matrice tipo di destinatari × ruolo di chi consulta, con il Banco su ogni tipo; gli
 * allegati seguono gli stessi controlli della comunicazione.</p>
 */
@DisplayName("#28 CA-07 · Comunicazioni per destinatario")
class ComunicazioniAccessiTest extends AccessiBlackBoxTest {

    private static final byte[] CONTENUTO = "Allegato sintetico #28".getBytes(StandardCharsets.UTF_8);

    private Comunicazione invia(TipoDestinatari tipo, Set<UUID> gruppo, List<FileAllegato> allegati) {
        var dati = new DatiComunicazione("Avviso di prova", "Testo sintetico", tipo, gruppo, allegati);
        return scenario.comunicazioni().inviaComunicazione(scenario.banco().identita(), dati);
    }

    private Comunicazione invia(TipoDestinatari tipo, Set<UUID> gruppo) {
        return invia(tipo, gruppo, List.of());
    }

    private boolean ricevuta(Account account, UUID comunicazioneId) {
        return scenario.comunicazioni().elencoRicevute(account.identita()).stream()
                .anyMatch(c -> c.id().equals(comunicazioneId));
    }

    @Test
    @DisplayName("CA-07 · gruppo di OPT: la vedono la OPT del gruppo e il Banco; gli altri ricevono FORBIDDEN")
    void gruppoDiOpt() {
        Account destinataria = scenario.optRichiedente();
        Account esclusa = scenario.optRichiedente();
        Account operatore = scenario.operatore(TipoErogatore.INTERNO_BANCO);
        UUID id = invia(TipoDestinatari.GRUPPO_OPT, Set.of(destinataria.id())).id();

        assertEquals(id, scenario.comunicazioni().consultaComunicazione(destinataria.identita(), id).id());
        assertEquals(id, scenario.comunicazioni().consultaComunicazione(scenario.banco().identita(), id).id());
        assertNegato(() -> scenario.comunicazioni().consultaComunicazione(esclusa.identita(), id));
        assertNegato(() -> scenario.comunicazioni().consultaComunicazione(operatore.identita(), id));
        assertTrue(ricevuta(destinataria, id));
        assertFalse(ricevuta(esclusa, id));
        assertFalse(ricevuta(operatore, id));
    }

    @Test
    @DisplayName("CA-07 · tutte le OPT: la vedono le OPT; un operatore del Banco riceve FORBIDDEN")
    void tutteLeOpt() {
        Account optA = scenario.optRichiedente();
        Account optB = scenario.opt(ScenarioAccessi.COMUNE_LIMITROFO);
        Account operatore = scenario.operatore(TipoErogatore.ESTERNO_PAGATO_BANCO);
        UUID id = invia(TipoDestinatari.TUTTE_LE_OPT, Set.of()).id();

        assertEquals(id, scenario.comunicazioni().consultaComunicazione(optA.identita(), id).id());
        assertEquals(id, scenario.comunicazioni().consultaComunicazione(optB.identita(), id).id());
        assertNegato(() -> scenario.comunicazioni().consultaComunicazione(operatore.identita(), id));
    }

    @Test
    @DisplayName("CA-07 · gruppo di operatori: lo vede l'operatore del gruppo; collega e OPT ricevono FORBIDDEN")
    void gruppoDiOperatori() {
        Account destinatario = scenario.operatore(TipoErogatore.INTERNO_BANCO);
        Account collega = scenario.operatore(TipoErogatore.ESTERNO_PAGATO_BANCO);
        Account opt = scenario.optRichiedente();
        UUID id = invia(TipoDestinatari.GRUPPO_OPERATORI, Set.of(destinatario.id())).id();

        assertEquals(id, scenario.comunicazioni().consultaComunicazione(destinatario.identita(), id).id());
        assertNegato(() -> scenario.comunicazioni().consultaComunicazione(collega.identita(), id));
        assertNegato(() -> scenario.comunicazioni().consultaComunicazione(opt.identita(), id));
    }

    @Test
    @DisplayName("CA-07 · tutti gli operatori: interni ed esterni la vedono; una OPT riceve FORBIDDEN")
    void tuttiGliOperatori() {
        Account interno = scenario.operatore(TipoErogatore.INTERNO_BANCO);
        Account esterno = scenario.operatore(TipoErogatore.ESTERNO_PAGATO_BANCO);
        Account opt = scenario.optRichiedente();
        UUID id = invia(TipoDestinatari.TUTTI_GLI_OPERATORI, Set.of()).id();

        assertEquals(id, scenario.comunicazioni().consultaComunicazione(interno.identita(), id).id());
        assertEquals(id, scenario.comunicazioni().consultaComunicazione(esterno.identita(), id).id());
        assertNegato(() -> scenario.comunicazioni().consultaComunicazione(opt.identita(), id));
    }

    @ParameterizedTest(name = "CA-07 · il Banco autorizzato consulta una comunicazione {0}")
    @EnumSource(TipoDestinatari.class)
    void bancoConsultaOgniTipoDiComunicazione(TipoDestinatari tipo) {
        Set<UUID> gruppo = switch (tipo) {
            case GRUPPO_OPT -> Set.of(scenario.optRichiedente().id());
            case GRUPPO_OPERATORI -> Set.of(scenario.operatore(TipoErogatore.INTERNO_BANCO).id());
            default -> Set.of();
        };
        UUID id = invia(tipo, gruppo).id();

        assertEquals(id, scenario.comunicazioni().consultaComunicazione(scenario.banco().identita(), id).id());
    }

    @Test
    @DisplayName("CA-07 · ogni destinatario conferma solo per sé; un non destinatario non conferma")
    void confermaSoloPerSe() {
        Account destinatariaA = scenario.optRichiedente();
        Account destinatariaB = scenario.optRichiedente();
        Account esclusa = scenario.optRichiedente();
        UUID id = invia(TipoDestinatari.GRUPPO_OPT, Set.of(destinatariaA.id(), destinatariaB.id())).id();

        assertNegato(() -> scenario.comunicazioni().confermaRicezione(esclusa.identita(), id));
        assertNull(riscontro(id, destinatariaA).confermataIl(), "il diniego non registra riscontri");
        assertNull(riscontro(id, destinatariaB).confermataIl(), "il diniego non registra riscontri");
        assertFalse(scenario.comunicazioni().elencoRiscontri(scenario.banco().identita(), id).stream()
                .anyMatch(r -> r.destinatarioId().equals(esclusa.id())));

        scenario.comunicazioni().confermaRicezione(destinatariaA.identita(), id);
        Riscontro primoDiA = riscontro(id, destinatariaA);
        assertNotNull(primoDiA.confermataIl(), "A ha confermato");
        assertNull(riscontro(id, destinatariaB).confermataIl(), "la conferma di A non vale per B");

        scenario.comunicazioni().confermaRicezione(destinatariaB.identita(), id);
        assertNotNull(riscontro(id, destinatariaB).confermataIl(), "B ha confermato");
        assertEquals(primoDiA, riscontro(id, destinatariaA), "la conferma di B non modifica il riscontro di A");
    }

    @Test
    @DisplayName("CA-07 · l'allegato segue gli stessi controlli della comunicazione")
    void allegatoRiservatoAiDestinatari() {
        Account destinataria = scenario.optRichiedente();
        Account esclusa = scenario.optRichiedente();
        var file = new FileAllegato("avviso.txt", "text/plain", CONTENUTO);
        UUID id = invia(TipoDestinatari.GRUPPO_OPT, Set.of(destinataria.id()), List.of(file)).id();
        UUID allegatoId = scenario.comunicazioni().consultaComunicazione(destinataria.identita(), id)
                .allegati().getFirst().id();

        assertArrayEquals(CONTENUTO,
                scenario.comunicazioni().scaricaAllegato(destinataria.identita(), allegatoId).contenuto());
        assertNegato(() -> scenario.comunicazioni().scaricaAllegato(esclusa.identita(), allegatoId));
    }

    private Riscontro riscontro(UUID comunicazioneId, Account destinatario) {
        return scenario.comunicazioni().elencoRiscontri(scenario.banco().identita(), comunicazioneId).stream()
                .filter(r -> r.destinatarioId().equals(destinatario.id()))
                .findFirst().orElseThrow();
    }
}
