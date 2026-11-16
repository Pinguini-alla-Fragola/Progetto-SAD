package it.unina.picus.richieste.interno.persistenza;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface RichiestaRepository extends JpaRepository<RichiestaJpa, UUID> {

    /** Blocca la pratica prima di aggiornare un contatto o lo stato della richiesta. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RichiestaJpa r where r.id = :id")
    Optional<RichiestaJpa> cercaPerAggiornamento(@Param("id") UUID id);
}
