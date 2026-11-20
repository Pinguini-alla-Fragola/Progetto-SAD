package it.unina.picus.utenze.interno.dominio;

import java.util.regex.Pattern;

/** Controlla nome, cognome e codice fiscale. */
public final class RegolaAnagrafica implements RegolaDatiOperatore {

    static final int LUNGHEZZA_MAX_NOME = 100;
    /** Codice fiscale di 16 caratteri. Ammette anche le lettere usate al posto delle cifre nei casi di omocodia. */
    private static final Pattern CODICE_FISCALE = Pattern.compile(
            "^[A-Z]{6}[0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$");

    @Override
    public void verifica(DatiOperatore dati, Errori errori) {
        if (!nomeValido(dati.nome())) {
            errori.aggiungi(CampoOperatore.NOME, "Inserisci il nome");
        }
        if (!nomeValido(dati.cognome())) {
            errori.aggiungi(CampoOperatore.COGNOME, "Inserisci il cognome");
        }
        if (dati.codiceFiscale() == null || !CODICE_FISCALE.matcher(dati.codiceFiscale()).matches()) {
            errori.aggiungi(CampoOperatore.CODICE_FISCALE, "Inserisci un codice fiscale valido");
        }
    }

    private static boolean nomeValido(String valore) {
        return valore != null && valore.length() <= LUNGHEZZA_MAX_NOME;
    }
}
