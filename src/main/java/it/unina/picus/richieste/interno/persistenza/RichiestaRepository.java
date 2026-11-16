package it.unina.picus.richieste.interno.persistenza;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.time.Instant;
import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;

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

    /** Conteggia in un'unica query gli appuntamenti confermati non ancora trascorsi. */
    @Query("""
            select r.erogatoreId as erogatoreId, count(r) as numero
            from RichiestaJpa r where r.erogatoreId is not null and r.stato = :stato
            and r.accordo.dataOra >= :istante group by r.erogatoreId
            """)
    List<CaricoAppuntamenti> carichi(@Param("stato") StatoRichiesta stato, @Param("istante") Instant istante);

    /** Proiezione aggregata senza caricare le pratiche degli altri candidati. */
    interface CaricoAppuntamenti {
        /** {@return account dell'erogatore} */
        UUID getErogatoreId();
        /** {@return numero di appuntamenti nel criterio di carico} */
        long getNumero();
    }
}
