package it.unina.picus.utenze.interno.applicazione;

/**
 * Il link di recupero non si può usare: non esiste, è scaduto, è già stato usato oppure l'account non è più attivo.
 * Il messaggio è lo stesso per tutti i casi, così chi non ha un link valido non scopre quale sia il motivo.
 */
public class LinkRecuperoNonValidoException extends RuntimeException {

    /** Crea l'eccezione con il testo da mostrare all'utente, che invita a ripetere la richiesta. */
    public LinkRecuperoNonValidoException() {
        super("Il link non è valido, è scaduto oppure è già stato utilizzato. Richiedine uno nuovo.");
    }
}
