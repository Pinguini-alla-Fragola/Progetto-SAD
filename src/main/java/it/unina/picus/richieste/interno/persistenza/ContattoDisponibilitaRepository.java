package it.unina.picus.richieste.interno.persistenza;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContattoDisponibilitaRepository extends JpaRepository<ContattoDisponibilitaJpa, UUID> {

    @EntityGraph(attributePaths = "richiesta")
    Optional<ContattoDisponibilitaJpa> findOneById(UUID id);

    List<ContattoDisponibilitaJpa> findAllByRichiesta_IdOrderByInviatoIlAscIdAsc(UUID richiestaId);

    /**
     * Legge solo i riferimenti necessari a controllare il destinatario e bloccare la pratica.
     * L'esito viene poi letto sotto lock, senza riutilizzare un'entità caricata prima del lock.
     */
    @Query("""
            select c.richiesta.id as richiestaId, c.erogatoreId as erogatoreId
            from ContattoDisponibilitaJpa c
            where c.id = :id
            """)
    Optional<RiferimentoContatto> cercaRiferimento(@Param("id") UUID id);

    interface RiferimentoContatto {
        UUID getRichiestaId();
        UUID getErogatoreId();
    }
}
