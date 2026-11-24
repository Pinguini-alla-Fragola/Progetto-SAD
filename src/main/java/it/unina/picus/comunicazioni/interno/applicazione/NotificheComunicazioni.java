package it.unina.picus.comunicazioni.interno.applicazione;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/** Caso d'uso: una OPT o un Operatore vede quante comunicazioni ricevute aspettano la sua conferma. */
public interface NotificheComunicazioni {

    /**
     * Conta le comunicazioni ricevute e non ancora confermate. Aprirle non basta: serve la conferma esplicita.
     *
     * @param attore l'utente che fa la richiesta, ricavato dal server dalla sessione
     * @return numero di comunicazioni da confermare
     * @throws SecurityException se chi fa la richiesta non può ricevere comunicazioni
     */
    long daConfermare(IdentitaAutenticata attore);
}
