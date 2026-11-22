package it.unina.picus.monitoraggio.interno.persistenza;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConteggioOptRepository extends JpaRepository<ConteggioOptJpa, UUID> {

    Optional<ConteggioOptJpa> findByOptIdAndAnnoAndTrimestreAndMisuraIdAndVoce(
            UUID optId, int anno, int trimestre, long misuraId, String voce);

    @Query("""
        select c from ConteggioOptJpa c
        where (:optId is null or c.optId = :optId)
          and (c.anno * 10 + c.trimestre) between :inizio and :fine
        order by c.optId asc, c.misuraId asc, c.voce asc, c.anno asc, c.trimestre asc
        """)
    List<ConteggioOptJpa> cercaNelPeriodo(
            @Param("optId") UUID optId,
            @Param("inizio") int inizio,
            @Param("fine") int fine);
}