package it.unina.picus.catalogo.interno.persistenza;

import it.unina.picus.catalogo.interno.dominio.CodiceMisuraDuplicatoException;
import it.unina.picus.catalogo.interno.dominio.MisuraDiCatalogo;
import it.unina.picus.catalogo.interno.dominio.MisuraRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Adattatore JPA/PostgreSQL della porta {@link MisuraRepository}.
 *
 * <p>Applica il pattern GoF <em>Adapter</em> per tradurre le operazioni del dominio su
 * {@link MisuraJpa} mantenendo lock pessimistici in aggiornamento e traducendo le violazioni
 * di unicità del database in {@link CodiceMisuraDuplicatoException}.</p>
 */
@Repository
public class MisuraRepositoryJpa implements MisuraRepository {

    private static final String SQLSTATE_UNICO_VIOLATO = "23505";

    private final EntityManager entityManager;

    /**
     * Costruisce il repository con il gestore della persistenza JPA.
     *
     * @param entityManager contesto di persistenza iniettato da Spring
     */
    public MisuraRepositoryJpa(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<MisuraDiCatalogo> trovaTutte() {
        return entityManager.createQuery(
                        "select m from MisuraJpa m order by m.codice, m.id", MisuraJpa.class)
                .getResultList()
                .stream()
                .map(MisuraJpa::aDominio)
                .toList();
    }

    @Override
    public Optional<MisuraDiCatalogo> cercaPerAggiornamento(long misuraId) {
        if (misuraId <= 0) {
            return Optional.empty();
        }
        MisuraJpa entita = entityManager.find(MisuraJpa.class, misuraId, LockModeType.PESSIMISTIC_WRITE);
        if (entita == null) {
            return Optional.empty();
        }
        entityManager.refresh(entita, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(entita.aDominio());
    }

    @Override
    public boolean esisteCodice(String codice) {
        return esisteCodiceEscludendo(codice, null);
    }

    @Override
    public boolean esisteCodiceSuAltraMisura(String codice, long misuraIdEsclusa) {
        return esisteCodiceEscludendo(codice, misuraIdEsclusa);
    }

    @Override
    public MisuraDiCatalogo salvaNuova(MisuraDiCatalogo misura) {
        Objects.requireNonNull(misura, "misura");
        if (misura.id() != null) {
            throw new IllegalArgumentException("Una nuova misura non deve avere un identificativo preesistente");
        }
        MisuraJpa entita = MisuraJpa.nuova(misura);
        entityManager.persist(entita);
        return flushEDetach(entita);
    }

    @Override
    public MisuraDiCatalogo aggiorna(MisuraDiCatalogo misura) {
        Objects.requireNonNull(misura, "misura");
        if (misura.id() == null) {
            throw new IllegalArgumentException("L'aggiornamento richiede una misura già persistita");
        }
        MisuraJpa entita = entityManager.find(MisuraJpa.class, misura.id());
        if (entita == null || !LockModeType.PESSIMISTIC_WRITE.equals(entityManager.getLockMode(entita))) {
            throw new IllegalStateException("Aggiornamento senza lock della misura");
        }
        entita.aggiorna(misura);
        return flushEDetach(entita);
    }

    private MisuraDiCatalogo flushEDetach(MisuraJpa entita) {
        eseguiFlushConControlloUnicita();
        MisuraDiCatalogo salvata = entita.aDominio();
        // Rimuove l'istanza gestita dalla cache L1 così un successivo caricamento in lock
        // nella stessa transazione acquisisce sempre esplicitamente PESSIMISTIC_WRITE.
        entityManager.detach(entita);
        return salvata;
    }

    private boolean esisteCodiceEscludendo(String codice, Long misuraIdEsclusa) {
        if (codice == null) {
            return false;
        }
        String jpql = misuraIdEsclusa == null
                ? "select m.id from MisuraJpa m where lower(m.codice) = lower(:codice)"
                : "select m.id from MisuraJpa m where lower(m.codice) = lower(:codice) and m.id <> :esclusa";
        var query = entityManager.createQuery(jpql, Long.class)
                .setParameter("codice", codice)
                .setMaxResults(1);
        if (misuraIdEsclusa != null) {
            query.setParameter("esclusa", misuraIdEsclusa);
        }
        return !query.getResultList().isEmpty();
    }

    private void eseguiFlushConControlloUnicita() {
        try {
            entityManager.flush();
        } catch (PersistenceException | DataIntegrityViolationException ex) {
            if (contieneViolazioneUnicita(ex)) {
                throw new CodiceMisuraDuplicatoException();
            }
            throw ex;
        }
    }

    private static boolean contieneViolazioneUnicita(Throwable errore) {
        for (Throwable causa = errore; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && SQLSTATE_UNICO_VIOLATO.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
