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

    protected RichiestaJpa() {
    }

    public UUID getId() {
        return id;
    }

    public long getMisuraId() {
        return misuraId;
    }

    public String getComuneCodice() {
        return comuneCodice;
    }

    public TipoDestinatario getTipoDestinatario() {
        return TipoDestinatario.valueOf(tipoDestinatario);
    }

    public StatoRichiesta getStato() {
        return StatoRichiesta.valueOf(stato);
    }
}
