package it.unina.picus.monitoraggio.interno.persistenza;

import it.unina.picus.monitoraggio.interno.dominio.VoceCategoriaVersione;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Voce configurata all'interno di una versione delle categorie (#41, #181). */
@Entity
@Table(name = "monitoraggio_voci_versione_categorie")
public class VoceVersioneCategorieJpa {

    @EmbeddedId
    private Id id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("versioneId")
    @JoinColumn(name = "versione_id", nullable = false)
    private VersioneCategorieJpa versione;

    @Column(nullable = false, length = 255)
    private String descrizione;

    @Column(nullable = false, length = 1000)
    private String definizione;

    @Column(nullable = false)
    private int ordine;

    /** Costruttore riservato a JPA. */
    protected VoceVersioneCategorieJpa() {}

    VoceCategoriaVersione aDominio() {
        return new VoceCategoriaVersione(id.codice(), descrizione, definizione, ordine);
    }

    /** Chiave composta della voce per singola versione. */
    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "versione_id", nullable = false)
        private UUID versioneId;

        @Column(name = "codice", nullable = false, length = 64)
        private String codice;

        /** Costruttore riservato a JPA. */
        protected Id() {}

        String codice() {
            return codice;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id other)) return false;
            return Objects.equals(versioneId, other.versioneId) && Objects.equals(codice, other.codice);
        }

        @Override
        public int hashCode() {
            return Objects.hash(versioneId, codice);
        }
    }
}
