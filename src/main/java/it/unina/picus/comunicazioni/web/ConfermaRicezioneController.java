package it.unina.picus.comunicazioni.web;

import it.unina.picus.comunicazioni.interno.applicazione.ServizioConfermaRicezione;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** Espone il comando di conferma di ricezione per il destinatario in sessione. */
@RestController
@Profile("!skeleton")
public class ConfermaRicezioneController {

    private final ServizioConfermaRicezione servizio;

    /**
     * Collega la rotta di conferma al relativo caso d'uso.
     *
     * @param servizio servizio applicativo di conferma
     */
    public ConfermaRicezioneController(ServizioConfermaRicezione servizio) {
        this.servizio = servizio;
    }

    /**
     * Registra il riscontro tramite il comando esplicito del destinatario.
     *
     * @param attore identità ricavata dal resolver della sessione
     * @param comunicazioneId identificativo della comunicazione indicato nella rotta
     * @return istante della prima conferma per l'account in sessione
     */
    @PostMapping("/api/comunicazioni/{comunicazioneId}/ricezione")
    public Instant confermaRicezione(IdentitaAutenticata attore, @PathVariable UUID comunicazioneId) {
        return servizio.confermaRicezione(attore, comunicazioneId);
    }
}
