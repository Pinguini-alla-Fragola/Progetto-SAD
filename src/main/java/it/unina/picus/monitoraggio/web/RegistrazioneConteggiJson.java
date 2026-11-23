package it.unina.picus.monitoraggio.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.unina.picus.monitoraggio.api.DatiConteggiNonValidiException;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.VoceMisura;

/** Lettura esplicita dei campi della registrazione conteggi senza coercizione dei numeri frazionari. */
final class RegistrazioneConteggiJson {

    static final long MASSIMO_INTERO_SICURO_JS = 9_007_199_254_740_991L;

    private static final Set<String> CAMPI_RADICE = Set.of("anno", "trimestre", "voci");
    private static final Set<String> CAMPI_VOCE = Set.of("misuraId", "voce", "valore");

    private RegistrazioneConteggiJson() {}

    record RichiestaLetta(Trimestre trimestre, List<VoceMisura> voci) {}

    static RichiestaLetta leggi(Map<String, Object> corpo) {
        if (corpo == null || !CAMPI_RADICE.containsAll(corpo.keySet())) {
            throw new DatiConteggiNonValidiException(
                    "Struttura della richiesta non valida.",
                    Map.of("voci", "Sono ammessi solo i campi anno, trimestre e voci."));
        }

        Map<String, String> errori = new LinkedHashMap<>();

        Integer anno = leggiIntero(corpo.get("anno"));
        if (anno == null) {
            errori.put("anno", "L'anno deve essere un numero intero.");
        }

        Integer trimestre = leggiIntero(corpo.get("trimestre"));
        if (trimestre == null) {
            errori.put("trimestre", "Il trimestre deve essere un numero intero tra 1 e 4.");
        }

        List<VoceMisura> vociLette = new ArrayList<>();
        Object vociObj = corpo.get("voci");
        if (!(vociObj instanceof List<?> listaVoci) || listaVoci.isEmpty()) {
            errori.put("voci", "Inserisci almeno una voce valida.");
        } else {
            for (int i = 0; i < listaVoci.size(); i++) {
                Object elem = listaVoci.get(i);
                if (!(elem instanceof Map<?, ?> mappaVoce) || !CAMPI_VOCE.containsAll(mappaVoce.keySet())) {
                    errori.put("voci[" + i + "]", "Struttura della voce non valida.");
                    continue;
                }

                Long misuraId = leggiLong(mappaVoce.get("misuraId"));
                if (misuraId == null) {
                    errori.put("voci[" + i + "].misuraId", "L'identificativo della misura deve essere un numero intero.");
                }

                String voce = null;
                if (mappaVoce.get("voce") instanceof String s) {
                    voce = s;
                } else {
                    errori.put("voci[" + i + "].voce", "Indica il codice della voce.");
                }

                Long valore = leggiConteggio(mappaVoce.get("valore"));
                if (valore == null) {
                    errori.put("voci[" + i + "].valore", "Il conteggio deve essere un numero intero non negativo.");
                }

                if (misuraId != null && voce != null && valore != null) {
                    vociLette.add(new VoceMisura(misuraId, voce, valore));
                }
            }
        }

        if (!errori.isEmpty()) {
            throw new DatiConteggiNonValidiException("Controlla i campi dei conteggi trimestrali.", errori);
        }

        Trimestre periodo;
        try {
            periodo = new Trimestre(anno, trimestre);
        } catch (IllegalArgumentException ex) {
            Map<String, String> erroriPeriodo = new LinkedHashMap<>();
            if (anno < 1) {
                erroriPeriodo.put("anno", "L'anno deve essere positivo.");
            }
            if (trimestre < 1 || trimestre > 4) {
                erroriPeriodo.put("trimestre", "Il trimestre deve essere compreso tra 1 e 4.");
            }
            throw new DatiConteggiNonValidiException(ex.getMessage(), erroriPeriodo);
        }

        return new RichiestaLetta(periodo, List.copyOf(vociLette));
    }

    private static Integer leggiIntero(Object valore) {
        if (valore instanceof Integer i) {
            return i;
        }
        if (valore instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            return l.intValue();
        }
        return null;
    }

    private static Long leggiConteggio(Object valore) {
        // I client JavaScript possono inviare come testo i conteggi oltre la propria precisione intera.
        if (valore instanceof String testo && testo.matches("[1-9][0-9]{15,18}")) {
            try {
                long numero = Long.parseLong(testo);
                return numero > MASSIMO_INTERO_SICURO_JS ? numero : null;
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return leggiLong(valore);
    }

    private static Long leggiLong(Object valore) {
        if (valore instanceof Integer i) {
            return i.longValue();
        }
        if (valore instanceof Long l) {
            return l;
        }
        return null;
    }
}