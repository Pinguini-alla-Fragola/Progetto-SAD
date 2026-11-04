# QS-02 Minimizzazione e riservatezza dei dati

**Attributo di qualità:** Privacy.

**Priorità proposta:** P1.

**Motivazione:** Il coordinamento usa richieste prive di identificativi degli assistiti e il monitoraggio usa conteggi aggregati. I dati amministrativi ed economici degli operatori richiedono una protezione distinta dai recapiti necessari a organizzare il servizio.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
|---|---|---|---|---|---|
| Una OPT che compila una richiesta o un utente che consulta dati degli operatori. | Invia campi identificativi degli assistiti non previsti dal modulo oppure tenta di consultare dati riservati di un operatore senza permesso. | Moduli con campi strutturati, senza testo libero o allegati relativi agli assistiti. Controlli estesi alle schede e ai dati quantitativi delle OPT. Tutti i dati di prova sono sintetici. | Modello dati, campi accettati dal server, controlli di validazione, log, viste ed eventuali esportazioni. | Il server rifiuta i campi non ammessi senza salvarne o riportarne il contenuto nei messaggi o nei log. L'anagrafica completa, i dati contrattuali e il costo orario dell'operatore sono riservati al Banco autorizzato e all'operatore titolare. La OPT richiedente può vedere nome e telefono del referente indicato per l'appuntamento in presenza: questi recapiti servono al coordinamento e non sono dati degli assistiti. | 0 campi identificativi degli assistiti nel modello dati e nei campi accettati dal server. Tutti i 20 invii di prova con campi non ammessi sono respinti senza salvarne o riportarne i valori nei messaggi o nei log. Nessun identificativo di assistito rilevato nei dati, log ed eventuali esportazioni campionati. Tutti gli accessi non autorizzati ai dati riservati degli operatori sono negati. |

## Approccio di verifica

Ispezionare modello dati e campi accettati dal server. Inviare 20 richieste di prova contenenti campi identificativi sintetici non ammessi e verificare database, messaggi, log ed eventuali esportazioni. Applicare la matrice di QS-01 ai dati riservati degli operatori e verificare separatamente la visibilità dei recapiti del referente nella richiesta che coinvolge la OPT. Conservazione e cancellazione restano da definire in Q-05.
