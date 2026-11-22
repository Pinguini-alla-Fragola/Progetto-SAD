package it.unina.picus.monitoraggio.interno.applicazione;

import java.util.Map;

public class DatiConteggiNonValidiException extends IllegalArgumentException {
    private final Map<String, String> campi;

    public DatiConteggiNonValidiException(String messaggio, Map<String, String> campi) {
        super(messaggio);
        this.campi = Map.copyOf(campi);
    }

    public Map<String, String> campi() {
        return campi;
    }
}