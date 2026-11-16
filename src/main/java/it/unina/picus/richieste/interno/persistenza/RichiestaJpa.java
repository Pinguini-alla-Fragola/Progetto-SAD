package it.unina.picus.richieste.interno.persistenza;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;
import it.unina.picus.richieste.interno.dominio.RichiestaDiMisura;

@Entity
@Table(name = "richieste")
public class RichiestaJpa {
    @Id
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
    private StatoRichiesta stato;
    private UUID riferimentoInvio;

    @Embedded
    private AccordoJpa accordo;

    @Column(name = "erogatore_id")
    private UUID erogatoreId;

    protected RichiestaJpa() {
    }

    RichiestaDiMisura aDominio() {
        return new RichiestaDiMisura(id, titolareId, misuraId, tipoDestinatario,
                comuneCodice, description, inviataIl, stato, erogatoreId,
                accordo == null ? null : accordo.aDto(), riferimentoInvio);
    }
}
