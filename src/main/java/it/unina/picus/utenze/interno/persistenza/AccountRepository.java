package it.unina.picus.utenze.interno.persistenza;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<AccountJpa, UUID> {

    Optional<AccountJpa> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<AccountJpa> findByRuoloAndStato(String ruolo, String stato);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AccountJpa a set a.passwordHash = :hash where a.id = :id")
    int aggiornaHash(@Param("id") UUID id, @Param("hash") String hash);
}