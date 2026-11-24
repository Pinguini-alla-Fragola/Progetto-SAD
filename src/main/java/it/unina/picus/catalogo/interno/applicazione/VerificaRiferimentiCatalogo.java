package it.unina.picus.catalogo.interno.applicazione;

import org.springframework.stereotype.Component;

import it.unina.picus.catalogo.api.CatalogoApi.Misura;
import it.unina.picus.catalogo.api.ConsultazioneCatalogoApi;
import it.unina.picus.utenze.api.RiferimentiCatalogo;

/**
 * Fornisce a Utenze la verifica di comuni e misure. Usa solo la consultazione del catalogo: il servizio di
 * ricerca degli erogatori dipende da {@code UtenzeApi} e qui creerebbe un ciclo tra i bean.
 */
@Component
class VerificaRiferimentiCatalogo implements RiferimentiCatalogo {

    private final ConsultazioneCatalogoApi catalogo;

    VerificaRiferimentiCatalogo(ConsultazioneCatalogoApi catalogo) {
        this.catalogo = catalogo;
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
}
