package it.unina.picus.utenze.api;

/** Segnala che l'account richiesto non esiste. */
public class UtenzaNonTrovataException extends RuntimeException {
    /**
     * Crea l'eccezione con il dettaglio dell'errore.
     *
     * @param message dettaglio da riportare al chiamante
     */
    public UtenzaNonTrovataException(String message) {
        super(message);
    }
}