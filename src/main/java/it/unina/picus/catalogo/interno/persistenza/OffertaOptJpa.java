package it.unina.picus.catalogo.interno.persistenza;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

/** Dichiarazione corrente di offerta dell'account organizzativo OPT. */
@Entity
@Table(name = "catalogo_offerte_opt")
public class OffertaOptJpa {
    @EmbeddedId
    private Chiave id;
    private boolean tramiteProfessionista;
    private boolean attiva;

    protected OffertaOptJpa() {}

    boolean attiva() { return attiva; }

    /** Chiave della dichiarazione per OPT e misura. */
    @Embeddable
    public record Chiave(UUID optId, long misuraId) implements Serializable {}
}
