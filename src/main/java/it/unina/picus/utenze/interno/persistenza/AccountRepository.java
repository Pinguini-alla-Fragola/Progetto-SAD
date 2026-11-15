package it.unina.picus.utenze.interno.persistenza;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<AccountJpa, UUID> {

    Optional<AccountJpa> findByEmailIgnoreCase(String email);

    List<AccountJpa> findByRuoloAndStato(String ruolo, String stato);

    /** Azzera contatore e blocco quando il blocco è scaduto. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AccountJpa a set a.tentativiFalliti = 0, a.bloccatoFino = null "
            + "where a.id = :id and a.bloccatoFino is not null and a.bloccatoFino <= :ora")
    int sbloccaSeScaduto(@Param("id") UUID id, @Param("ora") Instant ora);

    /**
     * Incrementa il contatore in modo atomico (sicuro con tentativi concorrenti) e imposta il blocco
     * al raggiungimento della soglia. Non agisce su un account già bloccato.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AccountJpa a set a.tentativiFalliti = a.tentativiFalliti + 1, "
            + "a.bloccatoFino = case when a.tentativiFalliti + 1 >= :soglia then :fino else a.bloccatoFino end "
            + "where a.id = :id and a.bloccatoFino is null")
    int registraFallimento(@Param("id") UUID id, @Param("soglia") int soglia, @Param("fino") Instant fino);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AccountJpa a set a.tentativiFalliti = 0, a.bloccatoFino = null "
            + "where a.id = :id and (a.tentativiFalliti <> 0 or a.bloccatoFino is not null)")
    int azzeraTentativi(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AccountJpa a set a.passwordHash = :hash where a.id = :id")
    int aggiornaHash(@Param("id") UUID id, @Param("hash") String hash);
}
