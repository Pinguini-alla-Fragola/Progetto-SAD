package it.unina.picus.comunicazioni.interno.persistenza;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Account che ha ricevuto una comunicazione, registrato al momento dell'invio. */
@Entity
@Table(name = "comunicazioni_destinatari")
public class DestinatarioComunicazioneJpa {
    @EmbeddedId
    private Chiave id;
    /** Vuota finché il destinatario non conferma la ricezione. */
    private Instant confermataIl;

    protected DestinatarioComunicazioneJpa() {
    }

    public DestinatarioComunicazioneJpa(UUID comunicazioneId, UUID destinatarioId) {
        this.id = new Chiave(comunicazioneId, destinatarioId);
    }

    /** Coppia comunicazione e account destinatario. */
    @Embeddable
    public record Chiave(UUID comunicazioneId, UUID destinatarioId) implements Serializable {}
}
