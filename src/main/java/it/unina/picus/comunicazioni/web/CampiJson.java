package it.unina.picus.comunicazioni.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import it.unina.picus.comunicazioni.interno.applicazione.DatiComunicazioneNonValidiException;

/**
 * Legge i campi JSON accettando soltanto il tipo previsto per ciascuno. Senza questo controllo un booleano o un
 * numero verrebbe convertito in testo in silenzio e inviato come comunicazione. Raccoglie gli errori di tutti i
 * campi e li segnala insieme.
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

    /**
     * Una lista di testi, con al massimo {@code massimo} elementi. Oltre il limite gli elementi non vengono
     * nemmeno esaminati.
     */
    List<String> testi(List<Object> valori, String campo, int massimo) {
        if (valori == null) {
            return null;
        }
        if (valori.size() > massimo) {
            errori.putIfAbsent(campo, "Il gruppo può avere al massimo " + massimo + " account");
            return List.of();
        }
        List<String> testi = new ArrayList<>();
        for (Object valore : valori) {
            if (!(valore instanceof String testo)) {
                errori.putIfAbsent(campo, "Il gruppo contiene un account non riconosciuto");
                return List.of();
            }
            testi.add(testo);
        }
        return testi;
    }

    /** Se qualche campo ha il tipo sbagliato, ferma la richiesta indicando quali. */
    void verifica() {
        if (!errori.isEmpty()) {
            throw new DatiComunicazioneNonValidiException(errori);
        }
    }
}
