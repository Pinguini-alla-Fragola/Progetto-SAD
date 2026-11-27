package it.unina.picus.comunicazioni.interno.dominio;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * Test della regola sui ruoli usata dalla conferma. L'appartenenza alla singola comunicazione
 * è verificata dalla porta dei riscontri e dai test di integrazione.
 */
@Tag("issue-145")
@DisplayName("#145 Regola di accesso alla conferma di ricezione")
@Tag("unit")
class RegoleAccessoConfermaRicezioneTest {

    private static final UUID ACCOUNT = UUID.randomUUID();

    @ParameterizedTest(name = "{0} può essere un destinatario")
    @EnumSource(value = RuoloUtente.class, names = {"OPT", "OPERATORE"})
    void ammetteIRuoliDestinatari(RuoloUtente ruolo) {
        var attore = new IdentitaAutenticata(ACCOUNT, ruolo);

        assertThatCode(() -> RegoleAccessoComunicazione.verificaPossibileDestinatario(attore))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Il Banco non può essere un destinatario")
    void negaIlBanco() {
        var banco = new IdentitaAutenticata(ACCOUNT, RuoloUtente.BANCO);

        assertThatThrownBy(() -> RegoleAccessoComunicazione.verificaPossibileDestinatario(banco))
                .isInstanceOf(SecurityException.class);
    }

    static Stream<IdentitaAutenticata> identitaIncomplete() {
        return Stream.of(null, new IdentitaAutenticata(null, RuoloUtente.OPT),
                new IdentitaAutenticata(ACCOUNT, null));
    }

    @ParameterizedTest
    @MethodSource("identitaIncomplete")
    void negaLeIdentitaIncomplete(IdentitaAutenticata attore) {
        assertThatThrownBy(() -> RegoleAccessoComunicazione.verificaPossibileDestinatario(attore))
                .isInstanceOf(SecurityException.class);
    }
}
