package it.unina.picus.richieste.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoAvviso;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.DettaglioContatto;
import it.unina.picus.richieste.api.RichiesteApi.EsitoContatto;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Test di accettazione black box della User Story #51
 * «Risposta alla richiesta di disponibilità».
 *
 * <p>Ogni test riporta nel nome l'Acceptance Criterion verificato (AC1..AC4, nell'ordine
 * dell'issue). A livello di contratto Java il {@code FORBIDDEN} dell'AC4 corrisponde a
 * {@link SecurityException}; la traduzione in HTTP 403 spetta allo strato web.</p>
 */
@DisplayName("#51 Risposta alla richiesta di disponibilità")
class RispostaDisponibilitaTest extends ScenarioRichieste {

    // ------------------------------------------------------------------ AC1

    @Test
    @DisplayName("AC1 - la OPT interpellata consulta i dati necessari a valutare la disponibilità")
    void ac1_optInterpellataConsultaIDatiDellaRichiesta() {
        UUID richiestaId = richiestaPresaInCarico();
        ContattoDisponibilita contatto = contattoInAttesa(richiestaId, optErogatrice);

        DettaglioContatto dettaglio = richieste.consultaContatto(optErogatrice, contatto.id());

        assertAll(
                () -> assertEquals(contatto.id(), dettaglio.contatto().id()),
                () -> assertEquals(richiestaId, dettaglio.contatto().richiestaId()),
                () -> assertEquals(optErogatrice.accountId(), dettaglio.contatto().erogatoreId()),
                () -> assertEquals(EsitoContatto.IN_ATTESA, dettaglio.contatto().esito()),
                () -> assertEquals(misuraId, dettaglio.misuraId()),
                () -> assertEquals("Misura di prova", dettaglio.nomeMisura()),
                () -> assertEquals(TipoDestinatario.PERSONA, dettaglio.tipoDestinatario()),
                () -> assertEquals(comune, dettaglio.comuneCodice()));
    }

    @Test
    @DisplayName("AC1 - l'operatore del Banco interpellato consulta i dati dal proprio account personale")
    void ac1_operatoreInterpellatoConsultaIDatiDellaRichiesta() {
        UUID richiestaId = richiestaPresaInCarico();
        Optional<IdentitaAutenticata> operatore = operatoreCandidato(richiestaId);
        assumeTrue(operatore.isPresent(), "nessun operatore del Banco abilitato alla misura nei dati di prova");
        ContattoDisponibilita contatto = contattoInAttesa(richiestaId, operatore.get());

        DettaglioContatto dettaglio = richieste.consultaContatto(operatore.get(), contatto.id());

        assertAll(
                () -> assertEquals(contatto.id(), dettaglio.contatto().id()),
                () -> assertEquals(operatore.get().accountId(), dettaglio.contatto().erogatoreId()),
                () -> assertEquals(misuraId, dettaglio.misuraId()),
                () -> assertEquals(TipoDestinatario.PERSONA, dettaglio.tipoDestinatario()),
                () -> assertEquals(comune, dettaglio.comuneCodice()));
    }

    @Test
    @DisplayName("AC1 - la notifica arriva al solo account interpellato e punta al contatto")
    void ac1_avvisoDiDisponibilitaSoloAlDestinatario() {
        UUID richiestaId = richiestaPresaInCarico();
        ContattoDisponibilita contatto = contattoInAttesa(richiestaId, optErogatrice);

        assertAll(
                () -> assertTrue(
                        comunicazioni.elencoAvvisiOperativi(optErogatrice).stream()
                                .anyMatch(avviso -> avviso.tipo() == TipoAvviso.RICHIESTA_DISPONIBILITA
                                        && contatto.id().equals(avviso.contattoId())
                                        && richiestaId.equals(avviso.richiestaId())),
                        "la OPT interpellata deve trovare l'avviso del contatto"),
                () -> assertTrue(
                        comunicazioni.elencoAvvisiOperativi(optErogatriceAlternativa).stream()
                                .noneMatch(avviso -> contatto.id().equals(avviso.contattoId())),
                        "un erogatore non interpellato non deve ricevere l'avviso"));
    }

    @Test
    @DisplayName("AC1 - consultare il contatto non equivale a rispondere")
    void ac1_laConsultazioneNonRegistraUnaRisposta() {
        UUID richiestaId = richiestaPresaInCarico();
        ContattoDisponibilita contatto = contattoInAttesa(richiestaId, optErogatrice);

        richieste.consultaContatto(optErogatrice, contatto.id());

        assertAll(
                () -> assertEquals(EsitoContatto.IN_ATTESA, esitoVistoDalBanco(richiestaId, contatto.id())),
                () -> assertEquals(StatoRichiesta.PRESA_IN_CARICO, statoVistoDalBanco(richiestaId)));
    }

    // ------------------------------------------------------------------ AC2

    @Test
    @DisplayName("AC2 - la disponibilità è registrata, visibile al Banco e la richiesta resta Presa in carico")
    void ac2_rispostaPositivaVisibileAlBanco() {
        UUID richiestaId = richiestaPresaInCarico();
        ContattoDisponibilita contatto = contattoInAttesa(richiestaId, optErogatrice);

        ContattoDisponibilita risposta = richieste.rispondiDisponibilita(optErogatrice, contatto.id(), true);

        RichiestaDettaglio pratica = richieste.consultaRichiesta(banco, richiestaId);
        assertAll(
                () -> assertEquals(contatto.id(), risposta.id()),
                () -> assertEquals(EsitoContatto.DISPONIBILE, risposta.esito()),
                () -> assertEquals(EsitoContatto.DISPONIBILE, esitoVistoDalBanco(richiestaId, contatto.id())),
                () -> assertEquals(StatoRichiesta.PRESA_IN_CARICO, pratica.richiesta().stato()),
                () -> assertNull(pratica.erogatoreAssegnatoId(), "la sola disponibilità non assegna l'erogatore"));
    }

    @Test
    @DisplayName("AC2 - dopo la disponibilità il Banco può assegnare l'erogatore (#49)")
    void ac2_ilBancoPuoAssegnareLErogatoreDisponibile() {
        UUID richiestaId = richiestaPresaInCarico();
        ContattoDisponibilita contatto = contattoInAttesa(richiestaId, optErogatrice);
        richieste.rispondiDisponibilita(optErogatrice, contatto.id(), true);

        richieste.assegnaErogatore(banco, contatto.id());

        RichiestaDettaglio pratica = richieste.consultaRichiesta(banco, richiestaId);
        assertAll(
                () -> assertEquals(StatoRichiesta.IN_PROGRAMMAZIONE, pratica.richiesta().stato()),
                () -> assertEquals(optErogatrice.accountId(), pratica.erogatoreAssegnatoId()));
    }

    @Test
    @DisplayName("AC2 - risponde anche l'operatore del Banco interpellato")
    void ac2_rispostaPositivaDellOperatore() {
        UUID richiestaId = richiestaPresaInCarico();
        Optional<IdentitaAutenticata> operatore = operatoreCandidato(richiestaId);
        assumeTrue(operatore.isPresent(), "nessun operatore del Banco abilitato alla misura nei dati di prova");
        ContattoDisponibilita contatto = contattoInAttesa(richiestaId, operatore.get());

        richieste.rispondiDisponibilita(operatore.get(), contatto.id(), true);

        assertAll(
                () -> assertEquals(EsitoContatto.DISPONIBILE, esitoVistoDalBanco(richiestaId, contatto.id())),
                () -> assertEquals(StatoRichiesta.PRESA_IN_CARICO, statoVistoDalBanco(richiestaId)));
    }

    // ------------------------------------------------------------------ AC3

    @Test
    @DisplayName("AC3 - il rifiuto è visibile al Banco e la richiesta resta Presa in carico")
    void ac3_rifiutoVisibileAlBanco() {
        UUID richiestaId = richiestaPresaInCarico();
        ContattoDisponibilita contatto = contattoInAttesa(richiestaId, optErogatrice);

        ContattoDisponibilita risposta = richieste.rispondiDisponibilita(optErogatrice, contatto.id(), false);

        RichiestaDettaglio pratica = richieste.consultaRichiesta(banco, richiestaId);
        assertAll(
                () -> assertEquals(EsitoContatto.NON_DISPONIBILE, risposta.esito()),
                () -> assertEquals(EsitoContatto.NON_DISPONIBILE, esitoVistoDalBanco(richiestaId, contatto.id())),
                () -> assertEquals(StatoRichiesta.PRESA_IN_CARICO, pratica.richiesta().stato()),
                () -> assertNull(pratica.erogatoreAssegnatoId()));
    }

    @Test
    @DisplayName("AC3 - dopo il rifiuto il Banco può interpellare un altro candidato")
    void ac3_dopoIlRifiutoIlBancoInterpellaUnAltroCandidato() {
        UUID richiestaId = richiestaPresaInCarico();
        ContattoDisponibilita primo = contattoInAttesa(richiestaId, optErogatrice);
        richieste.rispondiDisponibilita(optErogatrice, primo.id(), false);

        ContattoDisponibilita secondo = contattoInAttesa(richiestaId, optErogatriceAlternativa);

        assertAll(
                () -> assertNotEquals(primo.id(), secondo.id()),
                () -> assertEquals(optErogatriceAlternativa.accountId(), secondo.erogatoreId()),
                () -> assertEquals(EsitoContatto.IN_ATTESA, secondo.esito()),
                () -> assertEquals(EsitoContatto.NON_DISPONIBILE, esitoVistoDalBanco(richiestaId, primo.id())),
                () -> assertEquals(StatoRichiesta.PRESA_IN_CARICO, statoVistoDalBanco(richiestaId)));
    }

    // ------------------------------------------------------------------ AC4

    @ParameterizedTest(name = "AC4 - {0} non può consultare un contatto indirizzato a un altro account")
    @EnumSource(value = Attore.class, names = "OPT_EROGATRICE", mode = EnumSource.Mode.EXCLUDE)
    void ac4_soloIlDestinatarioConsultaIlContatto(Attore estraneo) {
        UUID richiestaId = richiestaPresaInCarico();
        ContattoDisponibilita contatto = contattoInAttesa(richiestaId, optErogatrice);

        assertThrows(SecurityException.class, () -> richieste.consultaContatto(identita(estraneo), contatto.id()));

        assertAll(
                () -> assertEquals(EsitoContatto.IN_ATTESA, esitoVistoDalBanco(richiestaId, contatto.id())),
                () -> assertEquals(StatoRichiesta.PRESA_IN_CARICO, statoVistoDalBanco(richiestaId)));
    }

    @ParameterizedTest(name = "AC4 - {0} non può rispondere (disponibile = true) al posto del destinatario")
    @EnumSource(value = Attore.class, names = "OPT_EROGATRICE", mode = EnumSource.Mode.EXCLUDE)
    void ac4_soloIlDestinatarioRegistraLaDisponibilita(Attore estraneo) {
        assertRispostaNegataSenzaModifiche(estraneo, true);
    }

    @ParameterizedTest(name = "AC4 - {0} non può rispondere (disponibile = false) al posto del destinatario")
    @EnumSource(value = Attore.class, names = "OPT_EROGATRICE", mode = EnumSource.Mode.EXCLUDE)
    void ac4_soloIlDestinatarioRegistraIlRifiuto(Attore estraneo) {
        assertRispostaNegataSenzaModifiche(estraneo, false);
    }

    private void assertRispostaNegataSenzaModifiche(Attore estraneo, boolean disponibile) {
        UUID richiestaId = richiestaPresaInCarico();
        ContattoDisponibilita contatto = contattoInAttesa(richiestaId, optErogatrice);

        assertThrows(
                SecurityException.class,
                () -> richieste.rispondiDisponibilita(identita(estraneo), contatto.id(), disponibile));

        assertAll(
                () -> assertEquals(EsitoContatto.IN_ATTESA, esitoVistoDalBanco(richiestaId, contatto.id())),
                () -> assertEquals(StatoRichiesta.PRESA_IN_CARICO, statoVistoDalBanco(richiestaId)),
                () -> assertNull(richieste.consultaRichiesta(banco, richiestaId).erogatoreAssegnatoId()));

        // Il tentativo negato non deve aver consumato il contatto: il destinatario risponde ancora.
        assertEquals(
                EsitoContatto.DISPONIBILE,
                richieste.rispondiDisponibilita(optErogatrice, contatto.id(), true).esito());
    }

    // ------------------------------------------------------------------ precondizione di AC2 e AC3

    @Test
    @DisplayName("AC2/AC3 - si risponde solo a un contatto ancora in attesa: la prima risposta resta valida")
    void unaSecondaRispostaNonSostituisceLaPrima() {
        UUID richiestaId = richiestaPresaInCarico();
        ContattoDisponibilita contatto = contattoInAttesa(richiestaId, optErogatrice);
        richieste.rispondiDisponibilita(optErogatrice, contatto.id(), false);

        assertThrows(
                IllegalStateException.class,
                () -> richieste.rispondiDisponibilita(optErogatrice, contatto.id(), true));

        assertEquals(EsitoContatto.NON_DISPONIBILE, esitoVistoDalBanco(richiestaId, contatto.id()));
    }
}
