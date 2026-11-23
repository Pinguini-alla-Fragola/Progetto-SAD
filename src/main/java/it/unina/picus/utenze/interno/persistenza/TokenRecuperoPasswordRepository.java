package it.unina.picus.utenze.interno.persistenza;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenRecuperoPasswordRepository extends JpaRepository<TokenRecuperoPasswordJpa, UUID> {

    Optional<TokenRecuperoPasswordJpa> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update TokenRecuperoPasswordJpa t
               set t.usatoIl = :ora
             where t.accountId = :accountId
               and t.usatoIl is null
            """)
    int invalidaTokenAttiviPerAccount(@Param("accountId") UUID accountId, @Param("ora") Instant ora);

    /** Chiude un solo link, se non è già stato chiuso da altri. */
    @Modifying
    @Query("""
            update TokenRecuperoPasswordJpa t
               set t.usatoIl = :ora
             where t.id = :id
               and t.usatoIl is null
            """)
    int chiudiSeAttivo(@Param("id") UUID id, @Param("ora") Instant ora);

    /** Chiude i link ancora non usati dell'account creati prima di quello indicato. */
    @Modifying
    @Query("""
            update TokenRecuperoPasswordJpa t
               set t.usatoIl = :ora
             where t.accountId = :accountId
               and t.usatoIl is null
               and t.creatoIl < :prima
            """)
    int invalidaPrecedenti(@Param("accountId") UUID accountId, @Param("prima") Instant prima,
            @Param("ora") Instant ora);
}
