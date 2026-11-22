package it.unina.picus.comunicazioni.interno.persistenza;

import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.comunicazioni.interno.dominio.ArchivioComunicazioni;
import it.unina.picus.comunicazioni.interno.dominio.NuovaComunicazione;
import jakarta.persistence.EntityManager;

/** Salva le comunicazioni e i loro destinatari nel database condiviso del monolite. */
@Repository
@Transactional
class ArchivioComunicazioniJpa implements ArchivioComunicazioni {

    private final EntityManager entityManager;

    ArchivioComunicazioniJpa(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void registra(NuovaComunicazione comunicazione) {
        entityManager.persist(new ComunicazioneJpa(comunicazione.id(), comunicazione.mittenteId(),
                comunicazione.titolo(), comunicazione.contenuto(), comunicazione.tipo(), comunicazione.inviataIl()));
        for (UUID destinatario : comunicazione.destinatari()) {
            entityManager.persist(new DestinatarioComunicazioneJpa(comunicazione.id(), destinatario));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long contaDaConfermare(UUID destinatarioId) {
        return entityManager.createQuery("""
                select count(d) from DestinatarioComunicazioneJpa d
                where d.id.destinatarioId = :account and d.confermataIl is null
                """, Long.class).setParameter("account", destinatarioId).getSingleResult();
    }
}
