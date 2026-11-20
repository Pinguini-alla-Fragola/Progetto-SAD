package it.unina.picus.comunicazioni.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.unina.picus.comunicazioni.interno.applicazione.ComandoInvioComunicazione;
import it.unina.picus.comunicazioni.interno.applicazione.ComunicazioneInviata;
import it.unina.picus.comunicazioni.interno.applicazione.InvioComunicazioni;
import it.unina.picus.comunicazioni.interno.applicazione.NotificheComunicazioni;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/**
 * Riceve l'invio delle comunicazioni del Banco e la richiesta del contatore da confermare. I nomi dei campi
 * JSON sono quelli del modulo già presente nella pagina del Banco.
 */
@RestController
@RequestMapping(value = "/api/comunicazioni", produces = MediaType.APPLICATION_JSON_VALUE)
public class ComunicazioniController {

    /**
     * Campi del modulo di invio così come li manda il browser.
     *
     * @param titolo oggetto facoltativo
     * @param contenuto testo della comunicazione
     * @param destinatariTipo tipo di destinatari scelto
     * @param gruppo identificativi degli account scelti, solo per l'invio a un gruppo
     */
    public record NuovaComunicazioneJson(String titolo, String contenuto, String destinatariTipo, List<String> gruppo) {

        ComandoInvioComunicazione comando() {
            return new ComandoInvioComunicazione(titolo, contenuto, destinatariTipo, gruppo);
        }
    }

    /**
     * Ciò che il browser riceve dopo l'invio.
     *
     * @param id identificativo della comunicazione
     * @param titolo oggetto, se indicato
     * @param inviataIl istante dell'invio
     * @param destinatariTipo tipo di destinatari scelto
     * @param destinatari numero di account che l'hanno ricevuta
     */
    public record ComunicazioneInviataJson(UUID id, String titolo, Instant inviataIl, String destinatariTipo,
            int destinatari) {

        static ComunicazioneInviataJson da(ComunicazioneInviata inviata) {
            return new ComunicazioneInviataJson(inviata.id(), inviata.titolo(), inviata.inviataIl(),
                    inviata.tipo().name(), inviata.destinatari());
        }
    }

    /**
     * Numero di comunicazioni ricevute che aspettano la conferma.
     *
     * @param daConfermare quante sono
     */
    public record DaConfermareJson(long daConfermare) {}

    private final InvioComunicazioni invio;
    private final NotificheComunicazioni notifiche;

    public ComunicazioniController(InvioComunicazioni invio, NotificheComunicazioni notifiche) {
        this.invio = invio;
        this.notifiche = notifiche;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ComunicazioneInviataJson> invia(IdentitaAutenticata attore,
            @RequestBody NuovaComunicazioneJson corpo) {
        ComunicazioneInviata inviata = invio.invia(attore, corpo.comando());
        return ResponseEntity.created(URI.create("/api/comunicazioni/" + inviata.id()))
                .body(ComunicazioneInviataJson.da(inviata));
    }

    @GetMapping("/da-confermare")
    public DaConfermareJson daConfermare(IdentitaAutenticata attore) {
        return new DaConfermareJson(notifiche.daConfermare(attore));
    }
}
