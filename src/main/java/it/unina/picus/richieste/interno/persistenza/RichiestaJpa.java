package it.unina.picus.richieste.interno.persistenza;

import java.time.Instant;
import java.util.UUID;

import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
    private String tipoDestinatario;
    private String description;
    private Instant inviataIl;
    private String stato = "CREATA";

    @Column(name = "erogatore_id")
    private UUID erogatoreId;

    /** Costruttore riservato a JPA. */
    protected RichiestaJpa() {
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
        return TipoDestinatario.valueOf(tipoDestinatario);
    }

    /** {@return stato corrente della richiesta} */
    public StatoRichiesta getStato() {
        return StatoRichiesta.valueOf(stato);
    }
}
