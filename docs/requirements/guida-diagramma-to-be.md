# Lettura dei diagrammi del processo to be

Guida v0.3 del **03/11/2026**. Aggiorna la v0.2 del 25/10/2026 alle risposte del
cliente della seconda tornata e alle scelte del gruppo registrate il 03/11/2026. Il documento
Word v0.5 va riallineato. I diagrammi sono prodotti in Mermaid e incorporati in A3.
Sono una proposta del team, con ipotesi da validare e senza approvazioni del
committente presunte.

## Viste disponibili

| ID | Contenuto | File |
| --- | --- | --- |
| TOBE-01A | Validazione dei dati, ricerca dell'erogatore uno per volta e rifiuto | [Mermaid](diagrams/to-be/TOBE-01A-richieste-disponibilita.mmd), [PNG](diagrams/to-be/TOBE-01A-richieste-disponibilita.png), [SVG](diagrams/to-be/TOBE-01A-richieste-disponibilita.svg) |
| TOBE-01B | Accordo fra Banco ed erogatore, conferma del Banco e annullamento eccezionale | [Mermaid](diagrams/to-be/TOBE-01B-appuntamento-esito.mmd), [PNG](diagrams/to-be/TOBE-01B-appuntamento-esito.png), [SVG](diagrams/to-be/TOBE-01B-appuntamento-esito.svg) |
| TOBE-02 | Scheda trimestrale degli operatori del Banco, dati quantitativi della OPT e inserimento ministeriale manuale | [Mermaid](diagrams/to-be/TOBE-02-rendicontazione.mmd), [PNG](diagrams/to-be/TOBE-02-rendicontazione.png), [SVG](diagrams/to-be/TOBE-02-rendicontazione.svg) |

I diagrammi di sequenza mostrano chi scambia quali informazioni e in quale
ordine. TOBE-01A e TOBE-01B sono due viste consecutive dello stesso percorso.
TOBE-02 è un processo indipendente, che non parte dalla chiusura di una richiesta.

## Confine della richiesta e stati

**IP-01, da validare nella [domanda #29](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/29):**
la richiesta nasce **In programmazione**. Il flusso applicativo termina con
l'accordo confermato dal Banco, stato **Confermata** (nelle fonti precedenti e
nella #29 compare come Accettata), oppure con **Rifiutata** quando il Banco
conclude che non esiste una soluzione praticabile. Confermata attesta l'accordo,
non l'avvenuta erogazione.

Transizioni rappresentate: invio valido, In programmazione; In programmazione,
Confermata o Rifiutata per azione del Banco; Confermata, Annullata. Un singolo
rifiuto dell'erogatore o una controproposta lasciano la richiesta In programmazione.
In assenza di risposta la richiesta resta in attesa; non si assume un timeout.

**Annullata** richiede un evento eccezionale e un motivo obbligatorio. Dopo la
conferma i dettagli non sono modificabili e la ricerca non riparte da sola.
Lo segnala l'account legato all'erogatore assegnato:

| Erogatore assegnato | Chi registra l'annullamento |
| --- | --- |
| OPT erogatrice | Account della stessa OPT |
| Operatore esterno di una OPT | Account della OPT di appartenenza |
| Operatore interno o esterno pagato dal Banco | Account del Banco con ruolo di coordinamento |

Gli operatori hanno un account personale, ma non registrano l'annullamento:
nel diagramma agisce l'account della OPT o quello del Banco, non il singolo operatore.

F07 conserva l'alternativa **In programmazione, Completata, Annullata, Rifiutata**
e la verifica della prestazione svolta. Il gruppo ha scelto di non seguire
l'erogazione nel ciclo della richiesta; la scelta va validata nella #29.

## Ricerca dell'erogatore

Risposte del cliente: il Banco vede tutti gli erogatori compatibili con la misura e
ne interpella **uno per volta**, senza invii in parallelo. Il sistema può suggerire
l'ordine con i criteri riportati nel diagramma; scelta e assegnazione restano al
Banco. Servono la distanza fra comuni (stesso comune, confinanti) e il conteggio
degli appuntamenti già assegnati.

Il gruppo ha scelto di non mostrare una disponibilità preventiva con giorni o
fasce orarie. Il cliente aveva indicato come non facoltativo il dato su quando la
OPT può erogare una misura: da chiarire con il PO.

## Appuntamento e responsabilità

**IP-02, collegata a Q-09, scelta del gruppo registrata il 03/11/2026 da validare con il PO:**
Banco ed erogatore concordano i dettagli; il Banco rappresenta le esigenze
dell'assistito e, dopo l'accordo, è il solo a registrarli e a confermare nell'app.
Non sono previsti una seconda approvazione dell'erogatore né una decisione della
OPT richiedente nell'app. Se il committente attribuisce ad altri la proposta o la
conferma, modificare TOBE-01A, TOBE-01B, US-D07, US-D08 e la relativa dimostrazione.

Per la presenza si conservano data, ora, luogo, nome e cognome del referente e
telefono; per la distanza data, ora e link. La OPT informa la persona fuori
dall'applicazione. PICUS non acquisisce dati identificativi degli assistiti o
contenuti della prestazione.

## Rendicontazione

Compilano la scheda trimestrale, con account personale, gli operatori interni del
Banco e gli esterni pagati dal Banco. Non la compilano volontari, personale e
operatori esterni messi a disposizione dalle OPT. La scheda include anche attività
non richieste tramite l'applicazione. Gli errori formali tornano alla compilazione;
una scheda valida viene registrata e resa disponibile al Banco, che inserisce
manualmente i dati nel portale ministeriale; nessuna chiamata API è rappresentata.

La OPT inserisce a parte i dati quantitativi dell'organizzazione, che comprendono
l'attività di volontari e personale. Il gruppo ha scelto le viste trimestrale e
cumulativa, ricavate dagli stessi dati per periodo. Le categorie possono
sovrapporsi e non vanno sommate come gruppi distinti. Approvazione obbligatoria
della scheda e formato degli export non sono introdotti come requisiti.

## Punti non definiti dalle fonti

- Canale con cui Banco ed erogatore concordano i dettagli: nell'app o fuori.
- Cosa si intende per evento eccezionale che giustifica l'annullamento e in quale
  forma si registra il motivo: le fonti lo richiedono, ma non lo definiscono.
- Se la OPT richiedente viene informata dell'annullamento e se vede il motivo.
- Motivo del rifiuto della richiesta: non richiesto nelle fonti, non rappresentato.
- Conteggio dell'attività degli esterni delle OPT nei dati quantitativi, scadenze,
  correzioni e controlli di coerenza (Q-06 e Q-13).
- Chi consulta le viste trimestrale e cumulativa dei dati delle OPT: il diagramma le
  mostra al Banco, che raccoglie il monitoraggio, ma le fonti non lo dicono.

## Aggiornamento dopo i chiarimenti

Restano da riallineare al nuovo ciclo della richiesta: [registro dei chiarimenti](open-questions.md)
(Q-03, Q-04, Q-08, Q-09, Q-10, Q-11, Q-13, IP-01, IP-02, IP-06),
[proposta di MVP](proposta-mvp.md), A3, A6 e A7 del Word, US-D05–US-D09 e la #29.
Alla risposta a #29 aggiornare insieme A3, US-D09 e TOBE-01B.
Mantenere versioni, fonti, schema dei campi e criteri coerenti con i diagrammi.

Riferimenti: [scenari di qualità](../quality-scenarios/README.md).
Fonti F02 §§2–5 e appendice B, F06 e F07; F09 per le domande pubbliche; risposte
del cliente, seconda tornata.
