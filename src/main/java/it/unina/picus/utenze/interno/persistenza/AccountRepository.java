package it.unina.picus.utenze.interno.persistenza;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<AccountJpa, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountJpa a where a.id = :id")
    Optional<AccountJpa> findByIdForUpdate(@Param("id") UUID id);

    Optional<AccountJpa> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<AccountJpa> findByRuoloAndStato(String ruolo, String stato);
    List<AccountJpa> findByRuolo(String ruolo);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AccountJpa a set a.passwordHash = :hash where a.id = :id")
    int aggiornaHash(@Param("id") UUID id, @Param("hash") String hash);
}