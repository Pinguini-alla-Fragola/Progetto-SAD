# QS-01B Annullamento non autorizzato di una richiesta confermata

**Attributo di qualità** · Sicurezza

**Priorità proposta** · P1

**Motivazione.** L’annullamento cambia lo stato di una richiesta già confermata. Il server deve verificare che l’account possa annullare **quella specifica richiesta**.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
| --- | --- | --- | --- | --- | --- |
| Un operatore esterno pagato dal Banco, autenticato con il proprio account *Operatore*. | L’operatore invia un comando di annullamento con un motivo valido per **R2**, assegnata a un collega. | PICUS è in normale operatività. Esiste un unico Banco, con un account di coordinamento distinto dagli account personali degli operatori. **R1** e **R2** sono *Confermate*. R1 è assegnata all’operatore che invia il comando e R2 al collega. | La funzione di annullamento di una richiesta confermata. | Il server verifica il permesso di annullamento su R2 e respinge il comando. | Il tentativo su R2 riceve `403`. R2 resta *Confermata* e i suoi dati non cambiano. Come controllo positivo, lo stesso operatore riesce ad annullare R1. |

## Approccio di verifica

Usare richieste sintetiche e inviare i comandi direttamente al server. Ogni prova parte da una richiesta *Confermata* e il comando include un motivo di annullamento. Dopo il comando, verificare lo stato registrato dal sistema.

| Caso | Esito atteso |
| --- | --- |
| Un operatore esterno pagato dal Banco annulla una richiesta assegnata a lui. | La richiesta passa ad *Annullata*. |
| Un operatore interno del Banco annulla una richiesta assegnata a lui. | La richiesta passa ad *Annullata*. |
| La OPT erogatrice assegnata annulla la richiesta, anche se per il servizio si avvale di un professionista esterno. | La richiesta passa ad *Annullata* tramite l’account della OPT. |
| L’account di coordinamento del Banco annulla una richiesta assegnata a un proprio operatore. | La richiesta passa ad *Annullata*. |
| Un operatore tenta di annullare la richiesta assegnata a un collega. | `403`. La richiesta resta *Confermata*. |
| Una OPT soltanto interpellata tenta di annullare la richiesta. | `403`. La richiesta resta *Confermata*. |
| Un utente autorizzato tenta l’annullamento con la sessione scaduta. | `401`. La richiesta resta *Confermata*. |
