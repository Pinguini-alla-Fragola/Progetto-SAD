package it.unina.picus.comunicazioni.interno.persistenza;

import it.unina.picus.comunicazioni.api.AvvisiOperativiApi;
import it.unina.picus.comunicazioni.api.DestinatariAvvisi;
import it.unina.picus.shared.ControlloAccesso;
import it.unina.picus.comunicazioni.api.ComunicazioniApi.AvvisoOperativo;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Consegna in-app atomica con il contatto, tramite il database condiviso del monolite. */
@Repository
@Transactional(readOnly = true)
public class AvvisiOperativiJpa implements AvvisiOperativiApi {
    private final EntityManager entityManager;
    private final Clock clock;
    private final DestinatariAvvisi destinatari;

    public AvvisiOperativiJpa(EntityManager entityManager, Clock clock, DestinatariAvvisi destinatari) {
        this.entityManager = entityManager;
        this.clock = clock;
        this.destinatari = destinatari;
    }

    @Override
    @Transactional
    public void avvisaDisponibilita(UUID richiestaId, UUID contattoId, UUID destinatarioId) {
        if (richiestaId == null || contattoId == null || destinatarioId == null) {
            throw new IllegalArgumentException("Riferimenti dell'avviso obbligatori");
        }
        if (!destinatari.corrisponde(richiestaId, contattoId, destinatarioId)) {
            throw new IllegalArgumentException("Riferimenti dell'avviso non coerenti con il contatto");
        }
        entityManager.createNativeQuery("""
                insert into comunicazioni_avvisi_operativi
                    (id, richiesta_id, contatto_id, destinatario_id, tipo, registrato_il)
                values (:id, :richiesta, :contatto, :destinatario, 'RICHIESTA_DISPONIBILITA', :istante)
                on conflict (contatto_id, tipo) do nothing
                """).setParameter("id", UUID.randomUUID()).setParameter("richiesta", richiestaId)
                .setParameter("contatto", contattoId).setParameter("destinatario", destinatarioId)
                .setParameter("istante", clock.instant()).executeUpdate();
    }

    @Override
    public List<AvvisoOperativo> elencoAvvisiOperativi(IdentitaAutenticata attore) {
        ControlloAccesso.richiedi(attore != null && attore.accountId() != null && attore.ruolo() != null,
                "Identità verificata obbligatoria");
        return entityManager.createQuery("""
                select a from AvvisoOperativoJpa a where a.destinatarioId = :account
                order by a.registratoIl, a.id
                """, AvvisoOperativoJpa.class).setParameter("account", attore.accountId())
                .getResultList().stream().map(AvvisoOperativoJpa::aDto).toList();
    }
}
