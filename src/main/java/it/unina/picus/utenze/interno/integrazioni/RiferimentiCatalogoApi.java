package it.unina.picus.utenze.interno.integrazioni;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.utenze.interno.dominio.RiferimentiCatalogo;

/** Risponde alle domande del dominio usando l'API di consultazione del Catalogo. */
@Component
@Profile("!skeleton")
class RiferimentiCatalogoApi implements RiferimentiCatalogo {

    private final ConsultazioneCatalogoApi catalogo;

    RiferimentiCatalogoApi(ConsultazioneCatalogoApi catalogo) {
        this.catalogo = catalogo;
    }

    @Override
    public boolean comuneEsiste(String codice) {
        return catalogo.cercaComune(codice).isPresent();
    }

    @Override
    public boolean misuraAttiva(long misuraId) {
        return catalogo.cercaMisura(misuraId).filter(Misura::attiva).isPresent();
    }
}
