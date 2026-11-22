package it.unina.picus.catalogo.web;

import it.unina.picus.catalogo.interno.dominio.CodiceMisuraDuplicatoException;
import it.unina.picus.catalogo.interno.dominio.DatiMisuraNonValidiException;
import it.unina.picus.catalogo.interno.dominio.MisuraNonTrovataException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Traduce gli esiti di errore specifici del modulo Catalogo in risposte JSON uniformi,
 * senza esporre valori ricevuti dal client o dettagli interni.
 */
@RestControllerAdvice(basePackages = "it.unina.picus.catalogo.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErroriCatalogo {

    /**
     * Struttura uniforme degli errori restituiti dagli endpoint di Catalogo.
     *
     * @param codice codice testuale dello stato HTTP
     * @param messaggio messaggio controllato per l'utente
     * @param campi eventuale mappa degli errori per singolo campo
     */
    public record ErroreCatalogo(String codice, String messaggio, Map<String, String> campi) {
    }

    /**
     * Gestisce il conflitto di unicità sul codice misura ({@code 409 Conflict}).
     *
     * @param ex eccezione di codice duplicato
     * @return risposta HTTP 409 con indicazione sul campo {@code codice}
     */
    @ExceptionHandler(CodiceMisuraDuplicatoException.class)
    public ResponseEntity<ErroreCatalogo> codiceDuplicato(CodiceMisuraDuplicatoException ex) {
        return risposta(HttpStatus.CONFLICT, "Esiste già una misura con questo codice", ex.campi());
    }

    /**
     * Gestisce i dati di misura mancanti o non validi ({@code 400 Bad Request}).
     *
     * @param ex eccezione di validazione dei dati misura
     * @return risposta HTTP 400 con la mappa dei campi errati
     */
    @ExceptionHandler(DatiMisuraNonValidiException.class)
    public ResponseEntity<ErroreCatalogo> datiNonValidi(DatiMisuraNonValidiException ex) {
        return risposta(HttpStatus.BAD_REQUEST, "Controlla i campi della misura", ex.campi());
    }

    /**
     * Gestisce l'assenza della misura richiesta ({@code 404 Not Found}).
     *
     * @return risposta HTTP 404 priva di dettagli sulla risorsa
     */
    @ExceptionHandler(MisuraNonTrovataException.class)
    public ResponseEntity<ErroreCatalogo> nonTrovata() {
        return risposta(HttpStatus.NOT_FOUND, "Misura non trovata", Map.of());
    }

    private static ResponseEntity<ErroreCatalogo> risposta(
            HttpStatus stato, String messaggio, Map<String, String> campi) {
        return ResponseEntity.status(stato).body(new ErroreCatalogo(stato.name(), messaggio, campi));
    }
}
