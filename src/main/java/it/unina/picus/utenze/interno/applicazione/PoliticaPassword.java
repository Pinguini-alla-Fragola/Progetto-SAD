package it.unina.picus.utenze.interno.applicazione;

import java.nio.charset.StandardCharsets;

/**
 * Regola concordata per le password: la stessa all'attivazione e al recupero, così una password accettata in un
 * caso non viene rifiutata nell'altro. Almeno 12 caratteri; al massimo 72 byte, il limite oltre il quale BCrypt
 * ignorerebbe il resto della password.
 */
final class PoliticaPassword {

    static final int LUNGHEZZA_MIN = 12;

    private PoliticaPassword() {
    }

    /**
     * Controlla la password scelta dall'utente.
     *
     * @param password valore inserito, può essere nullo
     * @throws PasswordNonValidaException se manca, è troppo corta o supera i 72 byte
     */
    static void verifica(String password) {
        if (password == null || password.isBlank()) {
            throw new PasswordNonValidaException("La password è obbligatoria.");
        }
        if (password.length() < LUNGHEZZA_MIN) {
            throw new PasswordNonValidaException("La password deve contenere almeno " + LUNGHEZZA_MIN + " caratteri.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > UtenzeService.LUNGHEZZA_MAX_PASSWORD_BYTE) {
            throw new PasswordNonValidaException("La password supera la lunghezza massima consentita di "
                    + UtenzeService.LUNGHEZZA_MAX_PASSWORD_BYTE + " byte.");
        }
    }
}
