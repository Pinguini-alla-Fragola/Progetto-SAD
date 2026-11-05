# QS-01A Consultazione non autorizzata di una richiesta

**Attributo di qualità** · Sicurezza

**Priorità proposta** · P1

**Motivazione.** Una OPT interpellata deve poter consultare la richiesta che la riguarda. Le altre pratiche della OPT richiedente devono restare riservate.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
| --- | --- | --- | --- | --- | --- |
| Un utente autenticato con l’account organizzativo della OPT A. | Dopo aver consultato **R1**, l’utente cambia l’identificativo inviato al server per provare ad aprire **R2**. | PICUS è in normale operatività. La OPT B ha aperto entrambe le richieste. La OPT A è stata interpellata per R1 e non è coinvolta in R2. | La funzione di consultazione del dettaglio di una richiesta. | Il server verifica il permesso sulla singola richiesta e nega l’accesso a R2. | Con lo stesso account, R1 è consultabile. Il tentativo su R2 riceve `403` e la risposta contiene **zero dati di R2**. |

## Approccio di verifica

Preparare due richieste sintetiche e inviare le chiamate direttamente al server.

| Caso | Esito atteso |
| --- | --- |
| La OPT A apre R1, per cui è stata interpellata. | Il server restituisce il dettaglio di R1. |
| La OPT A prova ad aprire R2 cambiando l’identificativo. | `403` e zero dati di R2 restituiti. |
| La OPT B apre R2, che ha creato. | Il server restituisce il dettaglio di R2. |
| La OPT A prova ad aprire R1 con la sessione scaduta. | `401` e zero dati di R1 restituiti. |
