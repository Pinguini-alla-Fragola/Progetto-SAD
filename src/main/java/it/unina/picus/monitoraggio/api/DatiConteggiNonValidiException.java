package it.unina.picus.monitoraggio.api;

import java.util.Map;

/**
 * Eccezione sollevata quando i dati inviati per i conteggi trimestrali violano i vincoli
 * sintattici o di dominio del tracciato.
 */
public class DatiConteggiNonValidiException extends IllegalArgumentException {
    private final Map<String, String> campi;

    /**
     * Crea una nuova eccezione con un messaggio riepilogativo e il dettaglio degli errori per campo.
     *
     * @param messaggio descrizione generale dell'errore di validazione
     * @param campi mappa immutabile che associa il nome del campo al relativo messaggio di errore
     */
    public DatiConteggiNonValidiException(String messaggio, Map<String, String> campi) {
        super(messaggio);
        this.campi = Map.copyOf(campi);
    }

    /**
     * Restituisce la mappa degli errori associati ai singoli campi del tracciato.
     *
     * @return mappa campo-messaggio di errore
     */
    public Map<String, String> campi() {
        return campi;
    }
}