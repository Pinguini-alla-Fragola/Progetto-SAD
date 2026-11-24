package it.unina.picus.richieste.interno.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;

class RegoleAnnullamentoTest {

    @ParameterizedTest
    @ValueSource(strings = {"Motivo \uD800", "Motivo \uDC00"})
    void rifiutaSurrogatiIsolatiNelMotivo(String motivo) {
        assertThatExceptionOfType(DatiRichiestaNonValidiException.class)
                .isThrownBy(() -> RegoleAnnullamento.motivoDaRegistrare(motivo))
                .satisfies(errore -> assertThat(errore.campi()).containsOnlyKeys("motivo"));
    }

    @Test
    void conservaLeCoppieUnicodeValide() {
        String motivo = "Motivo \uD83D\uDE00";
        assertThat(RegoleAnnullamento.motivoDaRegistrare(motivo)).isEqualTo(motivo);
    }
}
