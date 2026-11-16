package it.unina.picus.richieste.web;

import it.unina.picus.richieste.api.DatiRichiestaNonValidiException;
import it.unina.picus.richieste.api.RichiesteApi.DatiInvio;
import it.unina.picus.richieste.api.RichiesteApi.TipoDestinatario;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Lista esplicita dei campi applicativi controllata prima di costruire il DTO. */
final class InvioRichiestaJson {
    private static final Set<String> CAMPI = Set.of("misuraId", "tipoDestinatario", "comuneCodice", "description");

    private InvioRichiestaJson() {}

    static DatiInvio leggi(Map<String, Object> corpo) {
        if (corpo == null || !CAMPI.containsAll(corpo.keySet())) {
            throw new DatiRichiestaNonValidiException(Map.of("richiesta", "Sono ammessi solo i campi del modulo"));
        }
        Map<String, String> errori = new LinkedHashMap<>();
        long misura = 0;
        Object valoreMisura = corpo.get("misuraId");
        if (valoreMisura instanceof Integer || valoreMisura instanceof Long) {
            misura = ((Number) valoreMisura).longValue();
        } else {
            errori.put("misuraId", "Seleziona una misura tramite il suo identificativo numerico");
        }
        TipoDestinatario tipo = null;
        if (corpo.get("tipoDestinatario") instanceof String valoreTipo) {
            try {
                tipo = TipoDestinatario.valueOf(valoreTipo);
            } catch (IllegalArgumentException ex) {
                errori.put("tipoDestinatario", "Seleziona PERSONA o NUCLEO");
            }
        } else {
            errori.put("tipoDestinatario", "Seleziona PERSONA o NUCLEO");
        }
        String comune = null;
        if (corpo.get("comuneCodice") instanceof String valoreComune) {
            comune = valoreComune;
        } else {
            errori.put("comuneCodice", "Seleziona un comune del catalogo");
        }
        Object descrizione = corpo.get("description");
        if (descrizione != null && !(descrizione instanceof String)) {
            errori.put("description", "Usa testo semplice senza identificativi degli assistiti");
        }
        if (!errori.isEmpty()) {
            throw new DatiRichiestaNonValidiException(errori);
        }
        return new DatiInvio(misura, tipo, comune, (String) descrizione);
    }

    static UUID riferimento(String header) {
        if (header == null) {
            return null;
        }
        try {
            UUID riferimento = UUID.fromString(header);
            if (!riferimento.toString().equalsIgnoreCase(header)) {
                throw new IllegalArgumentException();
            }
            return riferimento;
        } catch (IllegalArgumentException ex) {
            throw new DatiRichiestaNonValidiException(Map.of("riferimentoInvio", "Usa un UUID valido in Idempotency-Key"));
        }
    }
}
