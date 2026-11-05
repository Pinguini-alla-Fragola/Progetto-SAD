# QS-05 Ripristino del servizio dopo un fermo

**Attributo di qualità:** Disponibilità.

**Priorità proposta:** P2.

**Motivazione:** Dopo un'interruzione, OPT, Banco e operatori devono poter riprendere il lavoro dai dati che avevano già salvato. Le fonti indicano che è possibile tollerare un fermo del servizio, da cui la proposta di ripristinarlo entro poche ore.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
|---|---|---|---|---|---|
| Si guasta il processo applicativo o l'istanza su cui è in esecuzione. | PICUS diventa temporaneamente indisponibile dopo aver salvato alcuni dati. | Il servizio è fermo, ma il database è integro e i dati salvati sono ancora disponibili. La perdita del database o un guasto che coinvolge l'intera infrastruttura richiedono una politica di backup e uno scenario separato. | L'applicazione, il collegamento al database e la procedura di ripristino. | Chi interviene segue la procedura documentata per riattivare il servizio. Gli utenti ritrovano i dati già salvati e l'applicazione indica come completati solo gli invii riusciti. | L'obiettivo proposto è ripristinare il servizio entro 4 ore dal fermo. Con il database integro, il 100% dei dati già salvati deve essere ancora disponibile. Il tempo di ripristino si ferma quando tutte e tre le operazioni di prova vanno a buon fine, cioè quando si riesce ad accedere, leggere i dati ed effettuare un nuovo invio. |

## Approccio di verifica

In un ambiente di prova, interrompere solo il servizio e seguire la procedura documentata per riattivarlo. Confrontare i dati prima e dopo il ripristino. Provare ad accedere, leggere i dati ed effettuare un nuovo invio. Controllare anche che l'applicazione non confermi come riusciti gli invii falliti. Annotare i tempi, la configurazione e i limiti della procedura.
