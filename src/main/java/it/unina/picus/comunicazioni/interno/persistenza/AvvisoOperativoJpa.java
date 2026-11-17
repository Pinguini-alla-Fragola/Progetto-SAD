package it.unina.picus.comunicazioni.interno.persistenza;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.AvvisoOperativo;
import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoAvviso;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Avviso persistente destinato a un singolo account. */
@Entity
@Table(name = "comunicazioni_avvisi_operativi")
public class AvvisoOperativoJpa {
    @Id
    private UUID id;
    private UUID richiestaId;
    private UUID contattoId;
    private UUID destinatarioId;
    private String tipo;
    private Instant registratoIl;

    protected AvvisoOperativoJpa() {}

    AvvisoOperativo aDto() {
        return new AvvisoOperativo(id, TipoAvviso.valueOf(tipo), richiestaId, contattoId, registratoIl);
    }
}
