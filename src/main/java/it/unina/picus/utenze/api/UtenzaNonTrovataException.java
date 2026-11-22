package it.unina.picus.utenze.api;

/** Segnala che l'account richiesto non esiste. */
public class UtenzaNonTrovataException extends RuntimeException {
    public UtenzaNonTrovataException(String message) {
        super(message);
    }
}