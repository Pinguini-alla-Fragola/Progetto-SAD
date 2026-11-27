package it.unina.picus.monitoraggio.interno.persistenza;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.dominio.CategorieSchedeRendicontabili;
import it.unina.picus.monitoraggio.interno.dominio.ConfigurazioneTrimestre;
import it.unina.picus.monitoraggio.interno.dominio.StatoVersioneCategorie;
import it.unina.picus.monitoraggio.interno.dominio.TracciatoMonitoraggio;
import it.unina.picus.monitoraggio.interno.dominio.VersioneCategorie;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Adattatore JPA di sola lettura per le versioni delle categorie e le associazioni per trimestre (#181, #41).
 */
@Repository
@Profile("!skeleton")
public class CategorieSchedeJpa implements CategorieSchedeRendicontabili {

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
        String tracciato = TracciatoMonitoraggio.TM_OP_01.codice();
        List<ConfigurazioneTrimestreJpa> righe = entita.createQuery("""
                select c from ConfigurazioneTrimestreJpa c
                join fetch c.versione v
                left join fetch v.voci
                where c.id.tracciato = :tracciato
                  and c.id.anno = :anno
                  and c.id.trimestre = :trimestre
                """, ConfigurazioneTrimestreJpa.class)
                .setParameter("tracciato", tracciato)
                .setParameter("anno", trimestre.anno())
                .setParameter("trimestre", trimestre.numero())
                .getResultList();
        if (righe.isEmpty()) {
            boolean esistonoPubblicazioni = !entita.createQuery("""
                    select v.id from VersioneCategorieJpa v
                    where v.tracciato = :tracciato and v.stato = :pubblicata
                    """, UUID.class)
                    .setParameter("tracciato", tracciato)
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
}
