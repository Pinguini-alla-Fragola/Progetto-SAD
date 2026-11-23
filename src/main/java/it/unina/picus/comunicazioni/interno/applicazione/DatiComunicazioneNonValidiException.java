package it.unina.picus.comunicazioni.interno.applicazione;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Indica quali campi del modulo di invio vanno corretti, con un messaggio per ciascuno. */
public class DatiComunicazioneNonValidiException extends IllegalArgumentException {

    private final Map<String, String> campi;

    /**
     * Crea l'eccezione con i campi da correggere.
     *
     * @param campi per ogni campo sbagliato, il messaggio da mostrare al Banco
     */
    public DatiComunicazioneNonValidiException(Map<String, String> campi) {
        super("Controlla i campi della comunicazione");
        this.campi = Collections.unmodifiableMap(new LinkedHashMap<>(campi));
    }

    /**
     * Restituisce i campi da correggere.
     *
     * @return campi e messaggi nell'ordine in cui sono stati controllati; non contiene i valori inseriti
     */
    public Map<String, String> campi() {
        return campi;
    }
}
