package it.unina.picus.shared;

/**
 * Accesso negato dalle regole di un modulo (#28).
 *
 * <p>È una {@link SecurityException}, come dichiarano i contratti dei moduli, ma identifica un
 * diniego deciso dalle nostre regole: {@code GestioneRifiuti} traduce in {@code 403} solo questa.
 * Il messaggio serve ai log e non va mai mostrato al client.
 */
public final class AccessoNegatoException extends SecurityException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea l'eccezione.
     *
     * @param messaggio motivo del diniego, senza dati della risorsa
     */
    public AccessoNegatoException(String messaggio) {
        super(messaggio);
    }
}
