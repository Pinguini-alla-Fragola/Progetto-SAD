package it.unina.picus.monitoraggio.interno.dominio;

/** Segnala l'assenza di una configurazione pubblicata per un trimestre che la richiede. */
public class ConfigurazioneTrimestreAssenteException extends IllegalStateException {

    /**
     * Crea l'eccezione per trimestre non ancora configurato dal Banco.
     *
     * @param message messaggio controllato esposto al chiamante
     */
    public ConfigurazioneTrimestreAssenteException(String message) {
        super(message);
    }
}
