package it.unina.picus.catalogo.interno.persistenza;

import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.api.CatalogoApi.OffertaOpt;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adattatore di persistenza dell'offerta OPT e delle adiacenze del catalogo. */
@Repository
@Transactional(readOnly = true)
public class OffertaTerritorialeJpa {
    private final EntityManager entityManager;

    /**
     * Collega l'adattatore alla persistenza del catalogo.
     *
     * @param entityManager gestore delle entità del catalogo
     */
    public OffertaTerritorialeJpa(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Legge le dichiarazioni correnti dell'OPT, comprese quelle per misure disattivate.
     *
     * @param optId account organizzativo titolare
     * @return dichiarazioni correnti ordinate per identificativo della misura
     */
    public List<OffertaOpt> elencoOffertaOpt(UUID optId) {
        return entityManager.createQuery("""
                select o.id.misuraId as misuraId, o.tramiteProfessionista as tramiteProfessionista
                from OffertaOptJpa o where o.id.optId = :optId and o.attiva = true
                order by o.id.misuraId
                """, Tuple.class).setParameter("optId", optId).getResultList().stream()
                .map(riga -> new OffertaOpt(riga.get("misuraId", Long.class),
                        riga.get("tramiteProfessionista", Boolean.class)))
                .toList();
    }

    /**
     * Registra o riattiva una dichiarazione in un'unica istruzione. La chiave primaria
     * serializza dichiarazioni concorrenti per la stessa coppia OPT e misura.
     *
     * @param optId account organizzativo titolare
     * @param misuraId misura da offrire, già verificata e bloccata dal chiamante
     * @param tramiteProfessionista indica il ricorso a un professionista esterno
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registraOffertaOpt(UUID optId, long misuraId, boolean tramiteProfessionista) {
        entityManager.createNativeQuery("""
                insert into catalogo_offerte_opt (opt_id, misura_id, tramite_professionista, attiva)
                values (:optId, :misuraId, :tramiteProfessionista, true)
                on conflict (opt_id, misura_id)
                do update set tramite_professionista = excluded.tramite_professionista, attiva = true
                """).setParameter("optId", optId).setParameter("misuraId", misuraId)
                .setParameter("tramiteProfessionista", tramiteProfessionista).executeUpdate();
    }

    /**
     * Ritira una dichiarazione corrente conservando la riga e i suoi riferimenti.
     *
     * @param optId account organizzativo titolare
     * @param misuraId misura da ritirare
     * @return vero se è stata ritirata una dichiarazione corrente
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean ritiraOffertaOpt(UUID optId, long misuraId) {
        return entityManager.createQuery("""
                update OffertaOptJpa o set o.attiva = false
                where o.id.optId = :optId and o.id.misuraId = :misuraId and o.attiva = true
                """).setParameter("optId", optId).setParameter("misuraId", misuraId).executeUpdate() > 0;
    }

    /**
     * Cerca le OPT che hanno una dichiarazione corrente per la misura.
     *
     * @param misuraId identificativo della misura
     * @return identificativi delle OPT con offerta corrente
     */
    public List<UUID> optAttivePerMisura(long misuraId) {
        return entityManager.createQuery("""
                select o.id.optId from OffertaOptJpa o where o.id.misuraId = :misura and o.attiva = true
                """, UUID.class).setParameter("misura", misuraId).getResultList();
    }

    /**
     * Cerca i comuni confinanti con quello indicato.
     *
     * @param comune codice del comune di riferimento
     * @return codici dei comuni limitrofi
     */
    public List<String> comuniLimitrofi(String comune) {
        return entityManager.createQuery("""
                select case when a.comuneA = :comune then a.comuneB else a.comuneA end
                from AdiacenzaJpa a where a.comuneA = :comune or a.comuneB = :comune
                """, String.class).setParameter("comune", comune).getResultList();
    }

    /**
     * Blocca e rilegge la misura nella transazione del caso d'uso chiamante.
     *
     * @param id identificativo della misura
     * @return misura aggiornata sotto lock, se presente
     */
    public Optional<Misura> bloccaMisura(long id) {
        return Optional.ofNullable(blocca(MisuraJpa.class, id)).map(MisuraJpa::aDto);
    }

    /**
     * Protegge la dichiarazione corrente da ritiro o cancellazione concorrenti.
     *
     * @param optId account organizzativo titolare
     * @param misuraId identificativo della misura
     * @return vero se la dichiarazione corrente esiste ed è bloccata
     */
    public boolean bloccaOfferta(UUID optId, long misuraId) {
        var offerta = blocca(OffertaOptJpa.class, new OffertaOptJpa.Chiave(optId, misuraId));
        return offerta != null && offerta.attiva();
    }

    /**
     * Protegge il legame territoriale usato dalla verifica di compatibilità.
     *
     * @param primo codice del primo comune
     * @param secondo codice del secondo comune
     * @return vero se il legame esiste ed è bloccato
     */
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
