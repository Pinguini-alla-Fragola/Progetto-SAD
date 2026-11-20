package it.unina.picus.catalogo.interno.persistenza;

import it.unina.picus.catalogo.api.CatalogoApi.DatiMisura;
import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.interno.dominio.MisuraDiCatalogo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entità JPA associata alla tabella {@code catalogo_misure}.
 *
 * <p>Espone esclusivamente fabbriche e metodi di aggiornamento controllati a livello di package,
 * preservando l'identificativo stabile assegnato alla prima persistenza.</p>
 */
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

    /** Costruttore richiesto da JPA. */
    protected MisuraJpa() {
    }

    private MisuraJpa(MisuraDiCatalogo misura) {
        aggiornaCampi(misura);
    }

    /**
     * Crea una nuova entità JPA pronta per la persistenza a partire dal modello di dominio.
     *
     * @param misura misura di dominio appena creata
     * @return nuova entità JPA con identificativo ancora gestito dal database
     */
    static MisuraJpa nuova(MisuraDiCatalogo misura) {
        return new MisuraJpa(misura);
    }

    /**
     * Applica i nuovi attributi dal dominio senza alterare l'identificativo persistito.
     *
     * @param misura stato di dominio aggiornato della misura
     */
    void aggiorna(MisuraDiCatalogo misura) {
        aggiornaCampi(misura);
    }

    private void aggiornaCampi(MisuraDiCatalogo misura) {
        this.codice = misura.codice();
        this.nome = misura.nome();
        this.descrizione = misura.descrizione();
        this.attiva = misura.attiva();
        this.erogabileADistanza = misura.erogabileADistanza();
    }

    /**
     * Proietta l'entità persistita nel DTO pubblico del modulo Catalogo.
     *
     * @return DTO immutabile della misura
     */
    Misura aDto() {
        return new Misura(id, new DatiMisura(codice, nome, descrizione, erogabileADistanza), attiva);
    }

    /**
     * Converte l'entità persistita nell'oggetto di dominio corrispondente.
     *
     * @return entità di dominio ricostruita con il proprio identificativo stabile
     */
    MisuraDiCatalogo aDominio() {
        return MisuraDiCatalogo.ricostruita(id, codice, nome, descrizione, erogabileADistanza, attiva);
    }
}
