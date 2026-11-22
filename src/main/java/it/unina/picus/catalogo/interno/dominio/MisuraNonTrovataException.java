package it.unina.picus.catalogo.interno.dominio;

import java.io.Serial;
import java.util.NoSuchElementException;

/**
 * Segnala l'assenza di una misura richiesta esplicitamente per identificativo.
 *
 * <p>Estende {@link NoSuchElementException} secondo il contratto di
 * {@link it.unina.picus.catalogo.api.CatalogoApi} senza esporre identificativi nel messaggio.</p>
 */
public final class MisuraNonTrovataException extends NoSuchElementException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Crea l'eccezione con un messaggio generico privo di dettagli interni.
     */
    public MisuraNonTrovataException() {
        super("Misura non trovata");
    }
}
