package it.unina.picus.utenze.interno.applicazione;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/*
Regola concordata per la nuova password (US #33, AC5), la stessa dell'attivazione: almeno 12 caratteri Unicode e al
massimo 72 byte UTF-8. I limiti sono scritti qui come valori e non letti da PoliticaPassword: se la regola cambiasse,
questi test devono fallire.
*/
@DisplayName("Regola della nuova password: 12 caratteri, 72 byte (#33)")
class PoliticaPasswordTest {

    private static final String EMOJI = "😀"; // fuori dal piano base: 1 carattere, 2 unità UTF-16, 4 byte

    static Stream<Arguments> accettate() {
        return Stream.of(
                Arguments.of("12 caratteri ASCII", "a".repeat(12)),
                Arguments.of("72 byte ASCII", "a".repeat(72)),
                Arguments.of("36 caratteri da 2 byte (72 byte)", "è".repeat(36)),
                Arguments.of("12 simboli fuori dal piano base (48 byte)", EMOJI.repeat(12)),
                Arguments.of("spazi interni", "una frase con spazi"));
    }

    @ParameterizedTest(name = "accettata: {0}")
    @MethodSource("accettate")
    void passwordConforme(String caso, String password) {
        assertDoesNotThrow(() -> PoliticaPassword.verifica(password), caso);
    }

    static Stream<Arguments> rifiutate() {
        return Stream.of(
                Arguments.of("assente", null, "obbligatoria"),
                Arguments.of("vuota", "", "obbligatoria"),
                Arguments.of("solo spazi", " ".repeat(20), "obbligatoria"),
                Arguments.of("11 caratteri", "a".repeat(11), "almeno 12 caratteri"),
                Arguments.of("6 simboli fuori dal piano base: 12 unità UTF-16 ma 6 caratteri", EMOJI.repeat(6),
                        "almeno 12 caratteri"),
                Arguments.of("11 simboli fuori dal piano base", EMOJI.repeat(11), "almeno 12 caratteri"),
                Arguments.of("73 byte ASCII", "a".repeat(73), "72 byte"),
                Arguments.of("37 caratteri da 2 byte (74 byte)", "è".repeat(37), "72 byte"),
                Arguments.of("19 simboli fuori dal piano base (76 byte)", EMOJI.repeat(19), "72 byte"),
                Arguments.of("testo molto lungo", "a".repeat(100_000), "72 byte"));
    }

    @ParameterizedTest(name = "rifiutata: {0}")
    @MethodSource("rifiutate")
    void passwordNonConforme(String caso, String password, String cosaCorreggere) {
        PasswordNonValidaException errore =
                assertThrows(PasswordNonValidaException.class, () -> PoliticaPassword.verifica(password), caso);

        assertTrue(errore.getMessage().contains(cosaCorreggere), caso + ": " + errore.getMessage());
        if (password != null && !password.isBlank()) {
            assertFalse(errore.getMessage().contains(password), "il messaggio riporta la password");
        }
    }

    @Test
    @DisplayName("Il limite è in byte UTF-8: 72 byte con caratteri misti passano, uno in più no")
    void confineInByteConCaratteriMisti() {
        String settantadue = "è".repeat(30) + "a".repeat(12); // 60 + 12 byte
        assertTrue(settantadue.getBytes(StandardCharsets.UTF_8).length == 72);

        assertDoesNotThrow(() -> PoliticaPassword.verifica(settantadue));
        assertThrows(PasswordNonValidaException.class, () -> PoliticaPassword.verifica(settantadue + "a"));
    }

    @Test
    @DisplayName("L'errore resta un IllegalArgumentException, come si aspetta chi gestiva l'attivazione")
    void compatibileConLaGestioneDellAttivazione() {
        assertInstanceOf(IllegalArgumentException.class, new PasswordNonValidaException("x"));
    }
}