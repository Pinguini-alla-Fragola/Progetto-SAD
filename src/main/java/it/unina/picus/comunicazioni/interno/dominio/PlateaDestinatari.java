package it.unina.picus.comunicazioni.interno.dominio;

import it.unina.picus.comunicazioni.api.ComunicazioniApi.TipoDestinatari;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * A quale ruolo si rivolge un tipo di destinatari e se il Banco sceglie un gruppo di account.
 * Le comunicazioni alle OPT arrivano agli account delle organizzazioni.
 *
 * @param ruolo ruolo degli account che possono ricevere la comunicazione
 * @param gruppo vero se il Banco sceglie i singoli account, falso se la comunicazione va a tutti
 */
public record PlateaDestinatari(RuoloUtente ruolo, boolean gruppo) {

    /**
     * Ricava la platea dal tipo scelto dal Banco.
     *
     * @param tipo tipo di destinatari
     * @return ruolo e modalità di scelta corrispondenti
     */
    public static PlateaDestinatari di(TipoDestinatari tipo) {
        return switch (tipo) {
            case TUTTE_LE_OPT -> new PlateaDestinatari(RuoloUtente.OPT, false);
            case GRUPPO_OPT -> new PlateaDestinatari(RuoloUtente.OPT, true);
            case TUTTI_GLI_OPERATORI -> new PlateaDestinatari(RuoloUtente.OPERATORE, false);
            case GRUPPO_OPERATORI -> new PlateaDestinatari(RuoloUtente.OPERATORE, true);
        };
    }
}
