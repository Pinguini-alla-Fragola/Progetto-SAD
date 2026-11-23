package it.unina.picus.utenze.interno.applicazione;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Indica quali campi del modulo vanno corretti, con un messaggio per ciascuno. */
public class DatiOperatoreNonValidiException extends IllegalArgumentException {

    private final Map<String, String> campi;

    /**
     * @param campi per ogni campo sbagliato, il messaggio da mostrare all'utente
     */
    public DatiOperatoreNonValidiException(Map<String, String> campi) {
        super("Controlla i campi del modulo");
        this.campi = Collections.unmodifiableMap(new LinkedHashMap<>(campi));
    }

    /**
     * @return i campi da correggere, nello stesso ordine in cui sono stati controllati, ciascuno con il suo
     *         messaggio; non contiene i valori inseriti
     */
    public Map<String, String> campi() {
        return campi;
    }
}
