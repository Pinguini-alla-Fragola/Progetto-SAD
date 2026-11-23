package it.unina.picus.comunicazioni.interno.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * Allegato già controllato, pronto da salvare insieme alla comunicazione.
 *
 * @param id identificativo dell'allegato
 * @param nomeFile nome del file
 * @param tipo tipo riconosciuto dal contenuto
 * @param contenuto contenuto del file
 */
public record NuovoAllegato(UUID id, String nomeFile, TipoAllegato tipo, byte[] contenuto) {

    public NuovoAllegato {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nomeFile, "nomeFile");
        Objects.requireNonNull(tipo, "tipo");
        contenuto = Objects.requireNonNull(contenuto, "contenuto").clone();
    }

    @Override
    public byte[] contenuto() {
        return contenuto.clone();
    }
}
