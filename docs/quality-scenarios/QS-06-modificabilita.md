# QS-06 Modifica del catalogo senza rilascio software

**Attributo di qualità:** Modificabilità, limitatamente alla configurazione del catalogo.

**Priorità proposta:** P2.

**Motivazione:** Il Banco deve poter aggiornare le misure attraverso l'applicazione. Gestire il catalogo come configurazione permette queste modifiche senza coinvolgere uno sviluppatore. Lo scenario misura questa specifica capacità di adattamento del catalogo.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
|---|---|---|---|---|---|
| Un utente Banco autorizzato alla gestione del catalogo. | Aggiunge una nuova misura o corregge la descrizione di una misura esistente. | Applicazione completa in esecuzione, con il catalogo iniziale delle 12 misure e richieste e offerte già collegate ad alcune di esse. | Catalogo configurabile, interfaccia Banco e selezione della misura nelle richieste e nella dichiarazione dell'offerta. | Il Banco aggiorna il catalogo attraverso l'applicazione e le OPT vedono le nuove informazioni. Le richieste precedenti e le offerte già dichiarate mantengono il riferimento alla misura scelta e il suo significato. | Soglie proposte: operazione completata entro 5 minuti dall'utente Banco; dopo il salvataggio, le nuove informazioni sono visibili alla OPT al successivo aggiornamento della vista, entro 10 secondi dalla richiesta di aggiornamento. L'operazione avviene senza modificare il codice, ricompilare, riavviare il servizio o aggiornare manualmente il database. |

## Stato delle soglie

L'aggiornamento del catalogo senza interventi sul codice è previsto dal contesto; i tempi sono proposte del team. Il comportamento dettagliato sulle misure già usate, sulle offerte già dichiarate e sulla disattivazione resta da definire in US-D03. La misura riguarda la configurabilità del catalogo, con l'obiettivo di ridurre le modifiche necessarie al software.

## Approccio di verifica

Partire da un catalogo di prova, aggiungere una misura e modificare la descrizione di una esistente. Misurare il tempo necessario al Banco e verificare con una sessione OPT quando le informazioni aggiornate diventano visibili. Controllare una richiesta storica e un'offerta già dichiarata per verificare che i riferimenti restino validi e il significato della misura sia conservato.
