package it.unina.picus.monitoraggio.interno.persistenza;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "monitoraggio_conteggi_opt",
        uniqueConstraints = @UniqueConstraint(columnNames = {"opt_id", "anno", "trimestre", "misura_id", "voce"})
)
public class ConteggioOptJpa {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "opt_id", nullable = false)
    private UUID optId;

    @Column(name = "anno", nullable = false)
    private int anno;

    @Column(name = "trimestre", nullable = false)
    private int trimestre;

    @Column(name = "misura_id", nullable = false)
    private long misuraId;

    @Column(name = "voce", length = 64, nullable = false)
    private String voce;

    @Column(name = "valore", nullable = false)
    private long valore;

    @Column(name = "aggiornato_il", nullable = false)
    private Instant aggiornatoIl;

    protected ConteggioOptJpa() {}

    public ConteggioOptJpa(UUID id, UUID optId, int anno, int trimestre, long misuraId, String voce, long valore, Instant aggiornatoIl) {
        this.id = id;
        this.optId = optId;
        this.anno = anno;
        this.trimestre = trimestre;
        this.misuraId = misuraId;
        this.voce = voce;
        this.valore = valore;
        this.aggiornatoIl = aggiornatoIl;
    }

    public UUID getId() { return id; }
    public UUID getOptId() { return optId; }
    public int getAnno() { return anno; }
    public int getTrimestre() { return trimestre; }
    public long getMisuraId() { return misuraId; }
    public String getVoce() { return voce; }
    public long getValore() { return valore; }
    public Instant getAggiornatoIl() { return aggiornatoIl; }

    public void aggiornaValore(long nuovoValore, Instant istante) {
        this.valore = nuovoValore;
        this.aggiornatoIl = istante;
    }
}