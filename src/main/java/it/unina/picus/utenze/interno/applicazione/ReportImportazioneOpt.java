package it.unina.picus.utenze.interno.applicazione;

import java.util.List;

/**
 * Resoconto dell'importazione delle OPT, privo dei valori anagrafici presenti nel CSV.
 *
 * @param righe esiti nell'ordine del file
 */
public record ReportImportazioneOpt(List<RisultatoRiga> righe) {

    public ReportImportazioneOpt {
        righe = List.copyOf(righe);
    }

    /** Esito della singola riga del CSV. */
    public enum Esito {
        CREATA, AGGIORNATA, INVARIATA, SCARTATA
    }

    /**
     * Esito con il numero fisico della prima linea della riga CSV.
     *
     * @param numeroRiga numero a partire da 1 per l'intestazione
     * @param esito risultato dell'elaborazione
     * @param motivi motivi dello scarto o segnalazioni per il Banco
     */
    public record RisultatoRiga(long numeroRiga, Esito esito, List<String> motivi) {

        public RisultatoRiga {
            motivi = List.copyOf(motivi);
        }
    }
}
