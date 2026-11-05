# Requisiti e modelli di analisi

Analisi e modelli PICUS, allineati alla revisione del **04/11/2026** e al Word/PDF v0.7. Le [regole funzionali](regole-funzionali.md) distinguono scelte del gruppo, risposte acquisite e residui; il [registro](open-questions.md) conserva i quesiti realmente aperti.

- [Revisione del backlog](revisione-backlog-2026-11-04.md)
- [MVP proposto](proposta-mvp.md): sei Story dopo l’accorpamento #52/#53, con la porzione pertinente del vincolo #28
- [Regole comuni account e credenziali](regole-accessi.md)
- [Tracciati di monitoraggio in bozza](tracciati-monitoraggio.md)
- [Guida dei diagrammi to-be](guida-diagramma-to-be.md)
- Word di progetto nell’area OneDrive condivisa; [esportazione PDF](../Documentazione_SAD_2026.pdf).

## Soggetti e confini

Banco di coordinamento distinto dagli operatori interni. Account unico OPT e personali dei tre tipi Operatore; gli esterni OPT non accedono alla rendicontazione. Volontari e personale proprio OPT non censiti individualmente. Nessun account o identificativo assistiti. La OPT mantiene il contatto con gli assistiti; il Banco conferma soltanto l’accordo e inserisce manualmente nel portale ministeriale.

## Diagrammi

- Contesto: [drawio](<diagrams/contesto/Context Diagram.drawio>), [PNG](<diagrams/contesto/Context Diagram.drawio.png>); sottotipologie e permessi definiti nelle regole funzionali.
- Schema iniziale: [PNG](diagrams/contesto/DiagrammaIniziale_PICUS.png), bozza di contesto e non processo as-is.
- ASIS-01A: [Mermaid](diagrams/ASIS-01-richieste.mmd), [SVG](diagrams/ASIS-01-richieste.svg), [PNG](diagrams/ASIS-01-richieste.png).
- ASIS-01B: [Mermaid](diagrams/ASIS-01-rendicontazione.mmd), [SVG](diagrams/ASIS-01-rendicontazione.svg), [PNG](diagrams/ASIS-01-rendicontazione.png).
- TOBE-01A v0.3: [Mermaid](diagrams/to-be/TOBE-01A-richieste-disponibilita.mmd), [SVG](diagrams/to-be/TOBE-01A-richieste-disponibilita.svg), [PNG](diagrams/to-be/TOBE-01A-richieste-disponibilita.png).
- TOBE-01B v0.2: [Mermaid](diagrams/to-be/TOBE-01B-appuntamento-esito.mmd), [SVG](diagrams/to-be/TOBE-01B-appuntamento-esito.svg), [PNG](diagrams/to-be/TOBE-01B-appuntamento-esito.png).
- TOBE-02 v0.3: [Mermaid](diagrams/to-be/TOBE-02-rendicontazione.mmd), [SVG](diagrams/to-be/TOBE-02-rendicontazione.svg), [PNG](diagrams/to-be/TOBE-02-rendicontazione.png).

Sorgenti ed esportazioni IAM sono in [diagrams/iam](diagrams/iam/). I modelli IAM precedenti sono riferimenti di supporto: logout e revoca OPT in attesa sono precisati nelle storie aggiornate, anche se non rappresentati nei vecchi diagrammi. L’as-is conserva la ricostruzione storica; non viene adattato al nuovo to-be. Il contesto grafico precedente non sostituisce la matrice di permessi.

Il Product Backlog resta la fonte per pianificazione e avanzamento. Le bozze di revisione richiedono aggiornamento GitHub, nuova consultazione OPT e conservazione di #53 come assorbita. Le prove dei QS sono pianificate, non risultati dell’analisi.
