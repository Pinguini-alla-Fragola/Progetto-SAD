package it.unina.picus.utenze.api;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

import java.util.Optional;
import java.util.UUID;

/** Verifica corrente dell'account identificato da Spring Security. */
public interface SessioniApi {
    /** @see UtenzeApi#verificaAccountSessione(UUID) */
    Optional<IdentitaAutenticata> verificaAccountSessione(UUID accountId);
}
