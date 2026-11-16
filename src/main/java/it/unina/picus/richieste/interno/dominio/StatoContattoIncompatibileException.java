package it.unina.picus.richieste.interno.dominio;

/**
 * Indica che la richiesta o il contatto si trovano in uno stato incompatibile con la risposta.
 */
public class StatoContattoIncompatibileException extends IllegalStateException {

    /**
     * Crea l'eccezione con il motivo del rifiuto.
     *
     * @param messaggio descrizione dello stato incompatibile
     */
    public StatoContattoIncompatibileException(String messaggio) {
        super(messaggio);
    }
}
