package it.unina.picus.utenze.interno.applicazione;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import it.unina.picus.utenze.interno.dominio.DatiOperatore;

/** Ripulisce i campi ricevuti, così che le regole lavorino sempre su dati nella stessa forma. */
final class NormalizzatoreDatiOperatore {

    private NormalizzatoreDatiOperatore() {
    }

    static DatiOperatore normalizza(ComandoCreaOperatore comando) {
        if (comando == null) {
            return new DatiOperatore(null, null, null, null, null, null, null, Set.of(), false);
        }
        return new DatiOperatore(
                testo(comando.nome()),
                testo(comando.cognome()),
                maiuscolo(testo(comando.codiceFiscale())),
                minuscolo(testo(comando.email())),
                testo(comando.telefono()),
                testo(comando.comuneCodice()),
                testo(comando.tipologia()),
                misure(comando.misure()),
                comando.centroPicus());
    }

    /** Toglie gli spazi ai bordi; un testo vuoto diventa {@code null}. */
    private static String testo(String valore) {
        if (valore == null) {
            return null;
        }
        String ripulito = valore.strip();
        return ripulito.isEmpty() ? null : ripulito;
    }

    private static String maiuscolo(String valore) {
        return valore == null ? null : valore.toUpperCase(Locale.ROOT);
    }

    private static String minuscolo(String valore) {
        return valore == null ? null : valore.toLowerCase(Locale.ROOT);
    }

    private static Set<Long> misure(List<Long> valori) {
        if (valori == null) {
            return Set.of();
        }
        return valori.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }
}
