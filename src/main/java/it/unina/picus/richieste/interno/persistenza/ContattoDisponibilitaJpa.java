package it.unina.picus.richieste.interno.persistenza;

import java.time.Instant;
import java.util.UUID;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.richieste.api.ConflittoRichiestaException;
import it.unina.picus.richieste.api.RichiesteApi.EsitoContatto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entità JPA che conserva il contatto, il destinatario e l'esito della risposta.
 *
 * <p>Il contatto appartiene a una richiesta di misura. Il suo esito registra la risposta
 * dell'erogatore indipendentemente dallo stato della richiesta.
 */
@Entity
@Table(name = "richieste_contatti_disponibilita")
public class ContattoDisponibilitaJpa {
    @Id
    private UUID id;

    @Column(insertable = false, updatable = false)
    private Long ordineInvio;

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

    /** Costruttore riservato a JPA. */
    protected ContattoDisponibilitaJpa() {
    }

    /** Costruisce il contatto mentre il chiamante mantiene il lock della richiesta. */
    public static ContattoDisponibilitaJpa crea(RichiestaJpa richiesta, UUID erogatoreId,
            AmbitoErogazione ambito, String nomeMisura, Instant istante) {
        richiesta.aDominio().verificaPresaInCarico();
        var contatto = new ContattoDisponibilitaJpa();
        contatto.id = UUID.randomUUID();
        contatto.richiesta = richiesta;
        contatto.erogatoreId = erogatoreId;
        contatto.ambito = ambito.name();
        contatto.nomeMisura = nomeMisura;
        contatto.inviatoIl = istante;
        contatto.esito = EsitoContatto.IN_ATTESA.name();
        return contatto;
    }

    /** {@return identificativo del contatto} */
    public UUID getId() {
        return id;
    }

    /** {@return richiesta di misura a cui appartiene il contatto} */
    public RichiestaJpa getRichiesta() {
        return richiesta;
    }

    /** {@return identificativo dell'account destinatario del contatto} */
    public UUID getErogatoreId() {
        return erogatoreId;
    }

    /** {@return ambito di erogazione del contatto} */
    public AmbitoErogazione getAmbito() {
        return AmbitoErogazione.valueOf(ambito);
    }

    /** {@return nome della misura registrato nel contatto} */
    public String getNomeMisura() {
        return nomeMisura;
    }

    /** {@return esito corrente del contatto} */
    public EsitoContatto getEsito() {
        return EsitoContatto.valueOf(esito);
    }

    /**
     * Registra l'esito di un contatto ancora in attesa.
     *
     * <p>Il chiamante deve mantenere il lock sulla richiesta nella stessa transazione.
     *
     * @param disponibile {@code true} per confermare, {@code false} per rifiutare
     * @throws ConflittoRichiestaException se il contatto ha già ricevuto una risposta
     */
    public void registraRisposta(boolean disponibile) {
        if (getEsito() != EsitoContatto.IN_ATTESA) {
            throw new ConflittoRichiestaException("Il contatto non è in attesa di risposta");
        }
        esito = (disponibile ? EsitoContatto.DISPONIBILE : EsitoContatto.NON_DISPONIBILE).name();
    }
}
