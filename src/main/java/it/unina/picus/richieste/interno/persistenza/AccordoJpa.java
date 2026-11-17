package it.unina.picus.richieste.interno.persistenza;

import it.unina.picus.richieste.api.RichiesteApi.AccordoADistanza;
import it.unina.picus.richieste.api.RichiesteApi.AccordoInPresenza;
import it.unina.picus.richieste.api.RichiesteApi.DettagliAccordo;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.net.URI;
import java.time.Instant;

/**
 * Componente persistito sulla riga della richiesta. La conversione delle due varianti
 * applica il pattern <em>Data Mapper</em> tra i dettagli di dominio e le colonne SQL.
 */
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

    /** Costruttore riservato a JPA. */
    protected AccordoJpa() {}

    /**
     * Prepara i dettagli da salvare insieme alla transizione di stato.
     *
     * @param dettagli accordo già validato, oppure {@code null} se assente
     * @return componente da incorporare nella richiesta, oppure {@code null}
     */
    static AccordoJpa daDto(DettagliAccordo dettagli) {
        if (dettagli == null) {
            return null;
        }
        var entita = new AccordoJpa();
        switch (dettagli) {
            case AccordoInPresenza presenza -> {
                entita.modalita = "IN_PRESENZA";
                entita.dataOra = presenza.dataOra();
                entita.luogo = presenza.luogo();
                entita.nomeReferente = presenza.nomeReferente();
                entita.cognomeReferente = presenza.cognomeReferente();
                entita.telefonoReferente = presenza.telefonoReferente();
            }
            case AccordoADistanza remoto -> {
                entita.modalita = "A_DISTANZA";
                entita.dataOra = remoto.dataOra();
                entita.link = remoto.link().toString();
            }
        }
        return entita;
    }

    /** {@return dettagli di dominio ricostruiti dalle colonne, oppure {@code null} se assenti} */
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
