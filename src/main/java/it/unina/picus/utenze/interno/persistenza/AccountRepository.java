package it.unina.picus.utenze.interno.persistenza;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Cerca solo l'identificativo, senza caricare l'account nel contesto di persistenza. Serve a chi poi prende il
     * lock con {@link #findByIdForUpdate}: se l'account fosse già caricato, la lettura con lock restituirebbe
     * l'istanza in memoria con lo stato di prima e non quello aggiornato da una revoca o attivazione concorrente.
     */
    @Query("select a.id from AccountJpa a where lower(a.email) = lower(:email)")
    Optional<UUID> findIdByEmailIgnoreCase(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    List<AccountJpa> findByRuoloAndStato(String ruolo, String stato);
    List<AccountJpa> findByRuolo(String ruolo);

    /**
     * Proietta i soli identificativi e nomi degli account richiesti, in qualsiasi stato.
     *
     * @param ids identificativi da cercare
     * @return una proiezione per account trovato
     */
    List<NomeAccount> findNomiByIdIn(Set<UUID> ids);

    /** Proiezione usata per risolvere i nomi negli storici degli altri moduli. */
    interface NomeAccount {

        /** @return identificativo dell'account */
        UUID getId();

        /** @return nome visualizzato dell'account */
        String getNome();
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AccountJpa a set a.passwordHash = :hash where a.id = :id")
    int aggiornaHash(@Param("id") UUID id, @Param("hash") String hash);

    /**
     * Sostituisce l'hash solo se è ancora quello che chi aggiorna ha letto e verificato. Serve all'aggiornamento
     * del costo dell'hash al login: se nel frattempo la password è stata cambiata, l'hash nuovo non va sovrascritto
     * con quello della password vecchia.
     *
     * @return 1 se l'hash è stato sostituito, 0 se nel frattempo è cambiato
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AccountJpa a set a.passwordHash = :nuovo where a.id = :id and a.passwordHash = :atteso")
    int aggiornaHashSeInvariato(@Param("id") UUID id, @Param("atteso") String atteso, @Param("nuovo") String nuovo);
}
