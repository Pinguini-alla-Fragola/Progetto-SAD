package it.unina.picus.utenze.api;

/** Segnala che l'operazione richiesta non è applicabile all'account (ruolo o stato non compatibile). */
public class OperazioneUtenzaNonConsentitaException extends RuntimeException {
    /**
     * Crea l'eccezione con il dettaglio dell'errore.
     *
     * @param message dettaglio da riportare al chiamante
     */
    public OperazioneUtenzaNonConsentitaException(String message) {
        super(message);
    }
}