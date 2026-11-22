package it.unina.picus.monitoraggio.interno.dominio;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.DatiSchedaOperatore;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Tracciato provvisorio di primo invio TM-OP-01. I conteggi sono indipendenti
 * e l'elenco definitivo delle categorie e dei campi obbligatori è in raffinamento (#41).
 */
public final class TracciatoSchedaOperatore {

    /** Codice stabile della scheda personale. */
    public static final String CODICE = "TM-OP-01";
    /** Versione del tracciato iniziale applicata agli invii. */
    public static final String VERSIONE = "0.1";

    private static final int LUNGHEZZA_TESTO = 255;
    private static final Pattern CODICE_CATEGORIA = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}");
    private static final List<Campo> CAMPI = List.of(
            new Campo("nome", "testo", false),
            new Campo("cognome", "testo", false),
            new Campo("luogoNascita", "testo", false),
            new Campo("dataNascita", "data", false),
            new Campo("codiceFiscale", "testo", false),
            new Campo("qualifica", "testo", false),
            new Campo("tipologiaContratto", "testo", false),
            new Campo("areaLivello", "testo", false),
            new Campo("ore", "decimale", true),
            new Campo("costoOrarioLordo", "decimale", true),
            new Campo("conteggi", "conteggiPerCategoria", true));

    private TracciatoSchedaOperatore() {}

    /** {@return i campi compilabili del tracciato provvisorio, nell'ordine del modulo} */
    public static List<Campo> campi() {
        return CAMPI;
    }

    /**
     * Controlla i dati minimi richiesti e i limiti di rappresentazione del tracciato.
     *
     * @param trimestre periodo dichiarato
     * @param dati dati dichiarati
     * @throws DatiSchedaNonValidiException se uno o più campi non sono validi
     */
    public static void valida(Trimestre trimestre, DatiSchedaOperatore dati) {
        Map<String, String> errori = new LinkedHashMap<>();
        if (trimestre == null) {
            errori.put("trimestre", "Il periodo è obbligatorio");
        }
        if (dati == null) {
            errori.put("dati", "I dati della scheda sono obbligatori");
        } else {
            verificaTesti(dati, errori);
            if (dati.dataNascita() != null && (dati.dataNascita().getYear() < 1 || dati.dataNascita().getYear() > 9999)) {
                errori.put("dataNascita", "Usa una data con anno tra 1 e 9999");
            }
            verificaDecimale("ore", dati.ore(), errori);
            verificaDecimale("costoOrarioLordo", dati.costoOrarioLordo(), errori);
            if (dati.conteggi().entrySet().stream().anyMatch(voce ->
                    !CODICE_CATEGORIA.matcher(voce.getKey()).matches() || voce.getValue() < 0)) {
                errori.put("conteggi", "Usa codici di categoria validi e interi non negativi");
            }
        }
        if (!errori.isEmpty()) {
            throw new DatiSchedaNonValidiException(errori);
        }
    }

    /** Controlla la lunghezza dei testi facoltativi. */
    private static void verificaTesti(DatiSchedaOperatore dati, Map<String, String> errori) {
        verificaTesto("nome", dati.nome(), errori);
        verificaTesto("cognome", dati.cognome(), errori);
        verificaTesto("luogoNascita", dati.luogoNascita(), errori);
        verificaTesto("codiceFiscale", dati.codiceFiscale(), errori);
        verificaTesto("qualifica", dati.qualifica(), errori);
        verificaTesto("tipologiaContratto", dati.tipologiaContratto(), errori);
        verificaTesto("areaLivello", dati.areaLivello(), errori);
    }

    /** Allinea i testi al limite della colonna e ai caratteri supportati dal database. */
    private static void verificaTesto(String campo, String valore, Map<String, String> errori) {
        if (valore != null && (valore.codePointCount(0, valore.length()) > LUNGHEZZA_TESTO || valore.indexOf('\0') >= 0)) {
            errori.put(campo, "Usa al massimo 255 caratteri di testo");
        }
    }

    /** Verifica che il valore sia rappresentabile senza arrotondamento in NUMERIC(19,6). */
    private static void verificaDecimale(String campo, BigDecimal valore, Map<String, String> errori) {
        if (valore == null) {
            errori.put(campo, "Il valore è obbligatorio");
            return;
        }
        if (valore.signum() < 0 || valore.scale() < -12 || valore.scale() > 6
                || (long) valore.precision() - valore.scale() > 13) {
            errori.put(campo, "Usa un numero non negativo con al massimo 13 cifre intere e 6 decimali");
        }
    }

    /**
     * Campo del modulo compilabile.
     *
     * @param nome chiave JSON usata nell'invio
     * @param tipo tipo del dato rappresentato
     * @param obbligatorio indica se il tracciato richiede il valore
     */
    public record Campo(String nome, String tipo, boolean obbligatorio) {}
}
