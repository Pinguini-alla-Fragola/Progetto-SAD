package it.unina.picus.richieste.api;

import java.util.Map;
import java.io.Serial;

/** Errori riferiti esclusivamente ai campi ammessi, senza i valori ricevuti. */
public class DatiRichiestaNonValidiException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Indicazioni prive dei valori ricevuti.
     * @serial Nomi dei campi ammessi e messaggi controllati di correzione.
     */
    private final Map<String, String> campi;

    /**
     * Costruisce l'errore dai messaggi controllati di validazione.
     * @param campi nomi dei campi ammessi e rispettivi messaggi
     */
    public DatiRichiestaNonValidiException(Map<String, String> campi) {
        super("Controlla i campi della richiesta");
        this.campi = Map.copyOf(campi);
    }

    /**
     * Restituisce indicazioni immutabili per la correzione del modulo.
     * @return mappa di campi e messaggi privi dei valori ricevuti
     */
    public Map<String, String> campi() {
        return campi;
    }
}
