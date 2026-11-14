package it.unina.picus.catalogo.interno.persistenza;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "catalogo_comuni")
public class ComuneJpa {
    @Id
    private String codice;
    private String nome;

    protected ComuneJpa() {
    }
}
