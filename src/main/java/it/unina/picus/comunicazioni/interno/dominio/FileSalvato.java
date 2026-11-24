package it.unina.picus.comunicazioni.interno.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * Allegato salvato, con il suo contenuto, pronto da scaricare.
 *
 * @param comunicazioneId comunicazione a cui appartiene
 * @param nomeFile nome del file
 * @param tipo tipo del file
 * @param contenuto contenuto del file
 */
public record FileSalvato(UUID comunicazioneId, String nomeFile, TipoAllegato tipo, byte[] contenuto) {

    public FileSalvato {
        contenuto = Objects.requireNonNull(contenuto, "contenuto").clone();
    }

    @Override
    public byte[] contenuto() {
        return contenuto.clone();
    }
}
