package it.unina.picus.catalogo.interno.persistenza;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import java.util.Optional;
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

    /** Blocca e rilegge la misura della pratica nella transazione di assegnazione. */
    public Optional<Misura> bloccaMisura(long id) {
        return Optional.ofNullable(blocca(MisuraJpa.class, id)).map(MisuraJpa::aDto);
    }

    /** Protegge la dichiarazione corrente da ritiro o cancellazione concorrenti. */
    public boolean bloccaOfferta(UUID optId, long misuraId) {
        var offerta = blocca(OffertaOptJpa.class, new OffertaOptJpa.Chiave(optId, misuraId));
        return offerta != null && offerta.attiva();
    }

    /** Protegge il legame territoriale usato dalla verifica di compatibilità. */
    public boolean bloccaAdiacenza(String primo, String secondo) {
        return !entityManager.createQuery("""
                select a from AdiacenzaJpa a
                where (a.comuneA = :primo and a.comuneB = :secondo)
                   or (a.comuneA = :secondo and a.comuneB = :primo)
                """, AdiacenzaJpa.class).setParameter("primo", primo).setParameter("secondo", secondo)
                .setLockMode(LockModeType.PESSIMISTIC_READ).getResultList().isEmpty();
    }

    private <T> T blocca(Class<T> tipo, Object id) {
        var entita = entityManager.find(tipo, id, LockModeType.PESSIMISTIC_READ);
        if (entita != null) {
            entityManager.refresh(entita, LockModeType.PESSIMISTIC_READ);
        }
        return entita;
    }
}
