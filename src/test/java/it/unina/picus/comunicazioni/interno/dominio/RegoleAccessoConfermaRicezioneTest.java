package it.unina.picus.comunicazioni.interno.dominio;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * Test black box di {@link RegoleAccessoComunicazione#verificaConfermaRicezione} (#145, codice della #143),
 * ricavati dal solo Javadoc del metodo.
 *
 * Classi di equivalenza: attore destinatario (OPT o operatore), attore non destinatario dello stesso ruolo,
 * Banco anche se presente fra i destinatari, identità incompleta, insieme dei destinatari vuoto o assente.
 */
@Tag("issue-145")
@DisplayName("#145 Regola di accesso alla conferma di ricezione")
class RegoleAccessoConfermaRicezioneTest {

    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID ALTRO_ACCOUNT = UUID.randomUUID();

    @ParameterizedTest(name = "{0} destinatario della comunicazione: conferma consentita")
    @EnumSource(value = RuoloUtente.class, names = {"OPT", "OPERATORE"})
    void destinatarioPuoConfermare(RuoloUtente ruolo) {
        var attore = new IdentitaAutenticata(ACCOUNT, ruolo);

        assertThatCode(() -> RegoleAccessoComunicazione.verificaConfermaRicezione(attore, Set.of(ACCOUNT, ALTRO_ACCOUNT)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} non destinatario: SecurityException")
    @EnumSource(value = RuoloUtente.class, names = {"OPT", "OPERATORE"})
    void nonDestinatarioNonPuoConfermare(RuoloUtente ruolo) {
        var attore = new IdentitaAutenticata(ACCOUNT, ruolo);

        assertThatThrownBy(() -> RegoleAccessoComunicazione.verificaConfermaRicezione(attore, Set.of(ALTRO_ACCOUNT)))
                .isInstanceOf(SecurityException.class);
    }

    @ParameterizedTest(name = "{0} con una comunicazione senza destinatari: SecurityException")
    @EnumSource(RuoloUtente.class)
    void nessunoConfermaSenzaDestinatari(RuoloUtente ruolo) {
        var attore = new IdentitaAutenticata(ACCOUNT, ruolo);

        assertThatThrownBy(() -> RegoleAccessoComunicazione.verificaConfermaRicezione(attore, Set.of()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Il Banco non conferma al posto dei destinatari, nemmeno se il suo account è nell'elenco")
    void bancoNonConferma() {
        var banco = new IdentitaAutenticata(ACCOUNT, RuoloUtente.BANCO);

        assertThatThrownBy(() -> RegoleAccessoComunicazione.verificaConfermaRicezione(banco, Set.of(ALTRO_ACCOUNT)))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> RegoleAccessoComunicazione.verificaConfermaRicezione(banco, Set.of(ACCOUNT)))
                .isInstanceOf(SecurityException.class);
    }

    static Stream<Arguments> identitaIncomplete() {
        return Stream.of(
                Arguments.of("identità assente", null),
                Arguments.of("account assente", new IdentitaAutenticata(null, RuoloUtente.OPT)),
                Arguments.of("ruolo assente", new IdentitaAutenticata(ACCOUNT, null)));
    }

    @ParameterizedTest(name = "Identità incompleta ({0}): SecurityException")
    @MethodSource("identitaIncomplete")
    void identitaIncompletaNonConferma(String caso, IdentitaAutenticata attore) {
        assertThatThrownBy(() -> RegoleAccessoComunicazione.verificaConfermaRicezione(attore, Set.of(ACCOUNT)))
                .isInstanceOf(SecurityException.class);
    }

    @ParameterizedTest(name = "Identità incompleta ({0}) e destinatari assenti: prevale il diniego")
    @MethodSource("identitaIncomplete")
    void identitaIncompletaSenzaDestinatari(String caso, IdentitaAutenticata attore) {
        assertThatThrownBy(() -> RegoleAccessoComunicazione.verificaConfermaRicezione(attore, null))
                .isInstanceOf(SecurityException.class);
    }

    @ParameterizedTest(name = "{0} con identità completa e destinatari assenti: NullPointerException")
    @EnumSource(RuoloUtente.class)
    void destinatariAssentiPerIdentitaCompleta(RuoloUtente ruolo) {
        var attore = new IdentitaAutenticata(ACCOUNT, ruolo);

        assertThatThrownBy(() -> RegoleAccessoComunicazione.verificaConfermaRicezione(attore, null))
                .isInstanceOf(NullPointerException.class);
    }
}