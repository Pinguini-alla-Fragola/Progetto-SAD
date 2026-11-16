package it.unina.picus.richieste.interno.persistenza;

import java.time.Instant;
import java.util.UUID;

import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import it.unina.picus.richieste.interno.dominio.RichiestaDiMisura;
import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;

/**
 * Entità JPA che conserva i dati e lo stato della richiesta di misura.
 *
 * <p>La relativa riga è usata per coordinare gli aggiornamenti concorrenti dei contatti.
 *
 * @see RichiestaRepository#cercaPerAggiornamento(UUID)
 */
@Entity
@Table(name = "richieste")
public class RichiestaJpa {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID titolareId;
    private Long misuraId;
    private String comuneCodice;
    @Enumerated(EnumType.STRING)
    private TipoDestinatario tipoDestinatario;
    @Column(columnDefinition = "text")
    private String description;
    private Instant inviataIl;
    @Enumerated(EnumType.STRING)
    private StatoRichiesta stato = StatoRichiesta.CREATA;
    private UUID riferimentoInvio;

    @Embedded
    private AccordoJpa accordo;

    @Column(name = "erogatore_id")
    private UUID erogatoreId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AmbitoErogazione erogatoreAmbito;

    /** Costruttore riservato a JPA. */
    protected RichiestaJpa() {
    }

    RichiestaDiMisura aDominio() {
        return new RichiestaDiMisura(id, titolareId, misuraId, tipoDestinatario,
                comuneCodice, description, inviataIl, stato, erogatoreId,
                accordo == null ? null : accordo.aDto(), riferimentoInvio, erogatoreAmbito);
    }

    /** {@return identificativo della richiesta} */
    public UUID getId() {
        return id;
    }

    /** {@return identificativo della misura richiesta} */
    public long getMisuraId() {
        return misuraId;
    }

    /** {@return codice del comune della richiesta} */
    public String getComuneCodice() {
        return comuneCodice;
    }

    /** {@return tipo di destinatario della misura} */
    public TipoDestinatario getTipoDestinatario() {
        return tipoDestinatario;
    }

    /** {@return stato corrente della richiesta} */
    public StatoRichiesta getStato() {
        return stato;
    }

    void aggiorna(RichiestaDiMisura richiesta) {
        stato = richiesta.stato();
        erogatoreId = richiesta.erogatoreId();
        erogatoreAmbito = richiesta.erogatoreAmbito();
    }
}
