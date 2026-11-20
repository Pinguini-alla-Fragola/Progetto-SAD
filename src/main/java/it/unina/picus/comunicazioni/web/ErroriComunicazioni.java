package it.unina.picus.comunicazioni.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import it.unina.picus.comunicazioni.interno.applicazione.DatiComunicazioneNonValidiException;

/**
 * Trasforma i dati non validi dell'invio in una risposta con i campi da correggere, senza mai i valori inseriti.
 * Il JSON illeggibile è già gestito per questo package dai gestori comuni.
 */
@RestControllerAdvice(assignableTypes = ComunicazioniController.class)
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
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new Errore(HttpStatus.BAD_REQUEST.name(), "Controlla i campi della comunicazione", ex.campi()));
    }
}
