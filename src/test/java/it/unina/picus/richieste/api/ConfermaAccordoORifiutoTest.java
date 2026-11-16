package it.unina.picus.richieste.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import it.unina.picus.richieste.api.RichiesteApi.AccordoADistanza;
import it.unina.picus.richieste.api.RichiesteApi.AccordoInPresenza;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.DettagliAccordo;
import it.unina.picus.richieste.api.RichiesteApi.EsitoContatto;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaDettaglio;
import it.unina.picus.richieste.api.RichiesteApi.RichiestaSintesi;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test di accettazione black box della User Story #52
 * «Conferma dell'accordo o rifiuto della richiesta».
 *
 * <p>Ogni test riporta nel nome l'Acceptance Criterion verificato (AC1..AC9, nell'ordine
 * dell'issue). A livello di contratto Java il diniego corrisponde a {@link SecurityException},
 * il dato mancante a {@link IllegalArgumentException} e lo stato non ammesso a
 * {@link IllegalStateException}.</p>
 */
@DisplayName("#52 Conferma dell'accordo o rifiuto della richiesta")
class ConfermaAccordoORifiutoTest extends ScenarioRichieste {

    // ------------------------------------------------------------------ AC1

    @Test
    @DisplayName("AC1 - accordo in presenza: Confermata e dettagli disponibili a OPT titolare ed erogatore")
    void ac1_confermaInPresenza() {
        UUID richiestaId = richiestaInProgrammazione(optErogatrice);
        AccordoInPresenza accordo = accordoInPresenza();

        RichiestaDettaglio esito = richieste.confermaAccordo(banco, richiestaId, accordo);

        assertAll(
                () -> assertEquals(StatoRichiesta.CONFERMATA, esito.richiesta().stato()),
                () -> assertEquals(accordo, esito.accordo()),
                () -> assertEquals(optErogatrice.accountId(), esito.erogatoreAssegnatoId()));
        assertConfermataPerTutti(richiestaId, optErogatrice, accordo);
    }

    // ------------------------------------------------------------------ AC2

    @Test
    @DisplayName("AC2 - accordo a distanza: Confermata e dettagli disponibili a OPT titolare ed erogatore")
    void ac2_confermaADistanza() {
        UUID richiestaId = richiestaInProgrammazione(optErogatrice);
        AccordoADistanza accordo = accordoADistanza();

        RichiestaDettaglio esito = richieste.confermaAccordo(banco, richiestaId, accordo);

        assertAll(
                () -> assertEquals(StatoRichiesta.CONFERMATA, esito.richiesta().stato()),
                () -> assertEquals(accordo, esito.accordo()));
        assertConfermataPerTutti(richiestaId, optErogatrice, accordo);
    }

    @Test
    @DisplayName("AC1 - i dettagli confermati sono disponibili anche all'operatore del Banco assegnato")
    void ac1_confermaConOperatoreAssegnato() {
        UUID richiestaId = richiestaPresaInCarico();
        Optional<IdentitaAutenticata> operatore = operatoreCandidato(richiestaId);
        assumeTrue(operatore.isPresent(), "nessun operatore del Banco abilitato alla misura nei dati di prova");
        ContattoDisponibilita contatto = contattoInAttesa(richiestaId, operatore.get());
        richieste.rispondiDisponibilita(operatore.get(), contatto.id(), true);
        richieste.assegnaErogatore(banco, contatto.id());
        AccordoInPresenza accordo = accordoInPresenza();

        richieste.confermaAccordo(banco, richiestaId, accordo);

        assertConfermataPerTutti(richiestaId, operatore.get(), accordo);
    }

    private void assertConfermataPerTutti(UUID richiestaId, IdentitaAutenticata erogatore, DettagliAccordo accordo) {
        RichiestaDettaglio perBanco = richieste.consultaRichiesta(banco, richiestaId);
        RichiestaDettaglio perTitolare = richieste.consultaRichiesta(optTitolare, richiestaId);
        RichiestaDettaglio perErogatore = richieste.consultaRichiesta(erogatore, richiestaId);
        assertAll(
                () -> assertEquals(StatoRichiesta.CONFERMATA, perBanco.richiesta().stato()),
                () -> assertEquals(accordo, perBanco.accordo()),
                () -> assertEquals(StatoRichiesta.CONFERMATA, perTitolare.richiesta().stato()),
                () -> assertEquals(accordo, perTitolare.accordo(), "la OPT titolare deve vedere i dettagli"),
                () -> assertEquals(StatoRichiesta.CONFERMATA, perErogatore.richiesta().stato()),
                () -> assertEquals(accordo, perErogatore.accordo(), "l'erogatore assegnato deve vedere i dettagli"),
                () -> assertTrue(
                        richieste.elencoRichiesteOpt(optTitolare).stream()
                                .anyMatch(riga -> riga.id().equals(richiestaId)
                                        && riga.stato() == StatoRichiesta.CONFERMATA),
                        "l'elenco della OPT titolare deve mostrare la richiesta Confermata"));
    }

    // ------------------------------------------------------------------ AC3

    static Stream<Arguments> accordiIncompleti() {
        Instant quando = appuntamento();
        URI link = URI.create("https://videochiamata.example/stanza-di-prova");
        return Stream.of(
                Arguments.of("in presenza senza data e ora", new AccordoInPresenza(null, "Sede", "Nome", "Cognome", "0810000000")),
                Arguments.of("in presenza senza luogo", new AccordoInPresenza(quando, null, "Nome", "Cognome", "0810000000")),
                Arguments.of("in presenza con luogo vuoto", new AccordoInPresenza(quando, "  ", "Nome", "Cognome", "0810000000")),
                Arguments.of("in presenza senza nome del referente", new AccordoInPresenza(quando, "Sede", null, "Cognome", "0810000000")),
                Arguments.of("in presenza con nome del referente vuoto", new AccordoInPresenza(quando, "Sede", "", "Cognome", "0810000000")),
                Arguments.of("in presenza senza cognome del referente", new AccordoInPresenza(quando, "Sede", "Nome", null, "0810000000")),
                Arguments.of("in presenza con cognome del referente vuoto", new AccordoInPresenza(quando, "Sede", "Nome", " ", "0810000000")),
                Arguments.of("in presenza senza telefono", new AccordoInPresenza(quando, "Sede", "Nome", "Cognome", null)),
                Arguments.of("in presenza con telefono vuoto", new AccordoInPresenza(quando, "Sede", "Nome", "Cognome", "")),
                Arguments.of("a distanza senza data e ora", new AccordoADistanza(null, link)),
                Arguments.of("a distanza senza link", new AccordoADistanza(quando, null)),
                Arguments.of("senza alcun dettaglio", null));
    }

    @ParameterizedTest(name = "AC3 - accordo {0}: errore, resta In programmazione, nessun accordo confermato")
    @MethodSource("accordiIncompleti")
    void ac3_datoObbligatorioMancante(String caso, DettagliAccordo incompleto) {
        UUID richiestaId = richiestaInProgrammazione(optErogatrice);

        IllegalArgumentException errore = assertThrows(
                IllegalArgumentException.class, () -> richieste.confermaAccordo(banco, richiestaId, incompleto));

        assertAll(
                () -> assertNotNull(errore.getMessage(), "l'errore deve indicare cosa completare"),
                () -> assertFalse(errore.getMessage().isBlank(), "l'errore deve indicare cosa completare"),
                () -> assertSenzaAccordo(richiestaId, StatoRichiesta.IN_PROGRAMMAZIONE),
                () -> assertNull(richieste.consultaRichiesta(optTitolare, richiestaId).accordo()),
                () -> assertNull(richieste.consultaRichiesta(optErogatrice, richiestaId).accordo()));
    }

    @Test
    @DisplayName("AC3 - dopo l'errore il Banco completa i dati e la conferma riesce")
    void ac3_dopoLaCorrezioneLaConfermaRiesce() {
        UUID richiestaId = richiestaInProgrammazione(optErogatrice);
        assertThrows(
                IllegalArgumentException.class,
                () -> richieste.confermaAccordo(banco, richiestaId, new AccordoADistanza(appuntamento(), null)));

        AccordoADistanza completo = accordoADistanza();
        richieste.confermaAccordo(banco, richiestaId, completo);

        assertConfermataPerTutti(richiestaId, optErogatrice, completo);
    }

    // ------------------------------------------------------------------ AC4

    @Test
    @DisplayName("AC4 - prima della conferma esplicita del Banco la richiesta resta In programmazione")
    void ac4_senzaConfermaRestaInProgrammazione() {
        UUID richiestaId = richiestaInProgrammazione(optErogatrice);

        // Il contratto non espone lo scambio di proposte del modulo di contatto: si verifica
        // che nessuna consultazione delle tre parti faccia avanzare lo stato o produca un accordo.
        RichiestaDettaglio perBanco = richieste.consultaRichiesta(banco, richiestaId);
        RichiestaDettaglio perTitolare = richieste.consultaRichiesta(optTitolare, richiestaId);
        RichiestaDettaglio perErogatore = richieste.consultaRichiesta(optErogatrice, richiestaId);

        assertAll(
                () -> assertEquals(StatoRichiesta.IN_PROGRAMMAZIONE, perBanco.richiesta().stato()),
                () -> assertEquals(StatoRichiesta.IN_PROGRAMMAZIONE, perTitolare.richiesta().stato()),
                () -> assertEquals(StatoRichiesta.IN_PROGRAMMAZIONE, perErogatore.richiesta().stato()),
                () -> assertNull(perBanco.accordo()),
                () -> assertNull(perTitolare.accordo()),
                () -> assertNull(perErogatore.accordo()),
                () -> assertSenzaAccordo(richiestaId, StatoRichiesta.IN_PROGRAMMAZIONE));
    }

    // ------------------------------------------------------------------ AC5

    @Test
    @DisplayName("AC5 - rifiuto definitivo da Presa in carico: Rifiutata, visibile alla OPT titolare")
    void ac5_rifiutoDaPresaInCarico() {
        UUID richiestaId = richiestaPresaInCarico();

        RichiestaSintesi esito = richieste.rifiutaRichiesta(banco, richiestaId);

        assertEquals(StatoRichiesta.RIFIUTATA, esito.stato());
        assertRifiutataPerLaTitolare(richiestaId);
    }

    @Test
    @DisplayName("AC5 - rifiuto definitivo da In programmazione: Rifiutata, visibile alla OPT titolare")
    void ac5_rifiutoDaInProgrammazione() {
        UUID richiestaId = richiestaInProgrammazione(optErogatrice);

        RichiestaSintesi esito = richieste.rifiutaRichiesta(banco, richiestaId);

        assertEquals(StatoRichiesta.RIFIUTATA, esito.stato());
        assertRifiutataPerLaTitolare(richiestaId);
    }

    @Test
    @DisplayName("AC5 - rifiuto definitivo dopo che tutti i candidati interpellati hanno declinato")
    void ac5_rifiutoDopoIDinieghiDeiCandidati() {
        UUID richiestaId = richiestaPresaInCarico();
        ContattoDisponibilita primo = contattoInAttesa(richiestaId, optErogatrice);
        richieste.rispondiDisponibilita(optErogatrice, primo.id(), false);
        ContattoDisponibilita secondo = contattoInAttesa(richiestaId, optErogatriceAlternativa);
        richieste.rispondiDisponibilita(optErogatriceAlternativa, secondo.id(), false);

        richieste.rifiutaRichiesta(banco, richiestaId);

        assertRifiutataPerLaTitolare(richiestaId);
    }

    @ParameterizedTest(name = "AC5 - {0} non può registrare il rifiuto definitivo, che spetta al solo Banco")
    @EnumSource(value = Attore.class, names = "BANCO", mode = EnumSource.Mode.EXCLUDE)
    void ac5_soloIlBancoRifiuta(Attore nonBanco) {
        UUID richiestaId = richiestaInProgrammazione(optErogatrice);

        assertThrows(SecurityException.class, () -> richieste.rifiutaRichiesta(identita(nonBanco), richiestaId));

        assertSenzaAccordo(richiestaId, StatoRichiesta.IN_PROGRAMMAZIONE);
    }

    private void assertRifiutataPerLaTitolare(UUID richiestaId) {
        RichiestaDettaglio perTitolare = richieste.consultaRichiesta(optTitolare, richiestaId);
        assertAll(
                () -> assertEquals(StatoRichiesta.RIFIUTATA, perTitolare.richiesta().stato()),
                () -> assertNull(perTitolare.accordo()),
                () -> assertTrue(
                        richieste.elencoRichiesteOpt(optTitolare).stream()
                                .anyMatch(riga -> riga.id().equals(richiestaId)
                                        && riga.stato() == StatoRichiesta.RIFIUTATA),
                        "l'elenco della OPT titolare deve mostrare la richiesta Rifiutata"),
                () -> assertEquals(StatoRichiesta.RIFIUTATA, statoVistoDalBanco(richiestaId)));
    }

    // ------------------------------------------------------------------ AC6

    @Test
    @DisplayName("AC6 - il diniego di un candidato lascia Presa in carico e il Banco può cercare un'alternativa")
    void ac6_ilDiniegoDiUnCandidatoNonRifiutaLaRichiesta() {
        UUID richiestaId = richiestaPresaInCarico();
        ContattoDisponibilita contatto = contattoInAttesa(richiestaId, optErogatrice);

        richieste.rispondiDisponibilita(optErogatrice, contatto.id(), false);

        assertAll(
                () -> assertEquals(StatoRichiesta.PRESA_IN_CARICO, statoVistoDalBanco(richiestaId)),
                () -> assertEquals(
                        StatoRichiesta.PRESA_IN_CARICO,
                        richieste.consultaRichiesta(optTitolare, richiestaId).richiesta().stato()),
                () -> assertTrue(
                        richieste.cercaErogatoriOrdinati(banco, richiestaId).stream()
                                .anyMatch(candidato -> candidato.erogatore().accountId()
                                        .equals(optErogatriceAlternativa.accountId())),
                        "il Banco deve poter ancora cercare fra i candidati compatibili"));

        ContattoDisponibilita alternativa = contattoInAttesa(richiestaId, optErogatriceAlternativa);
        assertEquals(EsitoContatto.IN_ATTESA, alternativa.esito());
    }

    // ------------------------------------------------------------------ AC7

    @Test
    @DisplayName("AC7 - dopo la conferma i dettagli dell'accordo non sono modificabili")
    void ac7_accordoConfermatoImmutabile() {
        UUID richiestaId = richiestaInProgrammazione(optErogatrice);
        AccordoInPresenza originale = accordoInPresenza();
        richieste.confermaAccordo(banco, richiestaId, originale);
        AccordoInPresenza modificato = new AccordoInPresenza(
                originale.dataOra().plusSeconds(3600), "Altra sede", "Altro", "Referente", "0811111111");

        assertAll(
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> richieste.confermaAccordo(banco, richiestaId, modificato)),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> richieste.confermaAccordo(banco, richiestaId, accordoADistanza())));

        assertConfermataPerTutti(richiestaId, optErogatrice, originale);
    }

    // ------------------------------------------------------------------ AC8

    @ParameterizedTest(name = "AC8 - {0} non può confermare l'accordo, anche se assegnato")
    @EnumSource(value = Attore.class, names = "BANCO", mode = EnumSource.Mode.EXCLUDE)
    void ac8_soloIlBancoConferma(Attore nonBanco) {
        UUID richiestaId = richiestaInProgrammazione(optErogatrice);

        assertThrows(
                SecurityException.class,
                () -> richieste.confermaAccordo(identita(nonBanco), richiestaId, accordoInPresenza()));

        assertAll(
                () -> assertSenzaAccordo(richiestaId, StatoRichiesta.IN_PROGRAMMAZIONE),
                () -> assertEquals(
                        optErogatrice.accountId(),
                        richieste.consultaRichiesta(banco, richiestaId).erogatoreAssegnatoId()));
    }

    @Test
    @DisplayName("AC8 - l'operatore del Banco assegnato non può confermare l'accordo")
    void ac8_operatoreAssegnatoNonConferma() {
        UUID richiestaId = richiestaPresaInCarico();
        Optional<IdentitaAutenticata> operatore = operatoreCandidato(richiestaId);
        assumeTrue(operatore.isPresent(), "nessun operatore del Banco abilitato alla misura nei dati di prova");
        ContattoDisponibilita contatto = contattoInAttesa(richiestaId, operatore.get());
        richieste.rispondiDisponibilita(operatore.get(), contatto.id(), true);
        richieste.assegnaErogatore(banco, contatto.id());

        assertThrows(
                SecurityException.class,
                () -> richieste.confermaAccordo(operatore.get(), richiestaId, accordoInPresenza()));

        assertSenzaAccordo(richiestaId, StatoRichiesta.IN_PROGRAMMAZIONE);
    }

    // ------------------------------------------------------------------ AC9

    @Test
    @DisplayName("AC9 - una OPT estranea non consulta i dettagli di una richiesta Confermata")
    void ac9_optEstraneaNonConsultaLaRichiestaConfermata() {
        UUID richiestaId = richiestaInProgrammazione(optErogatrice);
        richieste.confermaAccordo(banco, richiestaId, accordoInPresenza());

        assertThrows(SecurityException.class, () -> richieste.consultaRichiesta(optEstranea, richiestaId));

        assertNonCompareNegliElenchiDellaOptEstranea(richiestaId);
    }

    @Test
    @DisplayName("AC9 - una OPT estranea non consulta nemmeno una richiesta In programmazione")
    void ac9_optEstraneaNonConsultaLaRichiestaInProgrammazione() {
        UUID richiestaId = richiestaInProgrammazione(optErogatrice);

        assertThrows(SecurityException.class, () -> richieste.consultaRichiesta(optEstranea, richiestaId));

        assertNonCompareNegliElenchiDellaOptEstranea(richiestaId);
    }

    @Test
    @DisplayName("AC9 - una OPT soltanto interpellata, che ha declinato, non consulta i dettagli confermati")
    void ac9_optInterpellataMaNonAssegnataNonConsulta() {
        UUID richiestaId = richiestaPresaInCarico();
        ContattoDisponibilita declinato = contattoInAttesa(richiestaId, optErogatriceAlternativa);
        richieste.rispondiDisponibilita(optErogatriceAlternativa, declinato.id(), false);
        ContattoDisponibilita accettato = contattoInAttesa(richiestaId, optErogatrice);
        richieste.rispondiDisponibilita(optErogatrice, accettato.id(), true);
        richieste.assegnaErogatore(banco, accettato.id());
        richieste.confermaAccordo(banco, richiestaId, accordoInPresenza());

        assertThrows(
                SecurityException.class, () -> richieste.consultaRichiesta(optErogatriceAlternativa, richiestaId));
    }

    private void assertNonCompareNegliElenchiDellaOptEstranea(UUID richiestaId) {
        assertAll(
                () -> assertTrue(
                        richieste.elencoRichiesteOpt(optEstranea).stream()
                                .noneMatch(riga -> riga.id().equals(richiestaId))),
                () -> assertTrue(
                        richieste.elencoAssegnazioni(optEstranea).stream()
                                .noneMatch(riga -> riga.id().equals(richiestaId))));
    }
}
