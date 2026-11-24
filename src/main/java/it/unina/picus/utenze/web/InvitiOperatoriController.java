package it.unina.picus.utenze.web;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService;
import it.unina.picus.utenze.interno.applicazione.AttivazioneUtenzaService.DatiInvioAttivazione;
import it.unina.picus.utenze.interno.applicazione.ConsegnaLinkAttivazione;
import it.unina.picus.utenze.interno.applicazione.ConsegnaLinkAttivazione.Esito;
import it.unina.picus.utenze.interno.applicazione.ConclusioneReinvio;
import it.unina.picus.utenze.interno.applicazione.RiprovaTransitoria;

/** Reinvio del link di attivazione di un Operatore in attesa, a cura del Banco. */
@RestController
@Profile("!skeleton")
@RequestMapping("/api/operatori")
class InvitiOperatoriController {

    /** Esito del reinvio, con gli stessi campi della risposta equivalente per le OPT. */
    record EsitoReinvio(UUID accountId, boolean invitoInviato, boolean erroreInvio, String messaggio) {}

    private final AttivazioneUtenzaService attivazione;
    private final ConsegnaLinkAttivazione consegna;
    private final ConclusioneReinvio conclusione;

    InvitiOperatoriController(AttivazioneUtenzaService attivazione, ConsegnaLinkAttivazione consegna,
            ConclusioneReinvio conclusione) {
        this.attivazione = attivazione;
        this.consegna = consegna;
        this.conclusione = conclusione;
    }

    /**
     * Crea un nuovo link, lo spedisce e solo se la posta lo accetta sostituisce il precedente: se l'invio fallisce
     * l'Operatore conserva il link che aveva già ricevuto. Risponde {@code 404} se l'account non esiste,
     * {@code 409} se non è un Operatore o non è più in attesa di attivazione (gli errori li traduce
     * {@link ErroriUtenze}).
     */
    @PostMapping("/{id}/reinvia-invito")
    ResponseEntity<EsitoReinvio> reinvia(@PathVariable UUID id) {
        DatiInvioAttivazione invito = attivazione.preparaReinvio(id, RuoloUtente.OPERATORE);
        Esito esito = consegna.consegna(invito);
        // la email è già partita: se la registrazione dell'esito ha un errore transitorio si ripete solo quella
        RiprovaTransitoria.esegui(() -> conclusione.concludi(id, invito, esito != Esito.NON_RIUSCITO));
        if (esito == Esito.NON_RIUSCITO) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new EsitoReinvio(id, false, true, MessaggiInvito.REINVIO_NON_RIUSCITO));
        }
        return ResponseEntity.ok(new EsitoReinvio(id, esito == Esito.INVIATO, false, MessaggiInvito.alReinvio(esito)));
    }
}
