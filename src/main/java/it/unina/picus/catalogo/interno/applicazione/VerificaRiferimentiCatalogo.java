package it.unina.picus.catalogo.interno.applicazione;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.catalogo.api.SelezioneMisureApi;
import it.unina.picus.catalogo.interno.persistenza.OffertaTerritorialeJpa;
import it.unina.picus.utenze.api.RiferimentiCatalogo;

/**
 * Fornisce agli altri moduli la verifica di comuni e misure e il lock condiviso di selezionabilità.
 * Usa la consultazione e l'adattatore di catalogo senza dipendere da {@code UtenzeApi}, evitando cicli
 * tra i bean.
 */
@Component
class VerificaRiferimentiCatalogo implements RiferimentiCatalogo, SelezioneMisureApi {

    private final ConsultazioneCatalogoApi catalogo;
    private final OffertaTerritorialeJpa offerta;

    VerificaRiferimentiCatalogo(ConsultazioneCatalogoApi catalogo, OffertaTerritorialeJpa offerta) {
        this.catalogo = catalogo;
        this.offerta = offerta;
    }

    @Override
    public boolean comuneEsiste(String codice) {
        return catalogo.cercaComune(codice).isPresent();
    }

    /** {@code cercaMisura} trova anche le misure disattivate, che restano per lo storico: conta solo se è attiva. */
    @Override
    public boolean misuraAttiva(long misuraId) {
        return catalogo.cercaMisura(misuraId).filter(Misura::attiva).isPresent();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean bloccaMisuraAttiva(long misuraId) {
        return offerta.bloccaMisura(misuraId).filter(Misura::attiva).isPresent();
    }
}
