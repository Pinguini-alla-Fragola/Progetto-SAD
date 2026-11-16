package it.unina.picus.utenze.interno.persistenza;

import it.unina.picus.utenze.api.SessioniApi;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Rilegge lo stato dell'account per ciascun accesso alle richieste protette. */
@Repository
@Transactional(readOnly = true)
public class SessioniJpa implements SessioniApi {
    private final EntityManager entityManager;

    public SessioniJpa(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<IdentitaAutenticata> verificaAccountSessione(UUID accountId) {
        return accountId == null ? Optional.empty()
                : Optional.ofNullable(entityManager.find(AccountJpa.class, accountId))
                        .flatMap(AccountJpa::identitaAttiva);
    }
}
