package it.unina.picus.utenze.interno.dominio;

import java.util.regex.Pattern;

/** Controlla email e telefono. */
public final class RegolaContatti implements RegolaDatiOperatore {

    static final int LUNGHEZZA_MAX_EMAIL = 254;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** Solo cifre e spazi, con un "+" facoltativo all'inizio. */
    private static final Pattern TELEFONO = Pattern.compile("^\\+?[0-9][0-9 ]{5,19}$");

    @Override
    public void verifica(DatiOperatore dati, Errori errori) {
        if (dati.email() == null || dati.email().length() > LUNGHEZZA_MAX_EMAIL
                || !EMAIL.matcher(dati.email()).matches()) {
            errori.aggiungi(CampoOperatore.EMAIL, "Inserisci un'email valida");
        }
        if (dati.telefono() == null || !TELEFONO.matcher(dati.telefono()).matches()) {
            errori.aggiungi(CampoOperatore.TELEFONO, "Inserisci un numero di telefono valido");
        }
    }
}
