package it.unina.picus.monitoraggio.interno.persistenza;

import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaOperatore;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.SchedaSintesi;
import it.unina.picus.monitoraggio.api.MonitoraggioApi.Trimestre;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeOperatore;
import it.unina.picus.monitoraggio.interno.dominio.ArchivioSchedeRendicontabili;
import it.unina.picus.monitoraggio.interno.dominio.SchedaGiaInviataException;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Adattatore JPA delle schede personali. Il vincolo sul periodo arbitra gli invii concorrenti. */
@Repository
@Profile("!skeleton")
public class ArchivioSchedeJpa implements ArchivioSchedeOperatore, ArchivioSchedeRendicontabili {

    private static final String VINCOLO_PRIMO_INVIO = "uq_scheda_operatore_periodo";
    private static final String PROIEZIONE_SINTESI = """
            select s.id, s.operatoreId, s.anno, s.trimestre, s.inviataIl from SchedaOperatoreJpa s
            """;
    private static final String PER_PERIODO = " where s.anno = :anno and s.trimestre = :trimestre";
    private static final String ORDINE_INVIO = " order by s.inviataIl, s.id";

    private final EntityManager entita;

    /**
     * Collega l'archivio al contesto di persistenza della transazione corrente.
     *
     * @param entita gestore JPA condiviso
     */
    public ArchivioSchedeJpa(EntityManager entita) {
        this.entita = entita;
    }

    /** {@inheritDoc} */
    @Override
    public void registra(SchedaOperatore scheda) {
        entita.persist(new SchedaOperatoreJpa(scheda));
        try {
            entita.flush();
        } catch (ConstraintViolationException ex) {
            if (vincoloPrimoInvio(ex.getConstraintName())) {
                throw new SchedaGiaInviataException();
            }
            throw ex;
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<SchedaOperatore> cerca(UUID id, Consumer<UUID> verificaTitolare) {
        return Optional.ofNullable(entita.find(SchedaOperatoreJpa.class, id)).map(scheda -> {
            verificaTitolare.accept(scheda.operatoreId());
            return scheda.aScheda();
        });
    }

    /** {@inheritDoc} */
    @Override
    public List<SchedaSintesi> perOperatore(UUID operatoreId) {
        return entita.createQuery(PROIEZIONE_SINTESI + " where s.operatoreId = :id" + ORDINE_INVIO, RigaSintesi.class)
                .setParameter("id", operatoreId)
                .getResultList().stream().map(RigaSintesi::aSintesi).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<SchedaSintesi> perTrimestre(Trimestre trimestre) {
        return entita.createQuery(PROIEZIONE_SINTESI + PER_PERIODO + ORDINE_INVIO, RigaSintesi.class)
                .setParameter("anno", trimestre.anno())
                .setParameter("trimestre", trimestre.numero())
                .getResultList().stream().map(RigaSintesi::aSintesi).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<VersioneInviata> elencaVersioniInviate(Trimestre trimestre) {
        return entita.createQuery("select s from SchedaOperatoreJpa s left join fetch s.conteggi"
                        + PER_PERIODO + ORDINE_INVIO, SchedaOperatoreJpa.class)
                .setParameter("anno", trimestre.anno())
                .setParameter("trimestre", trimestre.numero())
                .getResultList().stream().map(SchedaOperatoreJpa::aVersioneInviata).toList();
    }

    /** PostgreSQL restituisce il vincolo, H2 può indicare l'indice generato per quel vincolo. */
    private static boolean vincoloPrimoInvio(String nome) {
        if (nome == null) {
            return false;
        }
        String normalizzato = nome.toLowerCase(Locale.ROOT);
        return normalizzato.equals(VINCOLO_PRIMO_INVIO)
                || normalizzato.startsWith("public." + VINCOLO_PRIMO_INVIO + "_index_");
    }

    /** Proiezione delle sole colonne di sintesi, senza anagrafica e conteggi. */
    public record RigaSintesi(UUID id, UUID operatoreId, int anno, int trimestre, Instant inviataIl) {
        SchedaSintesi aSintesi() {
            return new SchedaSintesi(id, operatoreId, new Trimestre(anno, trimestre), inviataIl);
        }
    }
}
