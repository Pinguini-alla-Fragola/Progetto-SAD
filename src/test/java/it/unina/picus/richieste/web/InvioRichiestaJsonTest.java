package it.unina.picus.richieste.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;

/**
 * Lettura del corpo di invio (#48, criteri 1, 3 e 4 della #99): solo i campi del modulo, tipi
 * controllati e messaggi d'errore che non riportano mai i valori ricevuti.
 */
@DisplayName("Lettura del corpo JSON di invio")
class InvioRichiestaJsonTest {

    @Test
    @DisplayName("Un corpo con i soli campi ammessi produce i dati di invio")
    void corpoValido() {
        var dati = InvioRichiestaJson.leggi(corpo(Map.of("misuraId", 3, "tipoDestinatario", "NUCLEO",
                "comuneCodice", "napoli", "description", "Bisogno generico")));

        assertThat(dati).isEqualTo(new DatiInvio(3L, TipoDestinatario.NUCLEO, "napoli", "Bisogno generico"));
    }

    @Test
    @DisplayName("La description è facoltativa e un identificativo Long è accettato")
    void descriptionFacoltativa() {
        var dati = InvioRichiestaJson.leggi(corpo(Map.of("misuraId", 7L, "tipoDestinatario", "PERSONA",
                "comuneCodice", "portici")));

        assertThat(dati).isEqualTo(new DatiInvio(7L, TipoDestinatario.PERSONA, "portici", null));
    }

    @ParameterizedTest(name = "campo {0}")
    @ValueSource(strings = {"codiceFiscale", "nomeAssistito", "titolareId", "stato", "id", "erogatoreId"})
    @DisplayName("Un campo non previsto respinge l'intero corpo senza riportarne il valore")
    void campoExtra(String campo) {
        var valori = corpo(Map.of("misuraId", 1, "tipoDestinatario", "PERSONA", "comuneCodice", "napoli"));
        valori.put(campo, "VALORE-SINTETICO-RISERVATO");

        assertThatThrownBy(() -> InvioRichiestaJson.leggi(valori))
                .isInstanceOfSatisfying(DatiRichiestaNonValidiException.class, ex -> {
                    assertThat(ex.campi()).containsOnlyKeys("richiesta");
                    assertThat(ex.campi().toString()).doesNotContain("VALORE-SINTETICO-RISERVATO");
                    assertThat(ex.getMessage()).doesNotContain("VALORE-SINTETICO-RISERVATO");
                });
    }

    @Test
    @DisplayName("Un corpo assente è respinto")
    void corpoAssente() {
        assertThatThrownBy(() -> InvioRichiestaJson.leggi(null)).isInstanceOf(DatiRichiestaNonValidiException.class);
    }

    @Test
    @DisplayName("Tipi errati producono un errore per ciascun campo, senza valori")
    void tipiErrati() {
        var valori = corpo(Map.of("misuraId", "1", "tipoDestinatario", "FAMIGLIA", "comuneCodice", 42,
                "description", 99));

        assertThatThrownBy(() -> InvioRichiestaJson.leggi(valori))
                .isInstanceOfSatisfying(DatiRichiestaNonValidiException.class, ex -> {
                    assertThat(ex.campi()).containsOnlyKeys("misuraId", "tipoDestinatario", "comuneCodice", "description");
                    assertThat(ex.campi().values()).noneMatch(m -> m.contains("FAMIGLIA") || m.contains("42"));
                });
    }

    @Test
    @DisplayName("Un identificativo di misura decimale non è accettato")
    void misuraDecimale() {
        var valori = corpo(Map.of("misuraId", 1.5, "tipoDestinatario", "PERSONA", "comuneCodice", "napoli"));

        assertThatThrownBy(() -> InvioRichiestaJson.leggi(valori))
                .isInstanceOfSatisfying(DatiRichiestaNonValidiException.class,
                        ex -> assertThat(ex.campi()).containsOnlyKeys("misuraId"));
    }

    @Test
    @DisplayName("Un corpo vuoto segnala tutti i campi obbligatori mancanti")
    void campiObbligatoriMancanti() {
        assertThatThrownBy(() -> InvioRichiestaJson.leggi(Map.of()))
                .isInstanceOfSatisfying(DatiRichiestaNonValidiException.class, ex -> assertThat(ex.campi())
                        .containsOnlyKeys("misuraId", "tipoDestinatario", "comuneCodice"));
    }

    @Test
    @DisplayName("Idempotency-Key: assente, UUID canonico o maiuscolo sono accettati")
    void riferimentoValido() {
        UUID riferimento = UUID.randomUUID();

        assertThat(InvioRichiestaJson.riferimento(null)).isNull();
        assertThat(InvioRichiestaJson.riferimento(riferimento.toString())).isEqualTo(riferimento);
        assertThat(InvioRichiestaJson.riferimento(riferimento.toString().toUpperCase())).isEqualTo(riferimento);
    }

    @ParameterizedTest(name = "riferimento \"{0}\"")
    @ValueSource(strings = {"", "non-un-uuid", "1-1-1-1-1", "123e4567e89b12d3a456426614174000"})
    @DisplayName("Idempotency-Key non canonico è un dato non valido")
    void riferimentoNonValido(String valore) {
        assertThatThrownBy(() -> InvioRichiestaJson.riferimento(valore))
                .isInstanceOfSatisfying(DatiRichiestaNonValidiException.class,
                        ex -> assertThat(ex.campi()).containsOnlyKeys("riferimentoInvio"));
    }

    private static Map<String, Object> corpo(Map<String, Object> valori) {
        return new HashMap<>(valori);
    }
}
