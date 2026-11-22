package it.unina.picus.utenze.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import it.unina.picus.utenze.interno.applicazione.DatiOperatoreNonValidiException;

/** Trasforma gli errori del modulo Utenze in risposte con messaggi controllati, senza mai i valori inseriti. */
@RestControllerAdvice(assignableTypes = OperatoriController.class)
public class ErroriUtenze {

    /** Stessa forma degli errori degli altri moduli: elenca i campi da correggere. */
    public record Errore(String codice, String messaggio, Map<String, String> campi) {}

    @ExceptionHandler(DatiOperatoreNonValidiException.class)
    public ResponseEntity<Errore> datiNonValidi(DatiOperatoreNonValidiException ex) {
        return risposta("Controlla i campi del modulo", ex.campi());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Errore> formatoNonValido() {
        return risposta("Formato dei dati non valido", Map.of());
    }

    private static ResponseEntity<Errore> risposta(String messaggio, Map<String, String> campi) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new Errore(HttpStatus.BAD_REQUEST.name(), messaggio, campi));
    }
}
