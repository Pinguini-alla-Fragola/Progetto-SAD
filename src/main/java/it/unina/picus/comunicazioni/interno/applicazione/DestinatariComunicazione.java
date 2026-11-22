package it.unina.picus.comunicazioni.interno.applicazione;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import it.unina.picus.comunicazioni.interno.dominio.BozzaComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.CampoComunicazione;
import it.unina.picus.comunicazioni.interno.dominio.Errori;
import it.unina.picus.comunicazioni.interno.dominio.PlateaDestinatari;
import it.unina.picus.utenze.api.UtenzeApi;
import it.unina.picus.utenze.api.UtenzeApi.AccountSintetico;
import it.unina.picus.utenze.api.UtenzeApi.RuoloUtente;

/**
 * Individua gli account che ricevono la comunicazione, chiedendo a Utenze quelli attivi. Gli account in attesa di
 * attivazione o revocati non ricevono comunicazioni.
 */
@Component
class DestinatariComunicazione {

    private final UtenzeApi utenze;

    DestinatariComunicazione(UtenzeApi utenze) {
        this.utenze = utenze;
    }

    /**
     * Restituisce i destinatari della bozza, già controllata dalle regole.
     *
     * @param bozza comunicazione con un tipo di destinatari valido
     * @param errori dove segnalare un gruppo con account non ammessi o un invio senza destinatari
     * @return gli account destinatari
     */
    Set<UUID> risolvi(BozzaComunicazione bozza, Errori errori) {
        PlateaDestinatari platea = PlateaDestinatari.di(bozza.tipo());
        Set<UUID> attivi = utenze.elencoAccountAttivi(platea.ruolo()).stream()
                .map(AccountSintetico::id).collect(Collectors.toUnmodifiableSet());
        if (!platea.gruppo()) {
            if (attivi.isEmpty()) {
                errori.aggiungi(CampoComunicazione.DESTINATARI_TIPO,
                        "Non ci sono account attivi a cui inviare la comunicazione");
            }
            return attivi;
        }
        if (!attivi.containsAll(bozza.gruppo())) {
            errori.aggiungi(CampoComunicazione.GRUPPO, platea.ruolo() == RuoloUtente.OPT
                    ? "Il gruppo può contenere solo OPT attive"
                    : "Il gruppo può contenere solo operatori attivi");
        }
        return bozza.gruppo();
    }
}
