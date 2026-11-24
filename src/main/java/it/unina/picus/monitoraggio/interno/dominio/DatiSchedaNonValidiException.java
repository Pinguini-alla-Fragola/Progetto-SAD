package it.unina.picus.monitoraggio.interno.dominio;

import java.util.Map;

/** Errori di validazione della scheda, indicizzati per nome del campo. */
public final class DatiSchedaNonValidiException extends IllegalArgumentException {

    private final Map<String, String> campi;

    /**
     * Acquisisce una copia immutabile degli errori da restituire al compilatore.
     *
     * @param campi messaggi privi dei valori dichiarati
     */
    public DatiSchedaNonValidiException(Map<String, String> campi) {
        super("Dati della scheda non validi");
        this.campi = Map.copyOf(campi);
    }

    /** {@return gli errori per campo, senza i valori dichiarati} */
    public Map<String, String> campi() {
        return campi;
    }
}
