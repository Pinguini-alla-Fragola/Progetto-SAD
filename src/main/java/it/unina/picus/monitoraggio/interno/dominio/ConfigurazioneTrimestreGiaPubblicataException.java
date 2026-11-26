package it.unina.picus.monitoraggio.interno.dominio;

/** Segnala il tentativo di assegnare o pubblicare un trimestre già configurato. */
public class ConfigurazioneTrimestreGiaPubblicataException extends RuntimeException {

    /**
     * Crea l'eccezione per conflitto su associazione di trimestre.
     *
     * @param message messaggio controllato esposto al chiamante
     */
    public ConfigurazioneTrimestreGiaPubblicataException(String message) {
        super(message);
    }
}
