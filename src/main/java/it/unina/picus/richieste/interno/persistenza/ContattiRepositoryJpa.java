package it.unina.picus.richieste.interno.persistenza;

import it.unina.picus.catalogo.api.CatalogoApi.AmbitoErogazione;
import it.unina.picus.richieste.api.RichiesteApi.ContattoDisponibilita;
import it.unina.picus.richieste.api.RichiesteApi.EsitoContatto;
import it.unina.picus.richieste.interno.dominio.ContattiRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adatta i contatti persistiti alla porta del coordinamento. */
@Repository
public class ContattiRepositoryJpa implements ContattiRepository {
    private final ContattoDisponibilitaRepository contatti;
    private final EntityManager em;

    public ContattiRepositoryJpa(ContattoDisponibilitaRepository contatti, EntityManager em) {
        this.contatti = contatti;
        this.em = em;
    }

    @Override
    public Optional<UUID> richiestaDelContatto(UUID id) {
        return contatti.cercaRiferimento(id).map(ContattoDisponibilitaRepository.RiferimentoContatto::getRichiestaId);
    }

    @Override
    public Optional<ContattoDisponibilita> cerca(UUID id) {
        return contatti.findById(id).map(ContattiRepositoryJpa::aDto);
    }

    @Override
    public List<ContattoDisponibilita> storico(UUID richiestaId) {
        return contatti.findAllByRichiesta_IdOrderByOrdineInvioAsc(richiestaId).stream()
                .map(ContattiRepositoryJpa::aDto).toList();
    }

    @Override
    public boolean inAttesa(UUID richiestaId) {
        return contatti.existsByRichiesta_IdAndEsito(richiestaId, EsitoContatto.IN_ATTESA.name());
    }

    @Override
    public ContattoDisponibilita registra(UUID richiestaId, UUID erogatoreId, AmbitoErogazione ambito,
            String nomeMisura, Instant istante) {
        var richiesta = em.getReference(RichiestaJpa.class, richiestaId);
        return aDto(contatti.saveAndFlush(ContattoDisponibilitaJpa.crea(richiesta, erogatoreId, ambito, nomeMisura, istante)));
    }

    private static ContattoDisponibilita aDto(ContattoDisponibilitaJpa contatto) {
        return new ContattoDisponibilita(contatto.getId(), contatto.getRichiesta().getId(),
                contatto.getErogatoreId(), contatto.getAmbito(), contatto.getEsito());
    }
}
