package it.unina.picus.monitoraggio.interno.dominio;

import java.util.Arrays;
import java.util.Objects;

/** Tracciato di monitoraggio gestito con versioni immutabili per trimestre (#181, #41). */
public enum TracciatoMonitoraggio {
    /** Tracciato della scheda personale degli operatori del Banco. */
    TM_OP_01("TM-OP-01");

    private final String codice;

    TracciatoMonitoraggio(String codice) {
        this.codice = codice;
    }

    /** {@return codice testuale ufficiale del tracciato} */
    public String codice() {
        return codice;
    }

    /**
     * Risolve il tracciato dal codice ufficiale {@code TM-OP-01}.
     *
     * @param codice codice testuale del tracciato
     * @return tracciato corrispondente
     * @throws IllegalArgumentException se il codice è sconosciuto o mancante
     */
    public static TracciatoMonitoraggio daCodice(String codice) {
        if (codice == null) {
            throw new IllegalArgumentException("Il tracciato è obbligatorio");
        }
        String normalizzato = codice.trim();
        return Arrays.stream(values())
                .filter(v -> Objects.equals(v.codice, normalizzato))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tracciato non valido: " + normalizzato));
    }
}
