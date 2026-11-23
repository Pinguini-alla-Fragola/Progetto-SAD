package it.unina.picus.utenze.interno.dominio;

import java.util.Set;

/**
 * Dati di un Operatore da creare, già ripuliti: spazi tolti, codice fiscale in maiuscolo, email in minuscolo.
 * Un testo mancante o vuoto vale {@code null}; sta alle regole di {@link RegolaDatiOperatore} segnalarlo.
 */
public record DatiOperatore(String nome, String cognome, String codiceFiscale, String email, String telefono,
        String comuneCodice, String tipologia, Set<Long> misure, boolean centroPicus) {

    public DatiOperatore {
        misure = Set.copyOf(misure);
    }

    public String nomeVisualizzato() {
        return nome + " " + cognome;
    }
}
