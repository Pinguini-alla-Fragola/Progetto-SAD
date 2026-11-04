# Lettura dei diagrammi del processo to be

Guida v0.4 del **04/11/2026**, dopo la revisione delle epiche e delle storie. Le [regole funzionali](regole-funzionali.md) distinguono chiarimenti e scelte del gruppo; i [quesiti residui](open-questions.md) rimangono tracciati. Il to-be descrive il prodotto desiderato, non software già implementato.

## Viste e corrispondenza con il backlog

| ID | Contenuto | Sorgenti ed esportazioni |
| --- | --- | --- |
| TOBE-01A v0.3 | Invio, ricerca sequenziale, disponibilità e rifiuto con Nessun erogatore disponibile | [Mermaid](diagrams/to-be/TOBE-01A-richieste-disponibilita.mmd), [SVG](diagrams/to-be/TOBE-01A-richieste-disponibilita.svg), [PNG](diagrams/to-be/TOBE-01A-richieste-disponibilita.png) |
| TOBE-01B v0.2 | Accordo, conferma del solo Banco e annullamento eccezionale | [Mermaid](diagrams/to-be/TOBE-01B-appuntamento-esito.mmd), [SVG](diagrams/to-be/TOBE-01B-appuntamento-esito.svg), [PNG](diagrams/to-be/TOBE-01B-appuntamento-esito.png) |
| TOBE-02 v0.3 | Schede personali e registrazione/consultazione separata dei dati OPT per trimestre e periodo | [Mermaid](diagrams/to-be/TOBE-02-rendicontazione.mmd), [SVG](diagrams/to-be/TOBE-02-rendicontazione.svg), [PNG](diagrams/to-be/TOBE-02-rendicontazione.png) |

TOBE-01A/B sono due viste dello stesso percorso: #48/#49/#51 e #52/#54. #52 assorbe #53; mantenere quest’ultima come riferimento storico dopo l’aggiornamento GitHub. TOBE-02 è indipendente dalle richieste: #58/#57 per schede personali, #43 e nuova consultazione sotto #19 per le OPT.

## Richiesta e ricerca

Quattro stati da #29: In programmazione, Confermata, Rifiutata e Annullata. Confermata attesta l’accordo, non la prestazione. Un assenso del candidato e un singolo rifiuto non producono da soli gli esiti finali. #36 richiede l’indicazione Nessun erogatore disponibile nel rifiuto finale, senza testo libero obbligatorio.

Modulo minimo scelto: misura, persona/nucleo e comune; il sistema gestisce ID, arrivo e titolare. Coda cronologica e candidati ordinati per misura, comune/limitrofi, tipo e minor carico; remoto PICUS quando compatibile. Scelta Banco e un contatto per volta; carico/spareggi da precisare e Q-19 per mancata risposta. Non si assume timeout.

Il gruppo non mostra disponibilità preventive. La seconda tornata chiedeva anche quando la OPT può erogare: Q-03 mantiene esplicita la divergenza e il confronto col PO/committente sulla semplificazione o sul dato minimo. Non è un obbligo di costruire un calendario a slot.

## Accordo e annullamento

Banco ed erogatore concordano i dettagli; solo il Banco registra e conferma nell’app, senza seconda approvazione applicativa dell’erogatore. Conferma, stato e visibilità alla OPT sono un unico risultato. Presenza: data, ora, luogo, nome/cognome e telefono del referente. Distanza: data, ora e link. La OPT contatta l’assistito fuori dall’app.

Dopo conferma i dettagli sono immutabili. Annullamento solo da Confermata per evento eccezionale con motivo: OPT assegnata, OPT di appartenenza dell’esterno assegnato o Banco di coordinamento per i propri operatori. Non il singolo Operatore né la sola OPT richiedente. Nessuna ricerca riaperta automaticamente. Questa è la matrice di progetto comune a #28/#54 e QS-01.

La domanda #38 riguarda canale e controproposte prima della conferma; Q-04 conserva esempi di evento, avvisi e visibilità del motivo. Non sono nuovamente aperti il soggetto della conferma e la matrice.

## Monitoraggio

Scheda personale solo per interni/esterni pagati dal Banco; esterni OPT esclusi (#45). Dati personali e conteggi aggregati protetti; categorie sovrapponibili e inserimento ministeriale manuale, nessuna approvazione obbligatoria.

La OPT registra dati per anno/trimestre e misura; il Banco consulta un trimestre oppure il cumulativo dei trimestri selezionati. Le due viste derivano dagli stessi dati senza doppio inserimento. Dati mancanti distinti da zero, conteggi dichiarati distinti da persone uniche. Tracciati, correzioni, invii ripetuti e attività degli esterni OPT in #41/Q-13; la sola scelta del trimestre non introduce una scadenza di invio.

## Aggiornamento degli artefatti

Registro, MVP, Word/PDF e pacchetto delle issue sono riallineati alla revisione. Le relazioni e gli stati GitHub richiedono l’applicazione del pacchetto: nuova consultazione OPT, #52 ampliata, #53 conservata come assorbita. L’as-is resta una ricostruzione storica e non viene modificato per imitare il to-be. Soglie e prove dei [sei QS](../quality-scenarios/README.md) non sono risultati già misurati.
