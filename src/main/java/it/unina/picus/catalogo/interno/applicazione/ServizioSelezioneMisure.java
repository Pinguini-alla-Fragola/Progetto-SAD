package it.unina.picus.catalogo.interno.applicazione;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.api.SelezioneMisureApi;
import it.unina.picus.catalogo.interno.persistenza.OffertaTerritorialeJpa;

/**
 * Implementazione applicativa della verifica transazionale di selezionabilità delle misure
 * tramite lock condiviso ({@code PESSIMISTIC_READ}) in contesa con la disattivazione.
 */
@Component
class ServizioSelezioneMisure implements SelezioneMisureApi {

    private final OffertaTerritorialeJpa offerta;

    ServizioSelezioneMisure(OffertaTerritorialeJpa offerta) {
        this.offerta = offerta;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean bloccaMisuraAttiva(long misuraId) {
        return offerta.bloccaMisura(misuraId).filter(Misura::attiva).isPresent();
    }
}
