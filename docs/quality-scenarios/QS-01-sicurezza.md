# QS-01 Isolamento degli accessi

**Attributo di qualità:** Sicurezza.

**Priorità proposta:** P1.

**Motivazione:** Ogni OPT deve poter gestire le richieste che la riguardano. Le pratiche che riguardano solo altre OPT devono restare riservate. Gli operatori devono poter consultare le proprie assegnazioni e i propri dati. Al Banco serve una vista sulle richieste per coordinare il lavoro.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
|---|---|---|---|---|---|
| Un utente con account OPT o Operatore, con o senza una sessione valida. | Prova a leggere o modificare dati senza averne il permesso, anche cambiando l'identificativo inviato al server. La prova include tentativi di consultare altre pratiche di una OPT e di annullare, senza autorizzazione, una richiesta nello stato Confermata. | L'applicazione funziona normalmente e usa dati sintetici. La prova coinvolge due OPT, A e B, un operatore esterno collegato alla OPT A, un operatore interno del Banco, un operatore esterno pagato dal Banco e un utente Banco con ruolo di coordinamento. Le richieste di prova comprendono erogatori interpellati ed erogatori assegnati. | I controlli con cui il server verifica l'identità, i permessi e il coinvolgimento dell'utente nella richiesta. | Prima di restituire o modificare dati, il server verifica l'identità dell'utente, il suo ruolo e il suo legame con la risorsa. L'erogatore può consultare la richiesta per cui è stato interpellato. Le altre pratiche della OPT richiedente restano riservate. Il server consente l'annullamento eccezionale alla OPT erogatrice assegnata, alla OPT a cui appartiene l'operatore esterno assegnato oppure al Banco per i propri operatori. | Il server respinge tutti i tentativi non autorizzati della matrice di prova, con 0 dati riservati restituiti e 0 modifiche non consentite. Risponde con il codice 401 se manca un'autenticazione valida e con il 403 se l'identità è valida ma manca il permesso. Tutti i casi autorizzati della stessa matrice vanno a buon fine. |

## Approccio di verifica

Preparare una matrice che incroci il ruolo, il titolare della risorsa, l'azione e il legame con la richiesta, distinguendo tra OPT richiedente, erogatore interpellato ed erogatore assegnato. Tenere separati gli operatori del Banco e gli esterni collegati a una OPT. Verificare che l'erogatore possa consultare la richiesta che lo riguarda e che il sistema blocchi i tentativi di accedere alle altre pratiche. Per l'annullamento, provare tutti e tre i casi autorizzati e verificare che il sistema lo neghi ai singoli account Operatore, anche se appartengono all'organizzazione assegnata.

Eseguire le prove sia dall'interfaccia sia inviando richieste direttamente al server. Confrontare i dati prima e dopo i tentativi respinti e annotare la versione dell'applicazione e i casi eseguiti.
