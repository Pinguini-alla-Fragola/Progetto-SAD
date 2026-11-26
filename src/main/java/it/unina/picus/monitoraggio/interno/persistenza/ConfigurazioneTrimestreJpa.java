package it.unina.picus.monitoraggio.interno.persistenza;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoMonitoraggio;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Associazione immutabile di un trimestre a una versione pubblicata o legacy (#41, #181). */
@Entity
@Table(name = "monitoraggio_configurazioni_trimestre")
public class ConfigurazioneTrimestreJpa {

    @EmbeddedId
    private Id id;

    @Column(name = "versione_id", nullable = false)
    private UUID versioneId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "versione_id", insertable = false, updatable = false)
    private VersioneCategorieJpa versione;

    @Column(name = "assegnata_il", nullable = false)
    private Instant assegnataIl;

    protected ConfigurazioneTrimestreJpa() {}

    ConfigurazioneTrimestreJpa(TracciatoMonitoraggio tracciato, Trimestre trimestre, VersioneCategorieJpa versione, Instant assegnataIl) {
        this.id = new Id(tracciato.codice(), trimestre.anno(), trimestre.numero());
        this.versioneId = versione.id();
        this.versione = versione;
        this.assegnataIl = assegnataIl;
    }

    Id id() {
        return id;
    }

    VersioneCategorieJpa versione() {
        return versione;
    }

    Instant assegnataIl() {
        return assegnataIl;
    }

    /** Chiave primaria composta per tracciato e periodo. */
    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "tracciato", nullable = false, length = 32)
        private String tracciato;

        @Column(name = "anno", nullable = false)
        private int anno;

        @Column(name = "trimestre", nullable = false)
        private int trimestre;

        protected Id() {}

        public Id(String tracciato, int anno, int trimestre) {
            this.tracciato = tracciato;
            this.anno = anno;
            this.trimestre = trimestre;
        }

        public String tracciato() {
            return tracciato;
        }

        public Trimestre aTrimestre() {
            return new Trimestre(anno, trimestre);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id other)) return false;
            return anno == other.anno && trimestre == other.trimestre && Objects.equals(tracciato, other.tracciato);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tracciato, anno, trimestre);
        }
    }
}
