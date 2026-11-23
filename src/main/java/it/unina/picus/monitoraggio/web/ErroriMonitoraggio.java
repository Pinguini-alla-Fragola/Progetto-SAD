package it.unina.picus.monitoraggio.web;

import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import it.unina.picus.monitoraggio.api.DatiConteggiNonValidiException;

@RestControllerAdvice(basePackages = "it.unina.picus.monitoraggio.web")
class ErroriMonitoraggio {

    record Errore(String codice, String messaggio, Map<String, String> campi) {}

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<Void> accessoNegato() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(DatiConteggiNonValidiException.class)
    ResponseEntity<Errore> validazione(DatiConteggiNonValidiException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new Errore("BAD_REQUEST", ex.getMessage(), ex.campi()));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Errore> argomentoNonValido(Exception ex) {
        String msg = ex instanceof IllegalArgumentException ? ex.getMessage() : "Formato dei dati non valido.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new Errore("BAD_REQUEST", msg, Map.of()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Errore> nonTrovato(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new Errore("NOT_FOUND", ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Errore> statoNonConsentito(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Errore("CONFLICT", ex.getMessage(), Map.of()));
    }
}