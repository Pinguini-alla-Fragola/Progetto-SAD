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

    /**
     * Cerca solo l'account del token, senza caricare il token nel contesto di persistenza. Serve a chi poi prende il
     * lock con {@link #findByTokenHashForUpdate}: se il token fosse già caricato, la lettura con lock restituirebbe la
     * copia in memoria con lo stato di prima e non quello aggiornato da un reinvio concorrente.
     */
    @Query("select t.accountId from TokenAttivazioneJpa t where t.tokenHash = :hash")
    Optional<UUID> findAccountIdByTokenHash(@Param("hash") String hash);

    /** Numero di emissione dell'ultimo token dell'account; da leggere con il lock dell'account già preso. */
    @Query("select coalesce(max(t.ordineEmissione), 0) from TokenAttivazioneJpa t where t.accountId = :accountId")
    long ultimoOrdineEmissione(@Param("accountId") UUID accountId);

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

    /**
     * Chiude i token ancora non usati dell'account che il token consegnato sostituisce: quelli emessi prima di lui e
     * quelli dell'importazione CSV, che potrebbero essere stati preparati nel frattempo.
     */
    @Modifying
    @Query("""
            update TokenAttivazioneJpa t
               set t.usatoIl = :ora
             where t.accountId = :accountId
               and t.usatoIl is null
               and t.id <> :consegnato
               and (t.ordineEmissione < :ordine or t.daImportazioneOpt = true)
            """)
    int invalidaPrecedenti(@Param("accountId") UUID accountId, @Param("consegnato") UUID consegnato,
            @Param("ordine") long ordine, @Param("ora") Instant ora);
}