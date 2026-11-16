package it.unina.picus.utenze.api;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

import java.util.Optional;
import java.util.UUID;

/** Verifica corrente dell'account identificato da Spring Security. */
public interface SessioniApi {
    /**
     * Rilegge identità e ruolo soltanto per un account ancora attivo.
     * @param accountId identificativo ottenuto dal principal verificato dal server
     * @return identità corrente, oppure vuoto per account assente o non attivo
     * @see UtenzeApi#verificaAccountSessione(UUID)
     */
    Optional<IdentitaAutenticata> verificaAccountSessione(UUID accountId);
}
