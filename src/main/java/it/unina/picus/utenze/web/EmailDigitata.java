package it.unina.picus.utenze.web;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Legge l'indirizzo scritto da un utente non autenticato in un modulo di richiesta (recupero, nuovo link). Il
 * controllo è volutamente leggero: un indirizzo scritto male non corrisponde a nessun account, e la risposta non
 * deve mai dipendere dall'esistenza dell'account.
 */
final class EmailDigitata {

    static final String MESSAGGIO_NON_VALIDA = "Inserisci un indirizzo email valido.";

    private static final int LUNGHEZZA_MAX = 254;
    private static final Pattern FORMA = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private EmailDigitata() {
    }

    /**
     * @param valore valore JSON del campo, che può essere di qualsiasi tipo: un numero o un booleano non
     *        diventano testo
     * @return l'indirizzo pulito e in minuscolo, oppure vuoto se non è un testo nella forma di un indirizzo
     */
    static Optional<String> leggi(Object valore) {
        if (!(valore instanceof String testo)) {
            return Optional.empty();
        }
        String email = testo.strip().toLowerCase(Locale.ROOT);
        if (email.length() > LUNGHEZZA_MAX || !FORMA.matcher(email).matches()) {
            return Optional.empty();
        }
        return Optional.of(email);
    }
}
