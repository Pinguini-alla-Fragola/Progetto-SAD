package it.unina.picus.utenze.web;

import it.unina.picus.utenze.interno.applicazione.ConsegnaLinkAttivazione.Esito;

/** Testi con cui il Banco legge l'esito della consegna di un link di attivazione, uguali per OPT e Operatori. */
final class MessaggiInvito {

    private MessaggiInvito() {
    }

    /**
     * Testo dopo la creazione di un account.
     *
     * @param esito esito della consegna del link
     * @param registrazione frase che dice cosa è stato registrato, per esempio «Operatore creato»
     */
    static String allaCreazione(Esito esito, String registrazione) {
        return switch (esito) {
            case INVIATO -> registrazione + ". Email di attivazione inviata.";
            case SIMULATO -> registrazione
                    + " (modalità demo/simulata: nessun server SMTP configurato, email non inviata).";
            case NON_RIUSCITO -> registrazione + " in attesa di attivazione, ma l'invio dell'email è fallito. "
                    + "Puoi reinviare l'invito dall'elenco Account.";
        };
    }

    /** Testo dopo il reinvio riuscito di un link; il fallimento ha una risposta d'errore a parte. */
    static String alReinvio(Esito esito) {
        return esito == Esito.SIMULATO
                ? "Nuovo link di attivazione generato in modalità simulata."
                : "Nuovo link di attivazione inviato via email.";
    }

    /** Testo quando il link è stato rigenerato ma la posta non ha accettato l'invio. */
    static final String REINVIO_NON_RIUSCITO =
            "Invio email di attivazione non riuscito. Verifica la configurazione SMTP e riprova.";
}
