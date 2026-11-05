# QS-05 Ripristino del servizio dopo un fermo

**Attributo di qualità:** Disponibilità.

**Priorità proposta:** P2.

**Motivazione:** Dopo un'interruzione, OPT, Banco e operatori devono poter riprendere il lavoro ritrovando i dati già salvati. La tolleranza ai fermi descritta nelle fonti consente di proporre un ripristino nell'arco di alcune ore.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
|---|---|---|---|---|---|
| Un guasto del processo applicativo o dell'istanza che lo esegue. | PICUS diventa temporaneamente indisponibile dopo che alcuni dati sono stati salvati. | Fermo del servizio con database integro e dati salvati ancora disponibili. Perdita del database e disastro dell'intera infrastruttura richiedono una politica di backup e uno scenario separati. | Applicazione, collegamento al database e procedura di ripristino. | Il servizio viene ripristinato seguendo una procedura documentata. Gli utenti ritrovano i dati già salvati e gli invii non riusciti non vengono mostrati come completati. | Obiettivo proposto: ripristino entro 4 ore dal fermo, con il 100% dei dati già salvati ancora disponibile nel caso di database integro. Il tempo termina quando riescono tutte e tre le operazioni di prova: accesso, lettura e nuovo invio. |

## Approccio di verifica

In un ambiente di prova interrompere il solo servizio, seguire la procedura documentata di ripristino e confrontare i dati prima e dopo. Verificare accesso, lettura e nuovo invio, oltre all'assenza di false conferme per gli invii non riusciti. Registrare tempi, configurazione e limiti della procedura.
