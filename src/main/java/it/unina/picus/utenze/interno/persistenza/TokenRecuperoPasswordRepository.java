package it.unina.picus.utenze.interno.persistenza;

import java.time.Instant;
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

    /**
     * Cerca solo l'account del link, senza caricare il link nel contesto di persistenza. Serve a chi poi prende il
     * lock con {@link #findByTokenHashForUpdate}: se il link fosse già caricato, la lettura con lock restituirebbe la
     * copia in memoria con lo stato di prima e non quello aggiornato da un uso o da una sostituzione concorrente.
     */
    @Query("select t.accountId from TokenRecuperoPasswordJpa t where t.tokenHash = :hash")
    Optional<UUID> findAccountIdByTokenHash(@Param("hash") String hash);

    /** Legge il link con il lock di scrittura; l'account va bloccato prima. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenRecuperoPasswordJpa t where t.tokenHash = :hash")
    Optional<TokenRecuperoPasswordJpa> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("""
            update TokenRecuperoPasswordJpa t
               set t.usatoIl = :ora
             where t.accountId = :accountId
               and t.usatoIl is null
            """)
    int invalidaTokenAttiviPerAccount(@Param("accountId") UUID accountId, @Param("ora") Instant ora);

    /** Istante dell'ultimo link emesso per l'account; da leggere con il lock dell'account già preso. */
    @Query("select max(t.creatoIl) from TokenRecuperoPasswordJpa t where t.accountId = :accountId")
    Optional<Instant> ultimaEmissione(@Param("accountId") UUID accountId);

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
