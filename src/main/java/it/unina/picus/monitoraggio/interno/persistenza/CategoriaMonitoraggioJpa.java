package it.unina.picus.monitoraggio.interno.persistenza;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "monitoraggio_categorie")
public class CategoriaMonitoraggioJpa {

    @Id
    @Column(name = "codice", length = 64, nullable = false)
    private String codice;

    @Column(name = "descrizione", nullable = false)
    private String descrizione;

    @Column(name = "attiva", nullable = false)
    private boolean attiva;

    protected CategoriaMonitoraggioJpa() {}

    public String getCodice() { return codice; }
    public String getDescrizione() { return descrizione; }
    public boolean isAttiva() { return attiva; }
}