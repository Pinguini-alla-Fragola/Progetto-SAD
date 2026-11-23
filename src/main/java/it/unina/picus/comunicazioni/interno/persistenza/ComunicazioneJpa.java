package it.unina.picus.comunicazioni.interno.persistenza;

import java.time.Instant;
import java.util.UUID;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Comunicazione inviata dal Banco. */
@Entity
@Table(name = "comunicazioni")
public class ComunicazioneJpa {
    @Id
    private UUID id;
    private UUID mittenteId;
    private String titolo;
    @Column(columnDefinition = "text")
    private String testo;
    @Enumerated(EnumType.STRING)
    private TipoDestinatari tipoDestinatari;
    private Instant inviataIl;

    protected ComunicazioneJpa() {
    }

    public ComunicazioneJpa(UUID id, UUID mittenteId, String titolo, String testo, TipoDestinatari tipoDestinatari,
            Instant inviataIl) {
        this.id = id;
        this.mittenteId = mittenteId;
        this.titolo = titolo;
        this.testo = testo;
        this.tipoDestinatari = tipoDestinatari;
        this.inviataIl = inviataIl;
    }

    public UUID getId() { return id; }
    public String getTitolo() { return titolo; }
    public String getTesto() { return testo; }
    public TipoDestinatari getTipoDestinatari() { return tipoDestinatari; }
    public Instant getInviataIl() { return inviataIl; }
}
