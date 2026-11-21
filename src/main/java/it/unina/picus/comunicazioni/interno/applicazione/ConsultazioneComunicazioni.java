package it.unina.picus.comunicazioni.interno.applicazione;

import java.util.List;
import java.util.UUID;

import it.unina.picus.comunicazioni.interno.dominio.ComunicazioneSalvata;
import it.unina.picus.comunicazioni.interno.dominio.FileSalvato;
import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/**
 * Caso d'uso: consultare le comunicazioni. Il Banco vede tutte quelle inviate; OPT e Operatori solo quelle
 * ricevute. Testo e allegati restano riservati ai destinatari registrati e al Banco.
 */
public interface ConsultazioneComunicazioni {

    /**
     * Elenca le comunicazioni inviate.
     *
     * @param attore l'utente che fa la richiesta
     * @return le comunicazioni, dalla più recente
     * @throws SecurityException se chi fa la richiesta non è il Banco
     */
    List<ComunicazioneSalvata> inviate(IdentitaAutenticata attore);

    /**
     * Elenca le comunicazioni ricevute dall'account della sessione.
     *
     * @param attore l'utente che fa la richiesta
     * @return le comunicazioni ricevute, dalla più recente
     * @throws SecurityException se chi fa la richiesta non può ricevere comunicazioni
     */
    List<ComunicazioneSalvata> ricevute(IdentitaAutenticata attore);

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
