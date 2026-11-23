package it.unina.picus.monitoraggio.interno.persistenza;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConteggioOptRepository extends JpaRepository<ConteggioOptJpa, UUID> {

    Optional<ConteggioOptJpa> findByOptIdAndAnnoAndTrimestreAndMisuraIdAndVoce(
            UUID optId, int anno, int trimestre, long misuraId, String voce);

    @Modifying
    @Query(value = """
        insert into monitoraggio_conteggi_opt (id, opt_id, anno, trimestre, misura_id, voce, valore, aggiornato_il)
        values (:id, :optId, :anno, :trimestre, :misuraId, :voce, :valore, :aggiornatoIl)
        on conflict (opt_id, anno, trimestre, misura_id, voce) do nothing
        """, nativeQuery = true)
    int inserisciSeAssente(
            @Param("id") UUID id,
            @Param("optId") UUID optId,
            @Param("anno") int anno,
            @Param("trimestre") int trimestre,
            @Param("misuraId") long misuraId,
            @Param("voce") String voce,
            @Param("valore") long valore,
            @Param("aggiornatoIl") Instant aggiornatoIl);

    @Query("""
        select c from ConteggioOptJpa c
        where (:optId is null or c.optId = :optId)
          and (c.anno > :annoInizio or (c.anno = :annoInizio and c.trimestre >= :trimInizio))
          and (c.anno < :annoFine or (c.anno = :annoFine and c.trimestre <= :trimFine))
        order by c.optId asc, c.misuraId asc, c.voce asc, c.anno asc, c.trimestre asc
        """)
    List<ConteggioOptJpa> cercaNelPeriodo(
            @Param("optId") UUID optId,
            @Param("annoInizio") int annoInizio,
            @Param("trimInizio") int trimInizio,
            @Param("annoFine") int annoFine,
            @Param("trimFine") int trimFine);
}