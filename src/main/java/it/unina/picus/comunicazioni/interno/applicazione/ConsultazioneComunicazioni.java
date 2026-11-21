package it.unina.picus.comunicazioni.interno.applicazione;

import java.util.UUID;

import it.unina.picus.comunicazioni.interno.dominio.ComunicazioneSalvata;
import it.unina.picus.comunicazioni.interno.dominio.FileSalvato;
import it.unina.picus.comunicazioni.interno.dominio.PaginaComunicazioni;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/**
 * Caso d'uso: consultare le comunicazioni. Il Banco vede tutte quelle inviate; OPT e Operatori solo quelle
 * ricevute. Testo e allegati restano riservati ai destinatari registrati e al Banco.
 */
public interface ConsultazioneComunicazioni {

    /**
     * Restituisce una pagina delle comunicazioni inviate, con ricerca e filtri su tutto lo storico.
     *
     * @param attore l'utente che fa la richiesta
     * @param richiesta ricerca e pagina chieste
     * @return la pagina, dalla più recente
     * @throws SecurityException se chi fa la richiesta non è il Banco
     * @throws DatiComunicazioneNonValidiException se un valore della ricerca non è ammesso
     */
    PaginaComunicazioni inviate(IdentitaAutenticata attore, RichiestaElenco richiesta);

    /**
     * Restituisce una pagina delle comunicazioni ricevute dall'account della sessione, con ricerca e filtri su
     * tutto lo storico.
     *
     * @param attore l'utente che fa la richiesta
     * @param richiesta ricerca e pagina chieste
     * @return la pagina, dalla più recente
     * @throws SecurityException se chi fa la richiesta non può ricevere comunicazioni
     * @throws DatiComunicazioneNonValidiException se un valore della ricerca non è ammesso
     */
    PaginaComunicazioni ricevute(IdentitaAutenticata attore, RichiestaElenco richiesta);

    /**
     * Apre una comunicazione.
     *
     * @param attore l'utente che fa la richiesta
     * @param comunicazioneId comunicazione da aprire
     * @return la comunicazione
     * @throws SecurityException se chi fa la richiesta non è il Banco né un destinatario
     * @throws RisorsaComunicazioniNonTrovataException se la comunicazione non esiste
     */
    ComunicazioneSalvata consulta(IdentitaAutenticata attore, UUID comunicazioneId);

    /**
     * Scarica un allegato, con gli stessi permessi della comunicazione a cui appartiene.
     *
     * @param attore l'utente che fa la richiesta
     * @param allegatoId allegato da scaricare
     * @return il file
     * @throws SecurityException se chi fa la richiesta non è il Banco né un destinatario
     * @throws RisorsaComunicazioniNonTrovataException se l'allegato non esiste
     */
    FileSalvato scarica(IdentitaAutenticata attore, UUID allegatoId);
}
