package it.unina.picus.monitoraggio.interno.persistenza;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "monitoraggio_configurazione")
public class ConfigurazioneMonitoraggioJpa {

    @Id
    @Column(name = "id")
    private Short id;

    @Column(name = "correzioni_abilitate", nullable = false)
    private boolean correzioniAbilitate;

    @Column(name = "scadenza_correzioni")
    private Instant scadenzaCorrezioni;

    protected ConfigurazioneMonitoraggioJpa() {}

    public boolean isCorrezioniAbilitate() { return correzioniAbilitate; }
    public Instant getScadenzaCorrezioni() { return scadenzaCorrezioni; }
}