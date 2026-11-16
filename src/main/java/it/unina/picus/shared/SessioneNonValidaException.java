package it.unina.picus.shared;

/** Mancanza di un'identità PICUS attiva associata all'autenticazione corrente. */
public class SessioneNonValidaException extends RuntimeException {
    public SessioneNonValidaException() {
        super("Sessione assente o non più valida");
    }
}
