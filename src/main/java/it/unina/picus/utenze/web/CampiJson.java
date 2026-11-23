package it.unina.picus.utenze.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import it.unina.picus.utenze.interno.applicazione.DatiOperatoreNonValidiException;

/**
 * Legge i campi JSON accettando soltanto il tipo previsto per ciascuno. Senza questo controllo un booleano o un
 * numero in un campo di testo verrebbe convertito in testo in silenzio. Raccoglie gli errori di tutti i campi e
 * li segnala insieme.
 */
final class CampiJson {

    private final Map<String, String> errori = new LinkedHashMap<>();

    /** Un testo oppure niente; qualsiasi altro valore JSON è un errore sul campo. */
    String testo(Object valore, String campo) {
        if (valore == null || valore instanceof String) {
            return (String) valore;
        }
        errori.putIfAbsent(campo, "Il campo deve contenere un testo");
        return null;
    }

    /** Un vero o falso; se manca vale falso. */
    boolean scelta(Object valore, String campo) {
        if (valore == null) {
            return false;
        }
        if (valore instanceof Boolean scelta) {
            return scelta;
        }
        errori.putIfAbsent(campo, "Il campo deve valere vero o falso");
        return false;
    }

    /** Solo numeri interi: un numero con decimali, un testo o un valore vuoto vengono rifiutati. */
    List<Long> identificativi(List<Object> valori, String campo, String messaggio) {
        if (valori == null) {
            return List.of();
        }
        List<Long> identificativi = new ArrayList<>();
        for (Object valore : valori) {
            if (!(valore instanceof Integer || valore instanceof Long)) {
                errori.putIfAbsent(campo, messaggio);
                return List.of();
            }
            identificativi.add(((Number) valore).longValue());
        }
        return identificativi;
    }

    /** Se qualche campo ha il tipo sbagliato, ferma la richiesta indicando quali. */
    void verifica() {
        if (!errori.isEmpty()) {
            throw new DatiOperatoreNonValidiException(errori);
        }
    }
}
