# Lettura dei diagrammi del processo to be

Guida v0.2 del **25/10/2026**, allineata al documento Word **v0.5**.
I diagrammi sono prodotti in Mermaid e incorporati in A3. Sono una proposta
del team, con ipotesi da validare e senza approvazioni del committente presunte.

## Viste disponibili

| ID | Contenuto | File |
| --- | --- | --- |
| TOBE-01A | Invio valido, coda, ricerca manuale e risposta dell'erogatore | [Mermaid](diagrams/to-be/TOBE-01A-richieste-disponibilita.mmd), [PNG](diagrams/to-be/TOBE-01A-richieste-disponibilita.png), [SVG](diagrams/to-be/TOBE-01A-richieste-disponibilita.svg) |
| TOBE-01B | Conferma dei dettagli e chiusura del coordinamento | [Mermaid](diagrams/to-be/TOBE-01B-appuntamento-esito.mmd), [PNG](diagrams/to-be/TOBE-01B-appuntamento-esito.png), [SVG](diagrams/to-be/TOBE-01B-appuntamento-esito.svg) |
| TOBE-02 | Scheda trimestrale e inserimento ministeriale manuale | [Mermaid](diagrams/to-be/TOBE-02-rendicontazione.mmd), [PNG](diagrams/to-be/TOBE-02-rendicontazione.png), [SVG](diagrams/to-be/TOBE-02-rendicontazione.svg) |

I diagrammi di sequenza mostrano chi scambia quali informazioni e in quale
ordine. TOBE-01A e TOBE-01B sono due viste consecutive dello stesso percorso.
TOBE-02 è un processo indipendente, che non parte dalla chiusura di una richiesta.

## Proposta sul confine della richiesta

**IP-01, da validare nella [domanda #29](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/29):**
il flusso applicativo termina con un appuntamento confermato, stato proposto
**Accettata**, oppure con l'impossibilità di trovare una soluzione, **Rifiutata**.
Una disponibilità positiva non equivale ancora all'accettazione dell'appuntamento.
Un singolo rifiuto dell'erogatore non rifiuta automaticamente l'intera richiesta.
In assenza di risposta la richiesta resta in attesa; non si assume un timeout.

F07 conserva l'alternativa **In programmazione, Completata, Annullata, Rifiutata**
e la conferma del Banco sulla prestazione svolta. La proposta qui rappresentata
non cancella tale evidenza: chiede di validare se il software debba seguire anche
quell'esito. Le regole di annullamento restano da definire in entrambi i modelli.

## Appuntamento e responsabilità

**IP-02, collegata a Q-09:** il Banco registra una proposta di dettagli concordati
e li pubblica alla OPT dopo la conferma dell'erogatore. Non si presume che possa
fissare autonomamente l'agenda. Se il committente attribuisce ad altri la scelta
o l'inserimento, modificare TOBE-01B, US-D08 e la relativa dimostrazione.

Per la presenza si conservano data, ora, luogo, referente e recapito; per la
distanza data, ora e link. La OPT informa la persona fuori dall'applicazione.
PICUS non acquisisce dati identificativi degli assistiti o contenuti della prestazione.

## Rendicontazione

L'operatore compila la propria scheda anche per attività senza ticket PICUS.
Gli errori formali tornano alla compilazione; una scheda valida viene registrata
e resa disponibile al Banco. Le categorie aggregate possono sovrapporsi e non
devono essere sommate arbitrariamente al totale. Il Banco inserisce manualmente
i dati nel portale ministeriale; nessuna chiamata API è rappresentata.

Il personale interno delle OPT non viene censito individualmente. Il perimetro
degli operatori con account e scheda è da consolidare in Q-11. Approvazione
obbligatoria della scheda e formato degli export non sono introdotti come requisiti.

## Aggiornamento dopo i chiarimenti

Alla risposta a #29 aggiornare insieme A3, US-D09, A6, A7 e TOBE-01B.
Una risposta sulle responsabilità dell'appuntamento interessa anche US-D08.
Mantenere versioni, fonti, schema dei campi e criteri coerenti con i diagrammi.

Riferimenti: [registro dei chiarimenti](open-questions.md),
[proposta di MVP](proposta-mvp.md),
[scenari di qualità](../quality-scenarios/README.md).
Fonti F02 §§2–5 e appendice B, F06 e F07; F09 per le domande pubbliche.
