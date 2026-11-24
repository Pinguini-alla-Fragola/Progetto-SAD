package it.unina.picus.utenze.interno.applicazione;

/**
 * La nuova password non rispetta la regola concordata. Il messaggio dice cosa correggere e non contiene mai la
 * password. Estende {@link IllegalArgumentException} perché chi già gestiva il rifiuto della password in
 * attivazione continua a funzionare.
 */
public class PasswordNonValidaException extends IllegalArgumentException {

    /**
     * Crea l'eccezione con il testo da mostrare all'utente.
     *
     * @param messaggio cosa correggere, senza il valore inserito
     */
    public PasswordNonValidaException(String messaggio) {
        super(messaggio);
    }
}
