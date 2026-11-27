package it.unina.picus.richieste.interno.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.richieste.api.ConflittoRichiestaException;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;

/**
 * Transizioni della pratica usate dal coordinamento (#49): presa in carico esplicita e
 * assegnazione che registra insieme erogatore, ambito e In programmazione.
 */
@DisplayName("Transizioni di presa in carico e assegnazione della richiesta")
@Tag("unit")
class RichiestaDiMisuraTest {

    @Test
    @DisplayName("La presa in carico porta una richiesta Creata in Presa in carico senza cambiare altri dati")
    void presaInCaricoDaCreata() {
        var creata = richiesta(StatoRichiesta.CREATA);

        var presa = creata.prendiInCarico();

        assertThat(presa.stato()).isEqualTo(StatoRichiesta.PRESA_IN_CARICO);
        assertThat(presa).usingRecursiveComparison().ignoringFields("stato").isEqualTo(creata);
        assertThat(creata.stato()).as("la pratica originale è immutabile").isEqualTo(StatoRichiesta.CREATA);
    }

    @ParameterizedTest(name = "da {0}")
    @EnumSource(value = StatoRichiesta.class, names = "CREATA", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("La presa in carico è in conflitto con ogni stato diverso da Creata")
    void presaInCaricoRifiutataNegliAltriStati(StatoRichiesta stato) {
        var richiesta = richiesta(stato);

        assertThatThrownBy(richiesta::prendiInCarico).isInstanceOf(ConflittoRichiestaException.class);
    }

    @Test
    @DisplayName("Ricerca e contatti sono ammessi in Presa in carico")
    void verificaPresaInCaricoSuperata() {
        assertThatNoException().isThrownBy(() -> richiesta(StatoRichiesta.PRESA_IN_CARICO).verificaPresaInCarico());
    }

    @ParameterizedTest(name = "in {0}")
    @EnumSource(value = StatoRichiesta.class, names = "PRESA_IN_CARICO", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Ricerca e contatti sono in conflitto fuori da Presa in carico")
    void verificaPresaInCaricoFallita(StatoRichiesta stato) {
        var richiesta = richiesta(stato);

        assertThatThrownBy(richiesta::verificaPresaInCarico).isInstanceOf(ConflittoRichiestaException.class);
    }

    @Test
    @DisplayName("L'assegnazione registra insieme erogatore, ambito e In programmazione")
    void assegnazioneAtomica() {
        var presa = richiesta(StatoRichiesta.PRESA_IN_CARICO);
        UUID erogatore = UUID.randomUUID();

        var assegnata = presa.assegna(erogatore, AmbitoErogazione.COMUNE_LIMITROFO);

        assertThat(assegnata.stato()).isEqualTo(StatoRichiesta.IN_PROGRAMMAZIONE);
        assertThat(assegnata.erogatoreId()).isEqualTo(erogatore);
        assertThat(assegnata.erogatoreAmbito()).isEqualTo(AmbitoErogazione.COMUNE_LIMITROFO);
        assertThat(assegnata.accordo()).isNull();
        assertThat(assegnata).usingRecursiveComparison()
                .ignoringFields("stato", "erogatoreId", "erogatoreAmbito").isEqualTo(presa);
        assertThat(presa.erogatoreId()).as("la pratica originale resta senza assegnatario").isNull();
    }

    @ParameterizedTest(name = "da {0}")
    @EnumSource(value = StatoRichiesta.class, names = "PRESA_IN_CARICO", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("L'assegnazione è in conflitto fuori da Presa in carico")
    void assegnazioneRifiutataNegliAltriStati(StatoRichiesta stato) {
        var richiesta = richiesta(stato);

        assertThatThrownBy(() -> richiesta.assegna(UUID.randomUUID(), AmbitoErogazione.STESSO_COMUNE))
                .isInstanceOf(ConflittoRichiestaException.class);
    }

    @Test
    @DisplayName("L'assegnazione richiede sia l'erogatore sia l'ambito")
    void assegnazioneSenzaDatiObbligatori() {
        var presa = richiesta(StatoRichiesta.PRESA_IN_CARICO);

        assertThatThrownBy(() -> presa.assegna(null, AmbitoErogazione.STESSO_COMUNE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> presa.assegna(UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class);
    }

    private static RichiestaDiMisura richiesta(StatoRichiesta stato) {
        return new RichiestaDiMisura(UUID.randomUUID(), UUID.randomUUID(), 1L, TipoDestinatario.PERSONA, "napoli",
                "Descrizione sintetica", Instant.parse("2026-11-01T09:00:00Z"), stato, null, null, null, null);
    }
}
