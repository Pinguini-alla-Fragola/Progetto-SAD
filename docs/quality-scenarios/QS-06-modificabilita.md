# QS-06 Modifica del catalogo senza rilascio software

**Attributo di qualità:** Modificabilità, limitatamente alla configurazione del catalogo.

**Priorità proposta:** P2.

**Motivazione:** Il Banco deve poter aggiornare le misure direttamente nell'applicazione. Lo scenario verifica che possa farlo intervenendo sulla configurazione del catalogo, senza coinvolgere uno sviluppatore.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
|---|---|---|---|---|---|
| Un utente del Banco con il permesso di gestire il catalogo. | Aggiunge una misura o corregge la descrizione di una misura esistente. | L'applicazione completa è in esecuzione e il catalogo contiene le 12 misure iniziali. Ad alcune misure sono già collegate richieste e offerte. | Il catalogo configurabile, l'interfaccia del Banco e la scelta della misura nelle richieste e nella dichiarazione dell'offerta. | Il Banco aggiorna il catalogo dall'applicazione e le OPT vedono le nuove informazioni. Le richieste precedenti e le offerte già dichiarate continuano a riferirsi alla misura scelta, conservandone il significato. | La soglia proposta per l'utente del Banco è completare l'operazione entro 5 minuti. Dopo il salvataggio, la OPT vede le nuove informazioni la prima volta che aggiorna la schermata, entro 10 secondi dalla richiesta di aggiornamento. Per aggiornare il catalogo non occorre modificare il codice, ricompilare, riavviare il servizio o intervenire manualmente sul database. |

## Approccio di verifica

Usare un catalogo di prova per aggiungere una misura e cambiare la descrizione di una misura esistente. Misurare quanto tempo impiega l'utente del Banco e usare una sessione OPT per controllare quando compaiono le informazioni aggiornate. Verificare su una richiesta precedente e su un'offerta già dichiarata che il riferimento alla misura sia ancora valido e che la misura conservi lo stesso significato.
