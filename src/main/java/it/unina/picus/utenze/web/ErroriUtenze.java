package it.unina.picus.utenze.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import it.unina.picus.utenze.api.OperazioneUtenzaNonConsentitaException;
import it.unina.picus.utenze.api.UtenzaNonTrovataException;
import it.unina.picus.utenze.interno.applicazione.DatiOperatoreNonValidiException;

/** Trasforma gli errori del modulo Utenze in risposte con messaggi controllati, senza mai i valori inseriti. */
@RestControllerAdvice(assignableTypes = { OperatoriController.class, InvitiOperatoriController.class })
public class ErroriUtenze {

    /** Stessa forma degli errori degli altri moduli: elenca i campi da correggere. */
    public record Errore(String codice, String messaggio, Map<String, String> campi) {}

    @ExceptionHandler(DatiOperatoreNonValidiException.class)
    public ResponseEntity<Errore> datiNonValidi(DatiOperatoreNonValidiException ex) {
        return risposta(HttpStatus.BAD_REQUEST, "Controlla i campi del modulo", ex.campi());
    }

    @ExceptionHandler(UtenzaNonTrovataException.class)
    public ResponseEntity<Errore> nonTrovata(UtenzaNonTrovataException ex) {
        return risposta(HttpStatus.NOT_FOUND, ex.getMessage(), Map.of());
    }

    /** Ruolo o stato dell'account non compatibili con l'operazione, per esempio un reinvio a un account già attivo. */
    @ExceptionHandler(OperazioneUtenzaNonConsentitaException.class)
    public ResponseEntity<Errore> nonConsentita(OperazioneUtenzaNonConsentitaException ex) {
        return risposta(HttpStatus.CONFLICT, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Errore> formatoNonValido() {
        return risposta(HttpStatus.BAD_REQUEST, "Formato dei dati non valido", Map.of());
    }

    private static ResponseEntity<Errore> risposta(HttpStatus stato, String messaggio, Map<String, String> campi) {
        return ResponseEntity.status(stato).body(new Errore(stato.name(), messaggio, campi));
    }
}
