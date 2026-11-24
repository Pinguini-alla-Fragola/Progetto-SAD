package it.unina.picus.utenze.interno.persistenza;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfiloOptRepository extends JpaRepository<ProfiloOptJpa, UUID> {

    boolean existsByCodiceStrutturaIgnoreCase(String codiceStruttura);

    /**
     * Identifica l'utenza OPT tramite il codice struttura, indipendentemente dallo stato.
     *
     * @param codiceStruttura codice struttura normalizzato in maiuscolo
     * @return identificativo dell'utenza, se registrata
     */
    @Query("select p.accountId from ProfiloOptJpa p where upper(p.codiceStruttura) = :codiceStruttura")
    Optional<UUID> cercaAccountPerCodiceStruttura(@Param("codiceStruttura") String codiceStruttura);

    @Query("""
            select p.accountId as accountId, a.nome as nome, p.comuneCodice as comuneCodice
            from ProfiloOptJpa p, AccountJpa a
            where p.accountId = a.id and a.id in :ids and a.stato = 'ATTIVO' and a.ruolo = 'OPT'
            """)
    List<ProfiloAttivo> attive(@Param("ids") Set<UUID> ids);

    interface ProfiloAttivo {
        UUID getAccountId();
        String getNome();
        String getComuneCodice();
    }
}
