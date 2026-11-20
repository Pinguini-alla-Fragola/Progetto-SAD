package it.unina.picus.utenze.interno.dominio;

/** La registrazione è fallita perché, nello stesso momento, un'altra ha usato la stessa email o lo stesso codice fiscale. */
public class RegistrazioneInConflittoException extends RuntimeException {

    public RegistrazioneInConflittoException(Throwable causa) {
        super("Email o codice fiscale già registrati", causa);
    }
}
