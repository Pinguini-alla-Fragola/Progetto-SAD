package it.unina.picus.utenze.interno.dominio;

import java.util.regex.Pattern;

/**
 * Controllo del codice fiscale di una persona: forma e carattere di controllo finale.
 * Vale anche nei casi di omocodia, in cui alcune cifre sono sostituite da lettere.
 */
public final class CodiceFiscale {

    /** Sedici caratteri; nelle posizioni numeriche ammette anche le lettere usate per l'omocodia. */
    private static final Pattern FORMA = Pattern.compile(
            "^[A-Z]{6}[0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$");
    private static final int CARATTERI_CONTROLLATI = 15;
    private static final int LETTERE = 26;
    /** Valore dei caratteri in posizione dispari (1ª, 3ª, ...): prima le cifre 0-9, poi le lettere A-Z. */
    private static final int[] VALORI_DISPARI = {
            1, 0, 5, 7, 9, 13, 15, 17, 19, 21,
            1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16, 10, 22, 25, 24, 23};

    private CodiceFiscale() {
    }

    /**
     * Indica se il codice ha la forma giusta e il carattere di controllo corretto.
     *
     * @param codice codice già in maiuscolo e senza spazi
     * @return vero se il codice è valido
     */
    public static boolean valido(String codice) {
        if (codice == null || !FORMA.matcher(codice).matches()) {
            return false;
        }
        int somma = 0;
        for (int posizione = 0; posizione < CARATTERI_CONTROLLATI; posizione++) {
            char carattere = codice.charAt(posizione);
            // La posizione 0 è la prima del codice, quindi dispari nel conteggio ufficiale che parte da 1.
            somma += posizione % 2 == 0 ? VALORI_DISPARI[indiceDispari(carattere)] : valorePari(carattere);
        }
        return codice.charAt(CARATTERI_CONTROLLATI) == (char) ('A' + somma % LETTERE);
    }

    private static int indiceDispari(char carattere) {
        return Character.isDigit(carattere) ? carattere - '0' : 10 + carattere - 'A';
    }

    private static int valorePari(char carattere) {
        return Character.isDigit(carattere) ? carattere - '0' : carattere - 'A';
    }
}
