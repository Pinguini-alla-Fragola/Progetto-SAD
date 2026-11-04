# QS-01 Isolamento degli accessi

**Attributo di qualità:** Sicurezza.

**Priorità proposta:** P1.

**Motivazione:** Ogni organizzazione deve poter lavorare sulle richieste che la riguardano senza accedere alle pratiche delle altre OPT. Gli operatori devono vedere le proprie assegnazioni e i propri dati. Il Banco mantiene la visibilità necessaria al coordinamento.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
|---|---|---|---|---|---|
| Un utente OPT o Operatore, con o senza una sessione valida. | Tenta di leggere o modificare dati per cui non ha il permesso, anche cambiando l'identificativo inviato al server. I casi comprendono l'accesso alle altre pratiche di una OPT e l'annullamento di una richiesta Confermata da parte di un account non autorizzato. | Funzionamento ordinario, con dati sintetici: due OPT A/B, un operatore esterno collegato alla OPT A, un operatore interno del Banco, uno esterno pagato dal Banco e un utente Banco con ruolo di coordinamento. Le richieste includono erogatori interpellati e assegnati. | Controlli sul server per autenticazione, autorizzazione e coinvolgimento nella richiesta. | Il server verifica identità, ruolo e relazione con la risorsa prima di restituire dati o applicare modifiche. Un erogatore può consultare la richiesta per cui è stato interpellato, senza accedere alle altre pratiche della OPT richiedente. L'annullamento eccezionale spetta alla OPT erogatrice assegnata, alla OPT di appartenenza dell'operatore esterno assegnato oppure al Banco per i propri operatori. | Tutti i casi non autorizzati della matrice di prova sono negati: 0 dati riservati restituiti e 0 modifiche non consentite. Risposta 401 in assenza di autenticazione valida e 403 con identità valida ma permesso mancante. Tutti i casi autorizzati della stessa matrice riescono. |

## Stato delle soglie

L'isolamento degli accessi è un vincolo del contesto. I codici di risposta e la matrice di prova sono proposte del team.

## Approccio di verifica

Preparare una matrice che combini ruolo, titolare della risorsa, azione e relazione con la richiesta: OPT richiedente, erogatore interpellato o erogatore assegnato. Distinguere gli operatori del Banco dagli esterni collegati a una OPT. Includere sia gli accessi legittimi alla richiesta da parte dell'erogatore sia i tentativi di consultare altre pratiche. Per l'annullamento verificare le tre autorizzazioni previste e il diniego ai singoli account Operatore, anche quando appartengono all'organizzazione assegnata.

Eseguire i casi dall'interfaccia e con richieste dirette al server. Confrontare i dati prima e dopo i tentativi negati e registrare versione dell'applicazione e casi eseguiti.
