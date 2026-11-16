package it.unina.picus.catalogo.interno.persistenza;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/** Lettura dell'offerta e delle adiacenze di proprietà del catalogo. */
@Repository
@Transactional(readOnly = true)
public class OffertaTerritorialeJpa {
    private final EntityManager entityManager;

    public OffertaTerritorialeJpa(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<UUID> optAttivePerMisura(long misuraId) {
        return entityManager.createQuery("""
                select o.id.optId from OffertaOptJpa o where o.id.misuraId = :misura and o.attiva = true
                """, UUID.class).setParameter("misura", misuraId).getResultList();
    }

    public List<String> comuniLimitrofi(String comune) {
        return entityManager.createQuery("""
                select case when a.comuneA = :comune then a.comuneB else a.comuneA end
                from AdiacenzaJpa a where a.comuneA = :comune or a.comuneB = :comune
                """, String.class).setParameter("comune", comune).getResultList();
    }
}
