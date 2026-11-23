package it.unina.picus.utenze.interno.persistenza;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenAttivazioneRepository extends JpaRepository<TokenAttivazioneJpa, UUID> {

    Optional<TokenAttivazioneJpa> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenAttivazioneJpa t where t.tokenHash = :hash")
    Optional<TokenAttivazioneJpa> findByTokenHashForUpdate(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from TokenAttivazioneJpa t
             where t.accountId = :accountId
               and t.usatoIl is null
             order by t.id
            """)
    List<TokenAttivazioneJpa> findAttiviByAccountIdForUpdate(@Param("accountId") UUID accountId);

    @Modifying
    @Query("""
            update TokenAttivazioneJpa t
               set t.usatoIl = :ora
             where t.accountId = :accountId
               and t.usatoIl is null
            """)
    int invalidaTokenAttiviPerAccount(@Param("accountId") UUID accountId, @Param("ora") Instant ora);

    /** Chiude un solo token, se non è già stato chiuso da altri. */
    @Modifying
    @Query("""
            update TokenAttivazioneJpa t
               set t.usatoIl = :ora
             where t.id = :id
               and t.usatoIl is null
            """)
    int chiudiSeAttivo(@Param("id") UUID id, @Param("ora") Instant ora);

    /** Chiude i token ancora non usati dell'account creati prima di quello indicato. */
    @Modifying
    @Query("""
            update TokenAttivazioneJpa t
               set t.usatoIl = :ora
             where t.accountId = :accountId
               and t.usatoIl is null
               and t.creatoIl < :prima
            """)
    int invalidaPrecedenti(@Param("accountId") UUID accountId, @Param("prima") Instant prima,
            @Param("ora") Instant ora);
}