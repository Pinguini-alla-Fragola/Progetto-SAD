package it.unina.picus.catalogo.interno.persistenza;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "catalogo_misure")
public class MisuraJpa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String codice;
    private String nome;
    private String descrizione;
    private boolean attiva;

    protected MisuraJpa() {
    }
}
