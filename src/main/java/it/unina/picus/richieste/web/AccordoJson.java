package it.unina.picus.richieste.web;

import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.RichiesteApi.AccordoADistanza;
import it.unina.picus.richieste.api.RichiesteApi.AccordoInPresenza;
import it.unina.picus.richieste.api.RichiesteApi.DettagliAccordo;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Converte il corpo HTTP nelle due varianti immutabili dell'accordo. */
final class AccordoJson {
    private static final Set<String> CAMPI = Set.of("modalita", "dataOra", "luogo", "link",
            "nomeReferente", "cognomeReferente", "telefonoReferente");
    private static final Set<String> CAMPI_PRESENZA = Set.of("luogo", "nomeReferente", "cognomeReferente", "telefonoReferente");

    private AccordoJson() {}

    /**
     * Traduce il corpo e rifiuta proprietà estranee o significative per l'altra modalità.
     *
     * @param corpo proprietà JSON della conferma
     * @return dettagli tipizzati, da validare nel dominio
     * @throws DatiRichiestaNonValidiException se il formato o la modalità sono invalidi
     */
    static DettagliAccordo leggi(Map<String, Object> corpo) {
        if (corpo == null || !CAMPI.containsAll(corpo.keySet())) {
            throw new DatiRichiestaNonValidiException(Map.of("accordo", "Usa solo i campi della conferma"));
        }
        if (!(corpo.get("modalita") instanceof String modalita)) {
            throw modalitaNonValida();
        }
        Map<String, String> errori = new LinkedHashMap<>();
        Instant data = dataOra(corpo, errori);
        DettagliAccordo accordo = switch (modalita) {
            case "IN_PRESENZA" -> {
                verificaCampiInutilizzati(corpo, Set.of("link"), errori);
                yield new AccordoInPresenza(data, testo(corpo, "luogo", errori), testo(corpo, "nomeReferente", errori),
                        testo(corpo, "cognomeReferente", errori), testo(corpo, "telefonoReferente", errori));
            }
            case "A_DISTANZA" -> {
                verificaCampiInutilizzati(corpo, CAMPI_PRESENZA, errori);
                yield new AccordoADistanza(data, link(corpo, errori));
            }
            default -> throw modalitaNonValida();
        };
        if (!errori.isEmpty()) {
            throw new DatiRichiestaNonValidiException(errori);
        }
        return accordo;
    }

    private static void verificaCampiInutilizzati(Map<String, Object> corpo, Set<String> campi, Map<String, String> errori) {
        for (String campo : campi) {
            Object valore = corpo.get(campo);
            if (valore != null && (!(valore instanceof String testo) || !testo.isBlank())) {
                errori.put(campo, "Campo non previsto per la modalità scelta");
            }
        }
    }

    private static String testo(Map<String, Object> corpo, String campo, Map<String, String> errori) {
        Object valore = corpo.get(campo);
        if (valore == null || valore instanceof String) {
            return (String) valore;
        }
        errori.put(campo, "Inserisci un valore testuale");
        return null;
    }

    private static Instant dataOra(Map<String, Object> corpo, Map<String, String> errori) {
        String valore = testo(corpo, "dataOra", errori);
        if (valore == null || valore.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valore.strip()).toInstant();
        } catch (DateTimeParseException ex) {
            errori.put("dataOra", "Indica data e ora in formato ISO 8601 con fuso orario");
            return null;
        }
    }

    private static URI link(Map<String, Object> corpo, Map<String, String> errori) {
        String valore = testo(corpo, "link", errori);
        if (valore == null || valore.isBlank()) {
            return null;
        }
        try {
            return URI.create(valore.strip());
        } catch (IllegalArgumentException ex) {
            errori.put("link", "Indica un link valido");
            return null;
        }
    }

    private static DatiRichiestaNonValidiException modalitaNonValida() {
        return new DatiRichiestaNonValidiException(Map.of("modalita", "Seleziona IN_PRESENZA o A_DISTANZA"));
    }
}
