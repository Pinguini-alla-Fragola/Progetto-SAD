package it.unina.picus.richieste.interno.persistenza;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * Accede alle richieste di misura tramite il pattern <em>Repository</em>, implementato con Spring Data JPA.
 */
public interface RichiestaRepository extends JpaRepository<RichiestaJpa, UUID> {

    /**
     * Carica la richiesta acquisendo un lock pessimistico di scrittura sulla sua riga.
     *
     * <p>Va invocato dentro una transazione di scrittura. Il lock resta attivo fino alla
     * conclusione della transazione, durante la lettura e l'aggiornamento del contatto.
     *
     * @param id identificativo della richiesta
     * @return richiesta bloccata, oppure un valore vuoto se non esiste
     * @see LockModeType#PESSIMISTIC_WRITE
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RichiestaJpa r where r.id = :id")
    Optional<RichiestaJpa> cercaPerAggiornamento(@Param("id") UUID id);
}
