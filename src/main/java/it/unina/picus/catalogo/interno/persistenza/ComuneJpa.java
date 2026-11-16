package it.unina.picus.catalogo.interno.persistenza;

import it.unina.picus.catalogo.api.CatalogoApi.Comune;
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

    Comune aDto() {
        return new Comune(codice, nome);
    }
}
