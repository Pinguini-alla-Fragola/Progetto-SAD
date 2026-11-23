package it.unina.picus.utenze.interno.applicazione;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token monouso inviati per email, di attivazione e di recupero password: generazione casuale e impronta
 * da salvare. Il token in chiaro esiste solo nell'email; nel database resta l'impronta SHA-256.
 */
final class TokenMonouso {

    private static final int BYTE_TOKEN = 32; // 256 bit di entropia
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenMonouso() {
    }

    /** @return un token casuale adatto a un indirizzo web, senza riempimento */
    static String genera() {
        byte[] bytes = new byte[BYTE_TOKEN];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** @return l'impronta SHA-256, in esadecimale, del token in chiaro */
    static String impronta(String tokenInChiaro) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(tokenInChiaro.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 non disponibile", e);
        }
    }
}
