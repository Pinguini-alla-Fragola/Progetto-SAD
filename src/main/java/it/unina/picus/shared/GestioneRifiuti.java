package it.unina.picus.shared;

import java.util.Date;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Traduce in {@code 403} il diniego deciso dalle regole dei moduli (#28).
 *
 * <p>Gestisce soltanto {@link AccessoNegatoException}, cioè un esito applicativo esplicito: gli
 * altri guasti restano sul percorso {@code 500} e la risorsa inesistente spetta al controller
 * interessato. La risposta è costruita qui e non passa da {@code /error}, quindi non dipende dalla
 * configurazione di Spring: non contiene mai il messaggio dell'eccezione né dati della risorsa.
 *
 * <p>Le richieste di API, riconosciute da {@link RichiestaApi} come dalla sicurezza, ricevono il
 * solo codice di stato, come nei gestori locali dei controller. Le pagine ricevono la vista
 * {@code error} con soli {@code timestamp}, {@code status} ed {@code error}, cioè i campi standard
 * di Spring senza il messaggio. Un {@code @ExceptionHandler} scritto dentro un controller ha la
 * precedenza su questo.
 */
@ControllerAdvice
class GestioneRifiuti {

    /** Accesso non consentito dal ruolo o dalla relazione con la risorsa. */
    @ExceptionHandler(AccessoNegatoException.class)
    Object accessoNegato(HttpServletRequest richiesta) {
        if (RichiestaApi.e(richiesta)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return new ModelAndView("error", Map.of(
                "timestamp", new Date(),
                "status", HttpStatus.FORBIDDEN.value(),
                "error", HttpStatus.FORBIDDEN.getReasonPhrase()), HttpStatus.FORBIDDEN);
    }
}
