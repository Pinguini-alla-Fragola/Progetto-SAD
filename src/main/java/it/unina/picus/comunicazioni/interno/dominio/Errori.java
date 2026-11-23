package it.unina.picus.comunicazioni.interno.dominio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raccoglie gli errori trovati su un modulo, uno per campo, così chi invia vede subito tutto
 * quello che deve correggere. Se un campo ha già un errore, il primo messaggio resta quello valido.
 */
public final class Errori {

    private final Map<String, String> perCampo = new LinkedHashMap<>();

    public void aggiungi(String campo, String messaggio) {
        perCampo.putIfAbsent(campo, messaggio);
    }

    public boolean vuoto() {
        return perCampo.isEmpty();
    }

    public Map<String, String> perCampo() {
        return Collections.unmodifiableMap(perCampo);
    }
}
