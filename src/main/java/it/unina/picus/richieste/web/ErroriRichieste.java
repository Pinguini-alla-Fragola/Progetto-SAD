package it.unina.picus.richieste.web;

import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.shared.SessioneNonValidaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.NoSuchElementException;

/** Risposte con messaggi controllati: mai valori del corpo, descrizioni o messaggi del parser. */
@RestControllerAdvice(basePackages = {"it.unina.picus.richieste.web", "it.unina.picus.catalogo.web"})
public class ErroriRichieste {
    public record Errore(String codice, String messaggio, Map<String, String> campi) {}

    @ExceptionHandler(DatiRichiestaNonValidiException.class)
    public ResponseEntity<Errore> validazione(DatiRichiestaNonValidiException ex) {
        return risposta(HttpStatus.BAD_REQUEST, "Controlla i campi della richiesta", ex.campi());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Errore> formatoNonValido() {
        return risposta(HttpStatus.BAD_REQUEST, "Formato dei dati non valido", Map.of());
    }

    @ExceptionHandler(SessioneNonValidaException.class)
    public ResponseEntity<Errore> sessioneNonValida() {
        return risposta(HttpStatus.UNAUTHORIZED, "Sessione assente o non più valida", Map.of());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Errore> accessoNegato() {
        return risposta(HttpStatus.FORBIDDEN, "Accesso non consentito", Map.of());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Errore> nonTrovata() {
        return risposta(HttpStatus.NOT_FOUND, "Richiesta non trovata", Map.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Errore> conflitto() {
        return risposta(HttpStatus.CONFLICT, "Il comando è in conflitto con i dati già registrati", Map.of());
    }

    private static ResponseEntity<Errore> risposta(HttpStatus stato, String messaggio, Map<String, String> campi) {
        return ResponseEntity.status(stato).body(new Errore(stato.name(), messaggio, campi));
    }
}
