package it.unina.picus.shared;

import java.io.IOException;
import java.util.NoSuchElementException;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Traduce le eccezioni dichiarate dai contratti dei moduli nelle risposte HTTP (#28).
 *
 * <p>{@link SecurityException} diventa {@code 403} e {@link NoSuchElementException} diventa
 * {@code 404}. La risposta non riporta il messaggio dell'eccezione né dati della richiesta:
 * la pagina o il JSON di errore li compone Spring Boot sul percorso {@code /error}.
 * Un {@code @ExceptionHandler} scritto dentro un controller ha la precedenza su questo.
 */



@ControllerAdvice
class GestioneRifiuti {

    /** Accesso non consentito dal ruolo o dalla relazione con la risorsa. */
    @ExceptionHandler(SecurityException.class)
    void accessoNegato(HttpServletResponse risposta) throws IOException {
        risposta.sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    /** Risorsa inesistente. */
    @ExceptionHandler(NoSuchElementException.class)
    void nonTrovata(HttpServletResponse risposta) throws IOException {
        risposta.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
