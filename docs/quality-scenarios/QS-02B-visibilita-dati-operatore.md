# QS-02B Visibilità dei dati dell’operatore nel dettaglio della richiesta

**Attributo di qualità** · Privacy

**Priorità proposta** · P1

**Motivazione.** La OPT richiedente ha bisogno dei recapiti del referente per organizzare un appuntamento in presenza. I dati amministrativi ed economici dell’operatore sono riservati.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
| --- | --- | --- | --- | --- | --- |
| Un utente autenticato con l’account della OPT richiedente. | L’utente apre il dettaglio di una richiesta *Confermata* che la OPT ha creato, relativa a un appuntamento in presenza. | PICUS è in normale operatività. Il referente dell’appuntamento è un operatore pagato dal Banco. La sua scheda trimestrale personale contiene dati anagrafici, contrattuali ed economici sintetici. | La risposta del server che contiene il dettaglio della richiesta visibile alla OPT. | Il server restituisce i dettagli dell’appuntamento, inclusi nome, cognome e telefono del referente. I dati riservati della scheda trimestrale restano esclusi dalla risposta. | Tra i dati relativi all’operatore, la risposta contiene solo **nome, cognome e telefono del referente**. I dettagli dell’appuntamento, come data, ora e luogo, restano visibili. |

## Approccio di verifica

Controllare le risposte dirette del server usando dati sintetici. Per le tre prove di accesso alla scheda trimestrale usare l’identificativo della scheda dello stesso operatore, per lo stesso trimestre, cambiando solo l’account.

| Caso | Esito atteso |
| --- | --- |
| La OPT richiedente consulta il dettaglio dell’appuntamento relativo alla richiesta che ha creato. | Il server restituisce i dettagli dell’appuntamento. Tra i dati dell’operatore, la risposta contiene solo nome, cognome e telefono del referente. |
| Con una sessione valida, la OPT richiedente chiede direttamente al server la scheda trimestrale personale del referente usando un identificativo valido. | Il server nega l’accesso e restituisce zero dati della scheda. |
| L’operatore indicato come referente consulta la propria scheda trimestrale personale. | Il server restituisce i dati della scheda. |
| L’account autorizzato del Banco consulta la scheda trimestrale personale dell’operatore indicato come referente dell’appuntamento. | Il server restituisce i dati della scheda. |
