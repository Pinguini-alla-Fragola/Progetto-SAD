# Quality scenarios

Catalogo v0.3 del **04/11/2026**, allineato ad A5 del documento **v0.7**. Il PDF è pubblicato nella repository. Ogni scheda segue il template: una tabella con Source, Stimulus, Environment, Artifact, Response e Response measure, accompagnata da attributo di qualità, priorità, motivazione e approccio di verifica.

**Stato:** proposte da validare. Le soglie introdotte dal team non sono SLA concordati o risultati di prove software già eseguite.

| Scenario | Area | Priorità proposta |
| --- | --- | --- |
| [QS-01 Isolamento degli accessi](QS-01-sicurezza.md) | Sicurezza | P1 |
| [QS-02 Minimizzazione e riservatezza dei dati](QS-02-privacy.md) | Privacy | P1 |
| [QS-03 Risposta durante i picchi trimestrali](QS-03-performance.md) | Performance | P2 |
| [QS-04 Uso dei flussi principali da smartphone](QS-04-usabilita.md) | Usabilità | P1 |
| [QS-05 Ripristino del servizio dopo un fermo](QS-05-disponibilita.md) | Disponibilità | P2 |
| [QS-06 Modifica del catalogo senza rilascio software](QS-06-modificabilita.md) | Modificabilità | P2 |

Gli scenari si verificano a fine costruzione, sull'applicazione completa: QS-01 e QS-02 sui permessi e sui dati, QS-03 sul carico trimestrale, QS-04 sull'uso da smartphone, QS-05 sul ripristino e QS-06 sulla configurazione del catalogo. Queste schede descrivono prove previste, non risultati già ottenuti nella review S1.

Q-07 raccoglie la validazione dei carichi e delle soglie. Il numero delle OPT (circa 450) dimensiona i dati di prova, non la concorrenza; i profili non sono dedotti dal dato 230.000 utenti. QS-06 valuta la modificabilità limitatamente agli aggiornamenti del catalogo tramite configurazione.

[Documento PDF](../Documentazione_SAD_2026.pdf) · [Proposta di MVP](../requirements/proposta-mvp.md) · [Registro dei chiarimenti](../requirements/open-questions.md)
