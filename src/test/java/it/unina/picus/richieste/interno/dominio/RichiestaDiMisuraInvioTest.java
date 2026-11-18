package it.unina.picus.richieste.interno.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;

/**
 * Creazione della pratica all'invio (#48, criterio 1 della #99) e riconoscimento dei ritenti.
 */
@DisplayName("Creazione della richiesta all'invio")
class RichiestaDiMisuraInvioTest {

    private static final Instant ISTANTE = Instant.parse("2026-11-17T10:00:00Z");

    @Test
    @DisplayName("Una richiesta inviata è Creata, senza erogatore né accordo, con titolare e istante ricevuti")
    void inviataInCreata() {
        UUID titolare = UUID.randomUUID();
        UUID riferimento = UUID.randomUUID();
        var dati = new DatiInvio(4L, TipoDestinatario.NUCLEO, "napoli", "Bisogno generico");

        var richiesta = RichiestaDiMisura.inviata(titolare, dati, ISTANTE, riferimento);

        assertThat(richiesta.id()).isNotNull();
        assertThat(richiesta.titolareId()).isEqualTo(titolare);
        assertThat(richiesta.misuraId()).isEqualTo(4L);
        assertThat(richiesta.tipoDestinatario()).isEqualTo(TipoDestinatario.NUCLEO);
        assertThat(richiesta.comuneCodice()).isEqualTo("napoli");
        assertThat(richiesta.description()).isEqualTo("Bisogno generico");
        assertThat(richiesta.inviataIl()).isEqualTo(ISTANTE);
        assertThat(richiesta.stato()).isEqualTo(StatoRichiesta.CREATA);
        assertThat(richiesta.erogatoreId()).isNull();
        assertThat(richiesta.erogatoreAmbito()).isNull();
        assertThat(richiesta.accordo()).isNull();
        assertThat(richiesta.riferimentoInvio()).isEqualTo(riferimento);
    }

    @Test
    @DisplayName("Ogni invio riceve un identificativo distinto, anche con dati uguali")
    void identificativiDistinti() {
        var dati = new DatiInvio(1L, TipoDestinatario.PERSONA, "napoli", null);
        UUID titolare = UUID.randomUUID();

        assertThat(RichiestaDiMisura.inviata(titolare, dati, ISTANTE, null).id())
                .isNotEqualTo(RichiestaDiMisura.inviata(titolare, dati, ISTANTE, null).id());
    }

    @Test
    @DisplayName("Lo stesso invio è riconosciuto solo se misura, destinatario, comune e descrizione coincidono")
    void stessoInvio() {
        var dati = new DatiInvio(1L, TipoDestinatario.PERSONA, "napoli", "Testo");
        var richiesta = RichiestaDiMisura.inviata(UUID.randomUUID(), dati, ISTANTE, UUID.randomUUID());

        assertThat(richiesta.stessoInvio(dati)).isTrue();
        assertThat(richiesta.stessoInvio(new DatiInvio(2L, TipoDestinatario.PERSONA, "napoli", "Testo"))).isFalse();
        assertThat(richiesta.stessoInvio(new DatiInvio(1L, TipoDestinatario.NUCLEO, "napoli", "Testo"))).isFalse();
        assertThat(richiesta.stessoInvio(new DatiInvio(1L, TipoDestinatario.PERSONA, "portici", "Testo"))).isFalse();
        assertThat(richiesta.stessoInvio(new DatiInvio(1L, TipoDestinatario.PERSONA, "napoli", null))).isFalse();
    }
}
