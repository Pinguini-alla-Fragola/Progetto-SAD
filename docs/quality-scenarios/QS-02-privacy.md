# QS-02 Minimizzazione e riservatezza dei dati

**Attributo di qualità:** Privacy.

**Priorità proposta:** P1.

**Motivazione:** Per coordinare il servizio bastano richieste senza identificativi degli assistiti. Per il monitoraggio si usano conteggi aggregati. I recapiti necessari a organizzare il servizio seguono regole di accesso diverse da quelle dei dati amministrativi ed economici degli operatori.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
|---|---|---|---|---|---|
| Una OPT che compila una richiesta o un utente che consulta i dati degli operatori. | La OPT prova a inviare campi che identificano gli assistiti e che il modulo non prevede. L'utente prova a consultare i dati riservati di un operatore senza averne il permesso. | I moduli usano campi strutturati e non prevedono testo libero o allegati relativi agli assistiti. I controlli riguardano anche le schede e i dati quantitativi delle OPT. Tutti i dati di prova sono sintetici. | Il modello dati, i campi accettati dal server, i controlli di validazione, i log, le viste e le eventuali esportazioni. | Il server rifiuta i campi non ammessi senza salvare i valori né inserirli nei messaggi o nei log. Solo gli utenti autorizzati del Banco e l'operatore a cui si riferiscono i dati possono vedere l'anagrafica completa, i dati contrattuali e il costo orario. Per organizzare l'appuntamento in presenza, la OPT richiedente può vedere il nome e il telefono del referente indicato. Sono recapiti di servizio, distinti dai dati degli assistiti. | Sono previsti 0 campi identificativi degli assistiti sia nel modello dati sia tra quelli accettati dal server. Il server respinge tutti e 20 gli invii di prova con campi non ammessi, senza salvare i valori né riportarli nei messaggi o nei log. Nei campioni di dati, log ed eventuali esportazioni non compare alcun identificativo degli assistiti. Il sistema blocca ogni accesso non autorizzato ai dati riservati degli operatori. |

## Approccio di verifica

Esaminare il modello dati e i campi che il server accetta. Inviare 20 richieste di prova con campi identificativi sintetici non ammessi e controllare il database, i messaggi, i log e le eventuali esportazioni. Usare la matrice di QS-01 per verificare chi può accedere ai dati riservati degli operatori. Controllare a parte che la OPT possa vedere i recapiti del referente nella richiesta che la riguarda. Il chiarimento Q-05 riguarda le regole di conservazione e cancellazione, ancora da definire.
