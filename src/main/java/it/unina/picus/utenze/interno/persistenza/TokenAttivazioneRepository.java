package it.unina.picus.utenze.interno.persistenza;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenAttivazioneRepository extends JpaRepository<TokenAttivazioneJpa, UUID> {

    Optional<TokenAttivazioneJpa> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update TokenAttivazioneJpa t
               set t.usatoIl = :ora
             where t.accountId = :accountId
               and t.usatoIl is null
            """)
    int invalidaTokenAttiviPerAccount(@Param("accountId") UUID accountId, @Param("ora") Instant ora);
}