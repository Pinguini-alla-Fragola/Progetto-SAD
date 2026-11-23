package it.unina.picus.comunicazioni.interno.persistenza;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Allegato di una comunicazione, salvato nel database con il suo contenuto. */
@Entity
@Table(name = "comunicazioni_allegati")
public class AllegatoComunicazioneJpa {
    @Id
    private UUID id;
    private UUID comunicazioneId;
    private String nomeFile;
    private String tipoMime;
    private long dimensioneByte;
    private byte[] contenuto;

    protected AllegatoComunicazioneJpa() {
    }

    public AllegatoComunicazioneJpa(UUID id, UUID comunicazioneId, String nomeFile, String tipoMime, byte[] contenuto) {
        this.id = id;
        this.comunicazioneId = comunicazioneId;
        this.nomeFile = nomeFile;
        this.tipoMime = tipoMime;
        this.dimensioneByte = contenuto.length;
        this.contenuto = contenuto.clone();
    }

    public UUID getComunicazioneId() { return comunicazioneId; }
    public String getNomeFile() { return nomeFile; }
    public String getTipoMime() { return tipoMime; }
    public byte[] getContenuto() { return contenuto.clone(); }
}
