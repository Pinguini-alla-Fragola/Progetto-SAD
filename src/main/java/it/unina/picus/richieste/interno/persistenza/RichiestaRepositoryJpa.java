package it.unina.picus.richieste.interno.persistenza;

import it.unina.picus.richieste.api.RichiesteApi.StatoRichiesta;
import it.unina.picus.richieste.interno.dominio.RichiestaDiMisura;
import it.unina.picus.richieste.interno.dominio.RichiestaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adattatore PostgreSQL/JPA; il vincolo univoco coordina anche processi applicativi diversi. */
@Repository
public class RichiestaRepositoryJpa implements RichiestaRepository {
    private final EntityManager entityManager;

    public RichiestaRepositoryJpa(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public RichiestaDiMisura registra(RichiestaDiMisura richiesta) {
        int inserite = entityManager.createNativeQuery("""
                insert into richieste
                    (id, titolare_id, misura_id, tipo_destinatario, comune_codice,
                     description, inviata_il, stato, riferimento_invio)
                values (:id, :titolare, :misura, :tipo, :comune, :descrizione, :inviata, :stato, :riferimento)
                on conflict (titolare_id, riferimento_invio) do nothing
                """)
                .setParameter("id", richiesta.id())
                .setParameter("titolare", richiesta.titolareId())
                .setParameter("misura", richiesta.misuraId())
                .setParameter("tipo", richiesta.tipoDestinatario().name())
                .setParameter("comune", richiesta.comuneCodice())
                .setParameter("descrizione", richiesta.description())
                .setParameter("inviata", richiesta.inviataIl())
                .setParameter("stato", richiesta.stato().name())
                .setParameter("riferimento", richiesta.riferimentoInvio())
                .executeUpdate();
        if (inserite == 1) {
            return richiesta;
        }
        // READ COMMITTED: dopo il conflitto il secondo comando vede l'invio già committato.
        return cercaPerInvio(richiesta.titolareId(), richiesta.riferimentoInvio())
                .orElseThrow(() -> new IllegalStateException("Impossibile registrare l'invio"));
    }

    @Override
    public Optional<RichiestaDiMisura> cercaPerId(UUID id) {
        return Optional.ofNullable(entityManager.find(RichiestaJpa.class, id)).map(RichiestaJpa::aDominio);
    }

    @Override
    public Optional<RichiestaDiMisura> cercaPerInvio(UUID titolareId, UUID riferimentoInvio) {
        if (riferimentoInvio == null) {
            return Optional.empty();
        }
        return entityManager.createQuery("""
                select r from RichiestaJpa r
                where r.titolareId = :titolare and r.riferimentoInvio = :riferimento
                """, RichiestaJpa.class)
                .setParameter("titolare", titolareId).setParameter("riferimento", riferimentoInvio)
                .getResultStream().findFirst().map(RichiestaJpa::aDominio);
    }

    @Override
    public List<RichiestaDiMisura> cercaPerTitolare(UUID titolareId) {
        return entityManager.createQuery("""
                select r from RichiestaJpa r where r.titolareId = :titolare order by r.inviataIl, r.id
                """, RichiestaJpa.class)
                .setParameter("titolare", titolareId)
                .getResultList().stream().map(RichiestaJpa::aDominio).toList();
    }

    @Override
    public List<RichiestaDiMisura> codaBanco() {
        return entityManager.createQuery("""
                select r from RichiestaJpa r where r.stato in :stati order by r.inviataIl, r.id
                """, RichiestaJpa.class)
                .setParameter("stati", List.of(StatoRichiesta.CREATA, StatoRichiesta.PRESA_IN_CARICO,
                        StatoRichiesta.IN_PROGRAMMAZIONE))
                .getResultList().stream().map(RichiestaJpa::aDominio).toList();
    }
}
