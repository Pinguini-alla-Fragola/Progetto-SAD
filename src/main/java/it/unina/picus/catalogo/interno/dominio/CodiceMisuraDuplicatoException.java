package it.unina.picus.catalogo.interno.dominio;

import java.io.Serial;
import java.util.Map;

/**
 * Segnala che il codice indicato è già assegnato a un'altra misura del catalogo.
 *
 * <p>Estende {@link DatiMisuraNonValidiException} (e quindi {@link IllegalArgumentException})
 * in aderenza a {@link it.unina.picus.catalogo.api.CatalogoApi}, consentendo al livello web
 * di tradurre il conflitto di unicità nello stato HTTP {@code 409 Conflict}.</p>
 */
public final class CodiceMisuraDuplicatoException extends DatiMisuraNonValidiException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Crea l'eccezione con messaggio controllato associato al campo {@code codice}.
     */
    public CodiceMisuraDuplicatoException() {
        super("Esiste già una misura con questo codice",
                Map.of("codice", "Esiste già una misura con questo codice"));
    }
}
