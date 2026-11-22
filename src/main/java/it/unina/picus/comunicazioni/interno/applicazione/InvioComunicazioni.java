package it.unina.picus.comunicazioni.interno.applicazione;

import java.util.List;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/** Caso d'uso: il Banco invia una comunicazione a tutte le OPT, a tutti gli operatori o a un gruppo. */
public interface InvioComunicazioni {

    /**
     * Controlla la comunicazione, individua gli account destinatari e la registra insieme a loro e agli allegati.
     *
     * @param attore l'utente che fa la richiesta, ricavato dal server dalla sessione
     * @param comando i campi compilati dal Banco
     * @return l'esito dell'invio
     * @throws DatiComunicazioneNonValidiException se un campo non è valido o non c'è nessun destinatario
     * @throws SecurityException se chi fa la richiesta non è il Banco
     */
    ComunicazioneInviata invia(IdentitaAutenticata attore, ComandoInvioComunicazione comando);

    /**
     * Elenca gli account attivi tra cui il Banco può scegliere il gruppo per un tipo di destinatari.
     *
     * @param attore l'utente che fa la richiesta, ricavato dal server dalla sessione
     * @param destinatariTipo nome del tipo di destinatari
     * @return gli account selezionabili, in ordine di nome
     * @throws DatiComunicazioneNonValidiException se il tipo non è valido
     * @throws SecurityException se chi fa la richiesta non è il Banco
     */
    List<AccountSelezionabile> selezionabili(IdentitaAutenticata attore, String destinatariTipo);
}
