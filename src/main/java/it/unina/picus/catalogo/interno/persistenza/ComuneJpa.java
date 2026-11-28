package it.unina.picus.catalogo.interno.persistenza;

import it.unina.picus.catalogo.api.CatalogoApi.Comune;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "catalogo_comuni")
public class ComuneJpa {
    @Id
    private String codice;
    private String nome;
    @Column(name = "codice_istat")
    private String codiceIstat;
    private String provincia;
    @Column(name = "sigla_provincia")
    private String siglaProvincia;
    @Column(name = "citta_metropolitana")
    private boolean cittaMetropolitana;

    protected ComuneJpa() {
    }

    Comune aDto() {
        return new Comune(codice, nome, codiceIstat, provincia, siglaProvincia, cittaMetropolitana);
    }
}
