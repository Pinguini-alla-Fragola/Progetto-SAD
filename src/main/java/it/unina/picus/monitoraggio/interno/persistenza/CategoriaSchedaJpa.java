package it.unina.picus.monitoraggio.interno.persistenza;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Categoria di conteggio configurata dal Banco per le schede personali TM-OP-01. */
@Entity
@Table(name = "monitoraggio_categorie_schede")
public class CategoriaSchedaJpa {

    @Id
    @Column(name = "codice", length = 64, nullable = false)
    private String codice;

    @Column(name = "descrizione", nullable = false)
    private String descrizione;

    @Column(name = "attiva", nullable = false)
    private boolean attiva;

    /** Costruttore riservato a JPA. */
    protected CategoriaSchedaJpa() {}

    /** @return codice della categoria di conteggio */
    public String getCodice() {
        return codice;
    }

    /** @return descrizione leggibile della categoria */
    public String getDescrizione() {
        return descrizione;
    }

    /** @return {@code true} se la categoria è attualmente attiva per la rendicontazione */
    public boolean isAttiva() {
        return attiva;
    }
}
