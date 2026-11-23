package it.unina.picus.comunicazioni.interno.dominio;

import java.util.Objects;

/**
 * File allegato dal Banco, così come è arrivato, prima dei controlli.
 *
 * @param nomeFile nome del file, già ripulito dal percorso
 * @param contenuto contenuto del file
 */
public record AllegatoRicevuto(String nomeFile, byte[] contenuto) {

    public AllegatoRicevuto {
        contenuto = Objects.requireNonNull(contenuto, "contenuto").clone();
    }

    @Override
    public byte[] contenuto() {
        return contenuto.clone();
    }

    /** Dimensione in byte, senza copiare il contenuto. */
    public int dimensione() {
        return contenuto.length;
    }
}
