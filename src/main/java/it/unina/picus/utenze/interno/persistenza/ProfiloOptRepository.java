package it.unina.picus.utenze.interno.persistenza;

import java.util.UUID;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfiloOptRepository extends JpaRepository<ProfiloOptJpa, UUID> {
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
