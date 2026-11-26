package it.unina.picus.monitoraggio.interno.dominio;

/** Segnala il tentativo di modificare una versione già pubblicata o legacy. */
public class VersioneCategorieImmutabileException extends RuntimeException {

    /**
     * Crea l'eccezione per modifica non consentita su versione immutabile.
     *
     * @param message messaggio controllato esposto al chiamante
     */
    public VersioneCategorieImmutabileException(String message) {
        super(message);
    }
}
