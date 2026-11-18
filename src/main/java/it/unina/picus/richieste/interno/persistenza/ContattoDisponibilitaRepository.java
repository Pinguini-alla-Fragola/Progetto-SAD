package it.unina.picus.richieste.interno.persistenza;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accede ai contatti di disponibilità tramite il pattern <em>Repository</em>, implementato con Spring Data JPA.
 *
 * <p>Raccoglie le query per consultare i contatti, ricostruire lo storico e leggere i riferimenti
 * necessari prima dell'aggiornamento.
 */
public interface ContattoDisponibilitaRepository extends JpaRepository<ContattoDisponibilitaJpa, UUID> {

    /** Verifica sotto il lock della pratica se c'è già un contatto in attesa. */
    boolean existsByRichiesta_IdAndEsito(UUID richiestaId, String esito);

    /** Ordine persistente dei tentativi, anche quando due invii hanno lo stesso istante. */
    List<ContattoDisponibilitaJpa> findAllByRichiesta_IdOrderByOrdineInvioAsc(UUID richiestaId);

    /**
     * Cerca un contatto caricando anche la richiesta necessaria per il dettaglio.
     *
     * @param id identificativo del contatto
     * @return il contatto con la richiesta, oppure un valore vuoto se non esiste
     */
    @EntityGraph(attributePaths = "richiesta")
    Optional<ContattoDisponibilitaJpa> findOneById(UUID id);

    /**
     * Legge i riferimenti necessari per verificare il destinatario e bloccare la richiesta.
     *
     * <p>La proiezione lascia il contatto fuori dal contesto di persistenza. Il servizio può quindi
     * leggere il suo esito dal database dopo aver acquisito il lock.
     *
     * @param id identificativo del contatto
     * @return identificativi della richiesta e del destinatario, oppure un valore vuoto
     */
    @Query("""
            select c.richiesta.id as richiestaId, c.erogatoreId as erogatoreId
            from ContattoDisponibilitaJpa c
            where c.id = :id
            """)
    Optional<RiferimentoContatto> cercaRiferimento(@Param("id") UUID id);

    /** Proiezione degli identificativi usati prima di acquisire il lock. */
    interface RiferimentoContatto {
        /** {@return identificativo della richiesta associata} */
        UUID getRichiestaId();

        /** {@return identificativo dell'account destinatario} */
        UUID getErogatoreId();
    }
}
