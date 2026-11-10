# Lettura dei diagrammi del processo to be

Guida v0.5 del **07/11/2026**, allineata al contesto del 5 novembre. Le [regole funzionali](regole-funzionali.md) distinguono chiarimenti e scelte del gruppo; i [quesiti residui](open-questions.md) rimangono tracciati. Il to-be descrive il prodotto desiderato, non software già implementato.

## Viste e corrispondenza con il backlog

| ID | Contenuto | Sorgenti ed esportazioni |
| --- | --- | --- |
| TOBE-01A v0.5 | Invio, ricerca sequenziale, disponibilità e rifiuto con Nessun erogatore disponibile | [Mermaid](diagrams/to-be/TOBE-01A-richieste-disponibilita.mmd), [SVG](diagrams/to-be/TOBE-01A-richieste-disponibilita.svg), [PNG](diagrams/to-be/TOBE-01A-richieste-disponibilita.png) |
| TOBE-01B v0.3 | Accordo, conferma del solo Banco e annullamento eccezionale | [Mermaid](diagrams/to-be/TOBE-01B-appuntamento-esito.mmd), [SVG](diagrams/to-be/TOBE-01B-appuntamento-esito.svg), [PNG](diagrams/to-be/TOBE-01B-appuntamento-esito.png) |
| TOBE-02 v0.4 | Schede personali e registrazione/consultazione separata dei dati OPT per trimestre e periodo | [Mermaid](diagrams/to-be/TOBE-02-rendicontazione.mmd), [SVG](diagrams/to-be/TOBE-02-rendicontazione.svg), [PNG](diagrams/to-be/TOBE-02-rendicontazione.png) |

TOBE-01A/B sono due viste dello stesso percorso, #48/#49/#51 e #52/#54. #52 assorbe la #53 chiusa. TOBE-02 è indipendente dalle richieste, con #58/#57 per schede personali e #43/#69 per i conteggi OPT.

## Richiesta e ricerca

Quattro stati da #29: In programmazione, Confermata, Rifiutata e Annullata. Confermata attesta l’accordo, non la prestazione. Un assenso del candidato e un singolo rifiuto non producono da soli gli esiti finali. #36 richiede l’indicazione Nessun erogatore disponibile nel rifiuto finale, senza testo libero obbligatorio.

Modulo con misura, persona/nucleo e comune obbligatori e `description` facoltativa senza identificativi degli assistiti. Il sistema gestisce ID, arrivo e titolare. Sono compatibili le OPT e gli interni/esterni pagati dal Banco che offrono la misura. Una OPT compare come organizzazione anche se eroga tramite un professionista. Stesso comune, poi limitrofi. A pari prossimità precedono gli operatori del Banco. Il Banco sceglie e contatta un erogatore per volta sul relativo account. #66/Q-19 riguarda il contatto senza risposta.

La disponibilità concreta si verifica per richiesta. Il calendario facoltativo per misura è un’estensione (#73). Q-03 conserva la decisione sul dato temporale minimo richiesto nella seconda tornata.

## Accordo e annullamento

Banco ed erogatore concordano i dettagli; solo il Banco registra e conferma nell’app, senza seconda approvazione applicativa dell’erogatore. Conferma, stato e visibilità alla OPT sono un unico risultato. Presenza: data, ora, luogo, nome/cognome e telefono del referente. Distanza: data, ora e link. La OPT contatta l’assistito fuori dall’app.

Dopo conferma i dettagli sono immutabili. La OPT assegnata, l’operatore del Banco assegnato o il Banco di coordinamento per i propri operatori possono annullare con motivo. La pratica passa ad Annullata e la ricerca resta chiusa. [Matrice comune](regole-funzionali.md#matrice-di-annullamento) per #28/#54 e [QS-01B](../quality-scenarios/QS-01B-annullamento-richieste.md).

Il modulo di contatto è il flusso base. #38/Q-09 dettaglia scambio di proposte, dati e vincoli. Q-04 conserva esempi di evento, avvisi e visibilità del motivo.

## Monitoraggio

Interni ed esterni pagati dal Banco compilano ciascuno la propria scheda. La OPT usa il proprio account per i conteggi organizzativi. Il Banco protegge i dati personali e inserisce manualmente i dati nel portale ministeriale.

La OPT registra dati per anno/trimestre e misura (#43). Il Banco consulta trimestre e cumulativo derivati dagli stessi dati (#69). #41/Q-13 riguarda tracciati, correzioni e inclusione/attribuzione delle attività svolte tramite professionisti esterni.

## Aggiornamento degli artefatti

L’as-is resta una ricostruzione storica. Le prove nel [catalogo degli scenari](../quality-scenarios/README.md) sono pianificate.
