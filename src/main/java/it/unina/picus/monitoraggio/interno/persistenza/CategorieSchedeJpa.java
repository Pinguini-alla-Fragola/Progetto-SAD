package it.unina.picus.monitoraggio.interno.persistenza;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioVersioniCategorie;
import it.unina.picus.monitoraggio.interno.dominio.AssegnazioneTrimestre;
import it.unina.picus.monitoraggio.interno.dominio.CategorieSchedeRendicontabili;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestre;
import it.unina.picus.monitoraggio.interno.dominio.StatoVersioneCategorie;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoMonitoraggio;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestreGiaPubblicataException;
import it.unina.picus.monitoraggio.interno.dominio.VersioneCategorie;
import it.unina.picus.monitoraggio.interno.dominio.VersioneCategorieImmutabileException;
import it.unina.picus.monitoraggio.interno.dominio.VoceCategoriaVersione;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
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
    @Transactional
    public ConfigurazioneTrimestre associaLegacySeAssente(Trimestre trimestre, Instant istante) {
        ConfigurazioneTrimestre attuale = configurazionePer(TracciatoMonitoraggio.TM_OP_01, trimestre);
        if (!(attuale instanceof ConfigurazioneTrimestre.NonConfigurata)) {
            return attuale;
        }
        entita.createNativeQuery("""
                insert into monitoraggio_configurazioni_trimestre (tracciato, anno, trimestre, versione_id, assegnata_il)
                select :tracciato, :anno, :trimestre, :versioneId, :assegnataIl
                where not exists (
                    select 1 from monitoraggio_configurazioni_trimestre
                    where tracciato = :tracciato and anno = :anno and trimestre = :trimestre
                )
                """)
                .setParameter("tracciato", TracciatoMonitoraggio.TM_OP_01.codice())
                .setParameter("anno", trimestre.anno())
                .setParameter("trimestre", trimestre.numero())
                .setParameter("versioneId", VERSIONE_LEGACY_V01_ID)
                .setParameter("assegnataIl", Timestamp.from(istante))
                .executeUpdate();
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
            return new ConfigurazioneTrimestre.NonConfigurata(trimestre);
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
        VersioneCategorieJpa entity = entita.find(VersioneCategorieJpa.class, versioneId);
        if (entity == null) {
            throw new NoSuchElementException("Versione categorie non trovata");
        }
        if (entity.stato() != StatoVersioneCategorie.BOZZA) {
            throw new VersioneCategorieImmutabileException("La versione pubblicata non è modificabile");
        }
        entity.aggiornaDescrizione(descrizione);
        entity.svuotaVoci();
        entita.flush();
        entity.aggiungiVoci(voci);
        entita.flush();
        return entity.aDominio();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ConfigurazioneTrimestre.Pubblicata pubblicaPerTrimestre(
            UUID versioneId, Trimestre trimestre, Instant istante) {
        List<VersioneCategorieJpa> trovate = entita.createQuery("""
                select v from VersioneCategorieJpa v
                left join fetch v.voci
                where v.id = :id
                """, VersioneCategorieJpa.class)
                .setParameter("id", versioneId)
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
        ConfigurazioneTrimestre existing = configurazionePer(tracciato, trimestre);
        if (!(existing instanceof ConfigurazioneTrimestre.NonConfigurata)) {
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

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AssegnazioneTrimestre> elencaAssegnazioni(TracciatoMonitoraggio tracciato) {
        return entita.createQuery("""
                select c from ConfigurazioneTrimestreJpa c
                join fetch c.versione v
                where c.id.tracciato = :tracciato
                order by c.id.anno asc, c.id.trimestre asc
                """, ConfigurazioneTrimestreJpa.class)
                .setParameter("tracciato", tracciato.codice())
                .getResultList().stream()
                .map(c -> new AssegnazioneTrimestre(
                        TracciatoMonitoraggio.daCodice(c.id().tracciato()),
                        c.id().aTrimestre(),
                        c.versione().id(),
                        c.versione().aDominio().codiceVersione(),
                        c.versione().stato(),
                        c.assegnataIl()))
                .toList();
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
