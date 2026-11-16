package it.unina.picus.richieste.interno.persistenza;

import it.unina.picus.richieste.api.RichiesteApi.AccordoADistanza;
import it.unina.picus.richieste.api.RichiesteApi.AccordoInPresenza;
import it.unina.picus.richieste.api.RichiesteApi.DettagliAccordo;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.net.URI;
import java.time.Instant;

/** Proiezione dei dettagli che verranno registrati dal coordinamento dell'accordo. */
@Embeddable
public class AccordoJpa {
    @Column(name = "accordo_modalita")
    private String modalita;
    @Column(name = "accordo_data_ora")
    private Instant dataOra;
    @Column(name = "accordo_luogo", length = 500)
    private String luogo;
    @Column(name = "accordo_link", length = 2048)
    private String link;
    @Column(name = "accordo_nome_referente")
    private String nomeReferente;
    @Column(name = "accordo_cognome_referente")
    private String cognomeReferente;
    @Column(name = "accordo_telefono_referente", length = 50)
    private String telefonoReferente;

    protected AccordoJpa() {}

    DettagliAccordo aDto() {
        if (modalita == null) {
            return null;
        }
        return switch (modalita) {
            case "IN_PRESENZA" -> new AccordoInPresenza(
                    dataOra, luogo, nomeReferente, cognomeReferente, telefonoReferente);
            case "A_DISTANZA" -> new AccordoADistanza(dataOra, URI.create(link));
            default -> throw new IllegalStateException("Modalità dell'accordo non riconosciuta");
        };
    }
}
