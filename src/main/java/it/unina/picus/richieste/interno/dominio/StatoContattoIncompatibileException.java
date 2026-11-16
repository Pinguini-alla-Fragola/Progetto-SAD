package it.unina.picus.richieste.interno.dominio;

/** La pratica o il contatto non sono più nello stato necessario per registrare una risposta. */
public class StatoContattoIncompatibileException extends IllegalStateException {

    public StatoContattoIncompatibileException(String messaggio) {
        super(messaggio);
    }
}
