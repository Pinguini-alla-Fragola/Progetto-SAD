package it.unina.picus.catalogo.interno.persistenza;

import it.unina.picus.catalogo.api.CatalogoApi.DatiMisura;
import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import jakarta.persistence.Column;
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
    @Column(name = "erogabile_a_distanza")
    private boolean erogabileADistanza;

    protected MisuraJpa() {
    }

    Misura aDto() {
        return new Misura(id, new DatiMisura(codice, nome, descrizione, erogabileADistanza), attiva);
    }
}
