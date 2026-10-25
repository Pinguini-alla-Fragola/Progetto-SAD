# Proposta di MVP per il coordinamento

Versione 0.1 del 25/10/2026. Proposta da discutere con PO e team; nessuna implementazione o approvazione presunta.

Il primo percorso termina con un appuntamento confermato (**Accettata**) o con l’impossibilità di trovare una soluzione (**Rifiutata**). È IP-01, proposta dell’Epic #20 da validare nella [domanda #29](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/29). F07 conserva l’alternativa con Completata e Annullata.

## Sette tagli funzionali

| Storia o taglio | Contributo | Prerequisiti e limiti |
| --- | --- | --- |
| MVP-01 / US-25 | Accesso con email e password ai ruoli Banco, OPT e Operatore. | Taglio proposto della storia #25: il recupero password è separabile e rinviato. Completare il solo login non equivale a chiudere tutta la issue. |
| MVP-02 / US-28 | Autorizzazione per ruolo e titolare su tutte le risorse del percorso. | QS-01 e QS-02; controlli sul server con casi positivi e negativi. |
| MVP-03 / US-D05 | Invio di una richiesta valida con ID, data e In programmazione. | IAM, catalogo precaricato e schema IP-03. Errori e campi non ammessi non creano record. |
| MVP-04 / US-D06 | Consultazione della coda e scelta manuale dell’erogatore con inoltro della richiesta di disponibilità. | Offerta precaricata; nessuna selezione automatica per vicinanza o priorità presunta. |
| MVP-05 / US-D07 | Risposta del destinatario e nuova ricerca dopo un rifiuto. | Il rifiuto del singolo erogatore è distinto dal rifiuto finale della richiesta. |
| MVP-06 / US-D08 | Proposta, conferma e pubblicazione dei dettagli dell’appuntamento. | IP-02 / Q-09; in presenza data, ora, luogo e referente; a distanza data, ora e link. |
| MVP-07 / US-D09 | Chiusura del coordinamento come Accettata o Rifiutata e consultazione dell’esito da parte della OPT. | IP-01 / #29: proposta da validare. Completata e annullamenti non sono tacitamente definiti. |

## Prerequisiti e funzioni rinviate

Account, catalogo e offerta sintetici sono predisposti per la dimostrazione. Non attestano amministrazione completa degli account o dell’offerta.

| Funzioni rinviate | Motivazione |
| --- | --- |
| Gestione completa utenze, revoche, CSV e recupero password | Il primo percorso usa account di prova predisposti. Creazione e revoca restano capacità del prodotto, da pianificare prima dell’uso operativo; il CSV rimane opzionale. |
| Modifica del catalogo e dichiarazione dell’offerta tramite interfaccia | Catalogo e offerta sono dati iniziali della dimostrazione; US-D03 e US-D04 vengono introdotte in un incremento successivo. |
| Schede trimestrali, aggregati ed export | Processo autonomo rappresentato da TOBE-02, successivo al primo MVP del coordinamento. Non serve per chiudere una richiesta. |
| Comunicazioni istituzionali e conferma di ricezione | Epic #22 separata dal percorso di richiesta; Q-08 / #21 da chiarire. |
| Esito della prestazione e annullamenti | F07 rimane la fonte dell’alternativa Completata/Annullata. L’esclusione è una proposta temporanea del team, subordinata a #29; una risposta diversa cambia il taglio MVP. |
| Geolocalizzazione automatica, urgenze, timeout e piattaforma video interna | Scelta dell’erogatore manuale, coda cronologica e link esterno alla videochiamata sono sufficienti alla proposta. Non vengono introdotti requisiti non concordati. |

## Percorso dimostrativo previsto

| Passo | Azione | Riscontro |
| --- | --- | --- |
| 1 | OPT A accede e invia una richiesta sintetica valida. | Ottiene ID, data e In programmazione; il Banco ritrova la stessa richiesta in ordine cronologico. |
| 2 | Il Banco consulta un primo erogatore, che rifiuta. | Il rifiuto è visibile al Banco; la richiesta originaria resta In programmazione. |
| 3 | Il Banco interpella un secondo erogatore, che conferma disponibilità. | La risposta non chiude ancora la richiesta come Accettata. |
| 4 | Banco ed erogatore concordano e confermano i dettagli secondo IP-02. | La OPT vede solo l’appuntamento della propria richiesta; lo stato diventa Accettata dopo pubblicazione dei dati confermati. |
| 5 | In una seconda richiesta nessun erogatore è disponibile. | Il Banco registra Rifiutata e la OPT ne vede il motivo; nessun contatto con l’assistito avviene in PICUS. |
| 6 | OPT B tenta di consultare la richiesta di A; si invia anche un modulo con campi non ammessi. | Accesso estraneo negato e nessuna modifica; il payload non ammesso non crea record e non viene riprodotto nei log. |

QS-01/QS-02 verificano il percorso autorizzativo e i dati; QS-04 propone la prova mobile. Le altre verifiche dipendono dai moduli disponibili. In S1 si presenta il processo con i diagrammi; la demo software e gli esiti delle prove appartengono alla costruzione.
