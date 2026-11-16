package it.unina.picus.richieste.api;

import java.util.NoSuchElementException;

/** Assenza applicativa di una pratica o di un contatto richiesto esplicitamente. */
public final class RisorsaRichiesteNonTrovataException extends NoSuchElementException {
    private static final long serialVersionUID = 1L;

    /** Crea un esito senza identificativi o dati della risorsa. */
    public RisorsaRichiesteNonTrovataException() {
        super("Risorsa non trovata");
    }
}
