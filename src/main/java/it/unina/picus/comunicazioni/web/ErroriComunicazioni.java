package it.unina.picus.comunicazioni.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import it.unina.picus.comunicazioni.interno.applicazione.DatiComunicazioneNonValidiException;
import it.unina.picus.comunicazioni.interno.applicazione.RisorsaComunicazioniNonTrovataException;
import it.unina.picus.comunicazioni.interno.dominio.CampoComunicazione;

/**
 * Trasforma gli errori delle comunicazioni in risposte con messaggi controllati, senza mai i valori inseriti.
 * Il JSON illeggibile è già gestito per questo package dai gestori comuni.
 */
@RestControllerAdvice(assignableTypes = {ComunicazioniController.class, ConsultazioneComunicazioniController.class})
public class ErroriComunicazioni {

    /**
     * Stessa forma degli errori degli altri moduli: elenca i campi da correggere.
     *
     * @param codice stato HTTP
     * @param messaggio messaggio generale
     * @param campi campi da correggere con il loro messaggio
     */
    public record Errore(String codice, String messaggio, Map<String, String> campi) {}

    @ExceptionHandler(DatiComunicazioneNonValidiException.class)
    public ResponseEntity<Errore> datiNonValidi(DatiComunicazioneNonValidiException ex) {
        return risposta(HttpStatus.BAD_REQUEST, "Controlla i campi della comunicazione", ex.campi());
    }

    /** Un file troppo grande viene fermato mentre arriva, prima delle regole: si risponde come per gli altri errori. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Errore> fileTroppoGrandi() {
        return risposta(HttpStatus.BAD_REQUEST, "Controlla i campi della comunicazione",
                Map.of(CampoComunicazione.ALLEGATI, "Ogni allegato può pesare al massimo 5 MB"));
    }

    @ExceptionHandler(RisorsaComunicazioniNonTrovataException.class)
    public ResponseEntity<Errore> nonTrovata() {
        return risposta(HttpStatus.NOT_FOUND, "Comunicazione o allegato non trovati", Map.of());
    }

    private static ResponseEntity<Errore> risposta(HttpStatus stato, String messaggio, Map<String, String> campi) {
        return ResponseEntity.status(stato).body(new Errore(stato.name(), messaggio, campi));
    }
}
