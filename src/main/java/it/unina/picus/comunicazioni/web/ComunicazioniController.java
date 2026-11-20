package it.unina.picus.comunicazioni.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
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
import it.unina.picus.comunicazioni.interno.dominio.CampoComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.RegolaDestinatari;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/**
 * Riceve l'invio delle comunicazioni del Banco e la richiesta del contatore da confermare. I nomi dei campi
 * JSON sono quelli del modulo già presente nella pagina del Banco.
 */
@RestController
@RequestMapping(value = "/api/comunicazioni", produces = MediaType.APPLICATION_JSON_VALUE)
public class ComunicazioniController {

    /**
     * Campi del modulo di invio così come li manda il browser. Restano valori JSON generici fino al controllo
     * del tipo: convertiti subito, un booleano diventerebbe il testo "true" e verrebbe inviato.
     *
     * @param titolo oggetto facoltativo
     * @param contenuto testo della comunicazione
     * @param destinatariTipo tipo di destinatari scelto
     * @param gruppo identificativi degli account scelti, solo per l'invio a un gruppo
     */
    public record NuovaComunicazioneJson(Object titolo, Object contenuto, Object destinatariTipo,
            List<Object> gruppo) {

        ComandoInvioComunicazione comando() {
            CampiJson campi = new CampiJson();
            ComandoInvioComunicazione comando = new ComandoInvioComunicazione(
                    campi.testo(titolo, CampoComunicazione.TITOLO),
                    campi.testo(contenuto, CampoComunicazione.CONTENUTO),
                    campi.testo(destinatariTipo, CampoComunicazione.DESTINATARI_TIPO),
                    campi.testi(gruppo, CampoComunicazione.GRUPPO, RegolaDestinatari.MASSIMO_GRUPPO));
            campi.verifica();
            return comando;
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

    /**
     * Invia la comunicazione e risponde {@code 201}. Non indica l'indirizzo della comunicazione: la sua
     * consultazione arriverà con la #141.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ComunicazioneInviataJson> invia(IdentitaAutenticata attore,
            @RequestBody NuovaComunicazioneJson corpo) {
        ComunicazioneInviata inviata = invio.invia(attore, corpo.comando());
        return ResponseEntity.status(HttpStatus.CREATED).body(ComunicazioneInviataJson.da(inviata));
    }

    @GetMapping("/da-confermare")
    public DaConfermareJson daConfermare(IdentitaAutenticata attore) {
        return new DaConfermareJson(notifiche.daConfermare(attore));
    }
}
