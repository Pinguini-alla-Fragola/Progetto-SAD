package it.unina.picus.utenze.interno.persistenza;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

/** Misura erogabile dal profilo personale di un operatore del Banco. */
@Entity
@Table(name = "utenze_operatori_misure")
public class OperatoreMisuraJpa {
    @EmbeddedId
    private Chiave id;

    protected OperatoreMisuraJpa() {}

    /** Chiave della misura associata al profilo. */
    @Embeddable
    public record Chiave(UUID accountId, long misuraId) implements Serializable {}
}
