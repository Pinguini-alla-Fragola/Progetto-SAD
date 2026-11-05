# QS-01C Revoca di un account con sessione già aperta

**Attributo di qualità** · Sicurezza

**Priorità proposta** · P1

**Motivazione.** Il Banco deve poter interrompere l’accesso a PICUS anche quando l’account da revocare è già in uso.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
| --- | --- | --- | --- | --- | --- |
| Un utente del Banco autorizzato a revocare gli account. | Il Banco revoca l’account della **OPT A** mentre un referente lo sta usando. Dopo la conferma della revoca, il referente tenta di continuare a usare PICUS con la sessione già aperta. | PICUS è in normale operatività. Prima della revoca, la sessione della OPT A è valida e consente di consultare e inviare richieste. Il Banco opera da una sessione separata. | La gestione delle sessioni e i controlli di accesso del server. | PICUS registra l’account come *Revocato* e invalida la sessione della OPT A. Il server rifiuta le operazioni successive che usano quella sessione. | **Dalla prima operazione successiva alla conferma della revoca**, le richieste inviate con la sessione precedente ricevono `401`. I tentativi di consultazione restituiscono **zero dati protetti** e i tentativi di invio creano **zero nuove richieste**. |

## Approccio di verifica

Usare account e richieste sintetici. Verificare che la sessione della OPT A funzioni prima della revoca. Dopo che il Banco ha confermato la revoca, inviare le chiamate direttamente al server riutilizzando la stessa sessione.

| Caso | Esito atteso |
| --- | --- |
| Prima della revoca, la OPT A consulta una richiesta che ha creato. | Il server restituisce il dettaglio della richiesta. |
| Dopo la revoca, la OPT A tenta di consultare quella richiesta con la sessione già aperta. | `401` e zero dati della richiesta restituiti. |
| Dopo la revoca, la OPT A tenta di inviare una nuova richiesta valida con la stessa sessione. | `401` e zero nuove richieste create. |
| La OPT A prova ad accedere di nuovo con le proprie credenziali. | Il server rifiuta l’accesso e non apre una nuova sessione. |
