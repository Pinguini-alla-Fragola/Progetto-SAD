package it.unina.picus.richieste.web;

import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.RisorsaRichiesteNonTrovataException;
import it.unina.picus.richieste.api.ConflittoRichiestaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/** Risposte con messaggi controllati: mai valori del corpo, descrizioni o messaggi del parser. */
@RestControllerAdvice(basePackages = {"it.unina.picus.richieste.web", "it.unina.picus.catalogo.web",
        "it.unina.picus.comunicazioni.web"})
public class ErroriRichieste {
    public record Errore(String codice, String messaggio, Map<String, String> campi) {}

    @ExceptionHandler(DatiRichiestaNonValidiException.class)
    public ResponseEntity<Errore> validazione(DatiRichiestaNonValidiException ex) {
        return risposta(HttpStatus.BAD_REQUEST, "Controlla i campi della richiesta", ex.campi());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Errore> formatoNonValido() {
        return risposta(HttpStatus.BAD_REQUEST, "Formato dei dati non valido", Map.of());
    }

    @ExceptionHandler(RisorsaRichiesteNonTrovataException.class)
    public ResponseEntity<Errore> nonTrovata() {
        return risposta(HttpStatus.NOT_FOUND, "Richiesta non trovata", Map.of());
    }

    @ExceptionHandler(ConflittoRichiestaException.class)
    public ResponseEntity<Errore> conflitto() {
        return risposta(HttpStatus.CONFLICT, "Il comando è in conflitto con i dati già registrati", Map.of());
    }

    private static ResponseEntity<Errore> risposta(HttpStatus stato, String messaggio, Map<String, String> campi) {
        return ResponseEntity.status(stato).body(new Errore(stato.name(), messaggio, campi));
    }
}
