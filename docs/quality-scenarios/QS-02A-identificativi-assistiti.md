# QS-02A Esclusione degli identificativi degli assistiti dalle richieste

**Attributo di qualità** · Privacy

**Priorità proposta** · P1

**Motivazione.** Per chiedere una misura al Banco bastano la misura selezionata dal catalogo, l’indicazione *persona o nucleo* e il comune. Il coordinamento può iniziare senza raccogliere l’identità dell’assistito.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
| --- | --- | --- | --- | --- | --- |
| Un utente autenticato con l’account organizzativo di una OPT. | L’utente invia direttamente al server una richiesta altrimenti valida, aggiungendo il campo `codiceFiscale` con un valore sintetico. | PICUS è in normale operatività. La misura selezionata esiste nel catalogo gestito dal Banco. Anche il comune e l’indicazione *persona o nucleo* sono validi. | La funzione di invio della richiesta e la validazione dei campi accettati dal server. | Il server rileva il campo extra e respinge l’intero invio, senza creare la richiesta. | Con il campo extra vengono create **zero richieste**. Il valore sintetico compare **zero volte** nella risposta, nei dati salvati, nei log e nelle eventuali esportazioni. L’invio di controllo con i soli campi ammessi riesce. |

## Approccio di verifica

Inviare le richieste direttamente al server con dati sintetici. Esaminare il modello della richiesta per verificare che non preveda campi identificativi dell’assistito e controllare che i tracciati di monitoraggio rappresentino gli assistiti tramite conteggi aggregati.

| Caso | Esito atteso |
| --- | --- |
| La OPT invia misura, comune e indicazione *persona o nucleo*. | Il server crea la richiesta. |
| La OPT aggiunge `codiceFiscale` come campo extra a un invio altrimenti valido. | Il server respinge l’intero invio e crea **zero richieste**. Il valore sintetico è assente dalla risposta di errore, dai dati salvati, dai log e dalle eventuali esportazioni. |
| La OPT ripete la prova aggiungendo `nomeAssistito` come campo extra. | Il server respinge l’intero invio e crea **zero richieste**. Il valore sintetico è assente dalla risposta di errore, dai dati salvati, dai log e dalle eventuali esportazioni. |
