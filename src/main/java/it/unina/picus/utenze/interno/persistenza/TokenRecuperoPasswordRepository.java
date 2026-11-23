package it.unina.picus.utenze.interno.persistenza;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface TokenRecuperoPasswordRepository extends JpaRepository<TokenRecuperoPasswordJpa, UUID> {

    Optional<TokenRecuperoPasswordJpa> findByTokenHash(String tokenHash);

    /** Token ancora non usati dell'account, bloccati in ordine fisso per non creare deadlock tra richieste. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from TokenRecuperoPasswordJpa t
             where t.accountId = :accountId
               and t.usatoIl is null
             order by t.id
            """)
    List<TokenRecuperoPasswordJpa> findAttiviByAccountIdForUpdate(@Param("accountId") UUID accountId);

    @Modifying
    @Query("""
            update TokenRecuperoPasswordJpa t
               set t.usatoIl = :ora
             where t.accountId = :accountId
               and t.usatoIl is null
            """)
    int invalidaTokenAttiviPerAccount(@Param("accountId") UUID accountId, @Param("ora") Instant ora);
}
