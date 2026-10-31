# Requisiti e modelli di analisi

Questa cartella raccoglie stakeholder, contesto, processi as-is e to-be,
User Story, criteri di accettazione, ipotesi e domande aperte.
L'analisi già redatta è nel
[documento di progetto Word](../Documentazione_SAD_2026.docx).
Il processo as-is è ricostruito dalle fonti in due diagrammi Mermaid.
Il to-be è prodotto in tre viste Mermaid e descritto nella
[guida ai diagrammi](guida-diagramma-to-be.md), con IP-01/IP-02 da validare.
Sono disponibili anche il [registro dei chiarimenti](open-questions.md) e la
[proposta di MVP](proposta-mvp.md), allineati al Word v0.5.

Il Product Backlog resta la fonte operativa per il lavoro pianificato;
qui si conservano riepiloghi stabili e modelli di supporto. Distinguere sempre
fonti, proposte, ipotesi e requisiti concordati con relativa evidenza.

## Dominio e stakeholder

- Il Banco coordina richieste, erogatori, disponibilità e appuntamenti.
- Le OPT possono richiedere e offrire misure e mantengono i contatti con gli assistiti.
- Gli operatori PICUS e i professionisti esterni erogano misure, confermano
  appuntamenti e presentano le proprie schede trimestrali.
- La gestione interna del personale delle OPT resta in capo alle organizzazioni.
- Gli assistiti sono stakeholder senza account o dati identificativi nel sistema.

Il catalogo delle misure è aggiornabile dal Banco. Il monitoraggio trimestrale
è distinto dalle singole richieste e può includere attività senza richiesta PICUS.
I dati degli assistiti sono conteggi aggregati; le schede degli operatori
contengono dati personali e richiedono protezione. Conservazione e cancellazione
restano da chiarire con gli interlocutori competenti.

## Modelli disponibili

- [Context diagram modificabile](<diagrams/Context Diagram.drawio>)
- [Esportazione del context diagram](<diagrams/Context Diagram.drawio.png>)
- [Schema iniziale di contesto, non diagramma as-is](diagrams/DiagrammaIniziale_PICUS.png)
- As-is delle richieste: [Mermaid](diagrams/ASIS-01-richieste.mmd),
  [PNG](diagrams/ASIS-01-richieste.png), [SVG](diagrams/ASIS-01-richieste.svg)
- As-is della rendicontazione: [Mermaid](diagrams/ASIS-01-rendicontazione.mmd),
  [PNG](diagrams/ASIS-01-rendicontazione.png), [SVG](diagrams/ASIS-01-rendicontazione.svg)
- To-be della richiesta: [Mermaid](diagrams/TOBE-01A-richieste-disponibilita.mmd), [PNG](diagrams/TOBE-01A-richieste-disponibilita.png), [SVG](diagrams/TOBE-01A-richieste-disponibilita.svg)
- To-be dell'appuntamento: [Mermaid](diagrams/TOBE-01B-appuntamento-esito.mmd), [PNG](diagrams/TOBE-01B-appuntamento-esito.png), [SVG](diagrams/TOBE-01B-appuntamento-esito.svg)
- To-be della rendicontazione: [Mermaid](diagrams/TOBE-02-rendicontazione.mmd), [PNG](diagrams/TOBE-02-rendicontazione.png), [SVG](diagrams/TOBE-02-rendicontazione.svg)
- [Verifica dopo il confronto con lo stakeholder](../reviews/2026-10-21-coerenza-stakeholder-context.md)

CTX-01 è aggiornato alla **versione 0.3 del 25/10/2026**, incorporata anche nel
Word e nel PDF. La revisione del gruppo resta da registrare. Il diagramma
qualifica gli operatori, indica il CSV come opzionale, uniforma la conferma di
ricezione e include la creazione/revoca degli account degli operatori.

ASIS-01A/B sono alla **versione 0.1 del 25/10/2026**, con sorgenti Mermaid
ed esportazioni incorporate nella versione 0.5 del Word. I canali puntuali
di rendicontazione e di aggiornamento dell'esito non sono documentati dalle fonti:
restano visibili come punti da precisare, insieme alla revisione del gruppo.

Per ogni modello mantenere identificativo, versione, data, fonte modificabile
e collegamenti alle issue pertinenti. Non registrare come completati o validati
modelli e decisioni ancora in discussione.

TOBE-01A/B e TOBE-02 sono alla **versione 0.1 del 25/10/2026**. Accettata
chiude il coordinamento nella proposta IP-01, dopo conferma dell'appuntamento;
Completata e le regole di Annullata restano da riconciliare con F07 nella #29.
Il monitoraggio trimestrale è un processo indipendente. Non è attestata una
validazione del committente o una revisione del gruppo già svolta.
