# Scenari di qualità

Catalogo v0.2 del **04/11/2026**; la v0.1 del 25/10/2026 è incorporata in A5 del Word **v0.5**, da riallineare. Le sei schede contengono sorgente, stimolo, ambiente, artefatto, risposta e misura, oltre a stato delle soglie e metodo di verifica.

**Stato:** proposte da validare. Le soglie introdotte dal team non sono SLA concordati o risultati di prove software già eseguite.

| Scenario | Area | Priorità proposta |
| --- | --- | --- |
| [QS-01 Isolamento degli accessi](QS-01-sicurezza.md) | Sicurezza | P1 |
| [QS-02 Minimizzazione e riservatezza dei dati](QS-02-privacy.md) | Privacy | P1 |
| [QS-03 Risposta durante i picchi trimestrali](QS-03-performance.md) | Performance | P2 |
| [QS-04 Uso dei flussi principali da smartphone](QS-04-usabilita.md) | Usabilità | P1 |
| [QS-05 Ripristino del servizio dopo un fermo](QS-05-disponibilita.md) | Disponibilità | P2 |
| [QS-06 Modifica del catalogo senza rilascio software](QS-06-modificabilita.md) | Modificabilità | P2 |

Gli scenari si verificano a fine costruzione, sull’applicazione completa: QS-01 e QS-02 sul percorso autorizzativo e sui dati, QS-03 sul carico trimestrale, QS-04 sulla prova mobile, QS-05 sul ripristino e QS-06 sulla gestione del catalogo.
Q-07 raccoglie la validazione dei carichi e delle soglie. Il numero delle OPT (circa 450) dimensiona i dati di prova, non la concorrenza; i profili non sono dedotti dal dato 230.000 utenti.
[Documento Word](../Documentazione_SAD_2026.docx) · [Proposta di MVP](../requirements/proposta-mvp.md) · [Registro dei chiarimenti](../requirements/open-questions.md)
