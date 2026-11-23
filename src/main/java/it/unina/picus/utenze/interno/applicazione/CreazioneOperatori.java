package it.unina.picus.utenze.interno.applicazione;

import it.unina.picus.utenze.api.UtenzeApi.IdentitaAutenticata;

/** Caso d'uso: il Banco crea l'account personale di un Operatore. */
public interface CreazioneOperatori {

    /**
     * Crea l'account e il profilo dell'Operatore, in attesa di attivazione. L'account non ha ancora una
     * password che si possa usare: la sceglierà l'Operatore dal link di attivazione.
     *
     * @param attore l'utente che fa la richiesta, ricavato dal server dalla sessione
     * @param comando i campi compilati dal Banco
     * @return l'account creato
     * @throws DatiOperatoreNonValidiException se un campo non è valido o l'email è già usata
     * @throws SecurityException se chi fa la richiesta non è il Banco
     */
    OperatoreCreato crea(IdentitaAutenticata attore, ComandoCreaOperatore comando);
}
