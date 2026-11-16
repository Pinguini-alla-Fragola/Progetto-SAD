package it.unina.picus.richieste.interno.persistenza;

import java.time.Instant;
import java.util.UUID;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.richieste.api.RichiesteApi.EsitoContatto;
import it.unina.picus.richieste.interno.dominio.StatoContattoIncompatibileException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Contatto indirizzato a un account specifico, con esito distinto dallo stato della pratica. */
@Entity
@Table(name = "richieste_contatti_disponibilita")
public class ContattoDisponibilitaJpa {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "richiesta_id", nullable = false, updatable = false)
    private RichiestaJpa richiesta;

    @Column(nullable = false, updatable = false)
    private UUID erogatoreId;

    @Column(nullable = false, updatable = false, length = 30)
    private String ambito;

    @Column(nullable = false, updatable = false)
    private String nomeMisura;

    @Column(nullable = false, updatable = false)
    private Instant inviatoIl;

    @Column(nullable = false, length = 20)
    private String esito;

    protected ContattoDisponibilitaJpa() {
    }

    public UUID getId() {
        return id;
    }

    public RichiestaJpa getRichiesta() {
        return richiesta;
    }

    public UUID getErogatoreId() {
        return erogatoreId;
    }

    public AmbitoErogazione getAmbito() {
        return AmbitoErogazione.valueOf(ambito);
    }

    public String getNomeMisura() {
        return nomeMisura;
    }

    public EsitoContatto getEsito() {
        return EsitoContatto.valueOf(esito);
    }

    /** Registra una sola risposta al contatto, mentre la pratica è bloccata dal chiamante. */
    public void registraRisposta(boolean disponibile) {
        if (getEsito() != EsitoContatto.IN_ATTESA) {
            throw new StatoContattoIncompatibileException("Il contatto non è in attesa di risposta");
        }
        esito = (disponibile ? EsitoContatto.DISPONIBILE : EsitoContatto.NON_DISPONIBILE).name();
    }
}
