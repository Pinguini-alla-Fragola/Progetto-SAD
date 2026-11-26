package it.unina.picus.monitoraggio.interno.persistenza;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioVersioniCategorie;
import it.unina.picus.monitoraggio.interno.dominio.CategorieSchedeRendicontabili;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestre;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestreGiaPubblicataException;
import it.unina.picus.monitoraggio.interno.dominio.StatoVersioneCategorie;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoMonitoraggio;
import it.unina.picus.monitoraggio.interno.dominio.VersioneCategorie;
import it.unina.picus.monitoraggio.interno.dominio.VersioneCategorieImmutabileException;
import it.unina.picus.monitoraggio.interno.dominio.VoceCategoriaVersione;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Adattatore JPA per versioni delle categorie e associazioni immutabili per trimestre (#181, #41).
 */
@Repository
@Profile("!skeleton")
public class CategorieSchedeJpa implements CategorieSchedeRendicontabili, ArchivioVersioniCategorie {

    private final EntityManager entita;

    /**
     * Collega l'adattatore al contesto di persistenza condiviso.
     *
     * @param entita gestore JPA
     */
    public CategorieSchedeJpa(EntityManager entita) {
        this.entita = entita;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ConfigurazioneTrimestre configurazionePer(Trimestre trimestre) {
        return configurazionePer(TracciatoMonitoraggio.TM_OP_01, trimestre);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ConfigurazioneTrimestre configurazionePer(TracciatoMonitoraggio tracciato, Trimestre trimestre) {
        List<ConfigurazioneTrimestreJpa> righe = entita.createQuery("""
                select c from ConfigurazioneTrimestreJpa c
                join fetch c.versione v
                left join fetch v.voci
                where c.id.tracciato = :tracciato
                  and c.id.anno = :anno
                  and c.id.trimestre = :trimestre
                """, ConfigurazioneTrimestreJpa.class)
                .setParameter("tracciato", tracciato.codice())
                .setParameter("anno", trimestre.anno())
                .setParameter("trimestre", trimestre.numero())
                .getResultList();
        if (righe.isEmpty()) {
            boolean esistonoPubblicazioni = !entita.createQuery("""
                    select v.id from VersioneCategorieJpa v
                    where v.tracciato = :tracciato and v.stato = :pubblicata
                    """, UUID.class)
                    .setParameter("tracciato", tracciato.codice())
                    .setParameter("pubblicata", StatoVersioneCategorie.PUBBLICATA)
                    .setMaxResults(1)
                    .getResultList()
                    .isEmpty();
            return new ConfigurazioneTrimestre.NonConfigurata(trimestre, !esistonoPubblicazioni);
        }
        ConfigurazioneTrimestreJpa riga = righe.getFirst();
        VersioneCategorie versione = riga.versione().aDominio();
        if (versione.stato() == StatoVersioneCategorie.LEGACY) {
            return new ConfigurazioneTrimestre.LegacyProvvisoria(trimestre, versione, riga.assegnataIl());
        }
        return new ConfigurazioneTrimestre.Pubblicata(trimestre, versione, riga.assegnataIl());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public VersioneCategorie creaBozza(
            TracciatoMonitoraggio tracciato,
            String codiceVersione,
            String descrizione,
            List<VoceCategoriaVersione> voci,
            Instant creataIl) {
        VersioneCategorieJpa entity = new VersioneCategorieJpa(
                UUID.randomUUID(), tracciato, codiceVersione, StatoVersioneCategorie.BOZZA, descrizione, creataIl, null);
        entity.aggiungiVoci(voci);
        entita.persist(entity);
        try {
            entita.flush();
        } catch (PersistenceException ex) {
            if (isConstraintName(ex, "uq_mon_ver_cat_tracciato_codice")) {
                throw new IllegalStateException("Esiste già una versione con questo codice per il tracciato");
            }
            throw ex;
        }
        return entity.aDominio();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public VersioneCategorie aggiornaBozza(UUID versioneId, String descrizione, List<VoceCategoriaVersione> voci) {
        VersioneCategorieJpa inCache = entita.find(VersioneCategorieJpa.class, versioneId);
        if (inCache != null) {
            entita.detach(inCache);
        }
        int aggiornate = entita.createQuery("""
                update VersioneCategorieJpa v
                set v.descrizione = :descrizione,
                    v.versioneRecord = v.versioneRecord + 1
                where v.id = :id and v.stato = :bozza
                """)
                .setParameter("descrizione", descrizione)
                .setParameter("id", versioneId)
                .setParameter("bozza", StatoVersioneCategorie.BOZZA)
                .executeUpdate();
        if (aggiornate == 0) {
            Long presenti = entita.createQuery(
                            "select count(v) from VersioneCategorieJpa v where v.id = :id", Long.class)
                    .setParameter("id", versioneId)
                    .getSingleResult();
            if (presenti == 0) {
                throw new NoSuchElementException("Versione categorie non trovata");
            }
            throw new VersioneCategorieImmutabileException("La versione pubblicata non è modificabile");
        }
        try {
            VersioneCategorieJpa entity = entita.find(
                    VersioneCategorieJpa.class, versioneId, LockModeType.PESSIMISTIC_WRITE);
            entity.svuotaVoci();
            entita.flush();
            entity.aggiungiVoci(voci);
            entita.flush();
            return entity.aDominio();
        } catch (OptimisticLockException ex) {
            throw new VersioneCategorieImmutabileException("Modifica concorrente non consentita sulla versione");
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ConfigurazioneTrimestre.Pubblicata pubblicaPerTrimestre(
            UUID versioneId, Trimestre trimestre, Instant istante) {
        VersioneCategorieJpa inCache = entita.find(VersioneCategorieJpa.class, versioneId);
        if (inCache != null) {
            entita.detach(inCache);
        }
        List<VersioneCategorieJpa> trovate = entita.createQuery("""
                select v from VersioneCategorieJpa v
                left join fetch v.voci
                where v.id = :id
                """, VersioneCategorieJpa.class)
                .setParameter("id", versioneId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        if (trovate.isEmpty()) {
            throw new NoSuchElementException("Versione categorie non trovata");
        }
        VersioneCategorieJpa versione = trovate.getFirst();
        if (versione.stato() == StatoVersioneCategorie.LEGACY) {
            throw new VersioneCategorieImmutabileException("La versione provvisoria legacy non può essere ripubblicata");
        }
        if (versione.voci().isEmpty()) {
            throw new IllegalArgumentException("Inserisci almeno una categoria prima di pubblicare");
        }
        TracciatoMonitoraggio tracciato = versione.tracciatoEnum();
        boolean giaConfigurato = !entita.createQuery("""
                select c.versioneId from ConfigurazioneTrimestreJpa c
                where c.id.tracciato = :tracciato and c.id.anno = :anno and c.id.trimestre = :trimestre
                """, UUID.class)
                .setParameter("tracciato", tracciato.codice())
                .setParameter("anno", trimestre.anno())
                .setParameter("trimestre", trimestre.numero())
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
        if (giaConfigurato) {
            throw new ConfigurazioneTrimestreGiaPubblicataException("Il trimestre ha già una configurazione pubblicata");
        }
        if (versione.stato() == StatoVersioneCategorie.BOZZA) {
            versione.segnaPubblicata(istante);
        }
        ConfigurazioneTrimestreJpa assegnazione = new ConfigurazioneTrimestreJpa(tracciato, trimestre, versione, istante);
        entita.persist(assegnazione);
        try {
            entita.flush();
        } catch (PersistenceException ex) {
            if (isTrimestrePkViolation(ex)) {
                throw new ConfigurazioneTrimestreGiaPubblicataException("Il trimestre ha già una configurazione pubblicata");
            }
            throw ex;
        }
        return new ConfigurazioneTrimestre.Pubblicata(trimestre, versione.aDominio(), istante);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<VersioneCategorie> elencaVersioni(TracciatoMonitoraggio tracciato) {
        return entita.createQuery("""
                select distinct v from VersioneCategorieJpa v
                left join fetch v.voci
                where v.tracciato = :tracciato
                order by v.creataIl asc, v.codiceVersione asc
                """, VersioneCategorieJpa.class)
                .setParameter("tracciato", tracciato.codice())
                .getResultList().stream()
                .map(VersioneCategorieJpa::aDominio)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<VersioneCategorie> trovaVersione(UUID versioneId) {
        List<VersioneCategorieJpa> trovate = entita.createQuery("""
                select v from VersioneCategorieJpa v
                left join fetch v.voci
                where v.id = :id
                """, VersioneCategorieJpa.class)
                .setParameter("id", versioneId)
                .getResultList();
        return trovate.isEmpty() ? Optional.empty() : Optional.of(trovate.getFirst().aDominio());
    }

    private static boolean isConstraintName(Throwable errore, String attesa) {
        String target = attesa.toLowerCase(Locale.ROOT);
        for (Throwable cur = errore; cur != null; cur = cur.getCause()) {
            if (cur instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
                String nome = cve.getConstraintName().toLowerCase(Locale.ROOT);
                if (nome.contains(target)) {
                    return true;
                }
            }
            if (cur.getMessage() != null && cur.getMessage().toLowerCase(Locale.ROOT).contains(target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTrimestrePkViolation(Throwable errore) {
        if (isConstraintName(errore, "monitoraggio_configurazioni_trimestre_pkey")
                || isConstraintName(errore, "pk_monitoraggio_configurazioni_trimestre")
                || isConstraintName(errore, "primary_key_")) {
            return true;
        }
        for (Throwable cur = errore; cur != null; cur = cur.getCause()) {
            if (cur instanceof java.sql.SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
