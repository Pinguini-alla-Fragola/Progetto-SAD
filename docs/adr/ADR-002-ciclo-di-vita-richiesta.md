# ADR-002: Rappresentare il ciclo della richiesta con quattro stati

- **Status:** Proposed
- **Date:** 2026-11-07
- **Decision owners:** @salvatoredc04
- **Related issues:** [#76](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/76), [#29](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/29), [#48](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/48), [#49](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/49), [#51](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/51), [#52](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/52), [#54](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/54)

## Context

La OPT invia una richiesta di misura. Il Banco cerca un erogatore, raccoglie la disponibilità e coordina l’accordo. Il chiarimento #29 fissa quattro stati della richiesta. Una disponibilità positiva indica che il contatto può proseguire. La conferma del Banco attesta l’accordo e rende consultabili i dettagli alla OPT titolare, che informa l’assistito fuori da PICUS. [Regole funzionali](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/2bb87e6c7c5b454d40af4a2b1b9635f07e8dfb2c/docs/requirements/regole-funzionali.md#richiesta-e-accordo)

Il walking skeleton registra la richiesta e la presenta alla OPT titolare e al Banco. Le storie #49, #51 e #52 completano il percorso dell’MVP fino alla conferma o al rifiuto definitivo. L’annullamento eccezionale ha un proprio incremento, tracciato nella #54. [Proposta di MVP](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/2bb87e6c7c5b454d40af4a2b1b9635f07e8dfb2c/docs/requirements/proposta-mvp.md)

## Considered options

1. **Quattro stati sulla richiesta e dettagli dell’accordo associati.** Corrisponde ai quattro stati di dominio e consente di aggiornare stato e accordo insieme.
2. **Ciclo separato per l’appuntamento.** Consente di rappresentare appuntamenti che evolvono in modo autonomo. Richiede regole di sincronizzazione fra appuntamento e richiesta anche quando i due esiti coincidono.
3. **Stati di richiesta per ogni fase del contatto.** Rende visibile l’avanzamento di ricerca e negoziazione, ma moltiplica le transizioni per ogni candidato e confonde il rifiuto di un erogatore con l’esito della pratica.

## Decision

Proponiamo di registrare i quattro stati in `richieste` e di associare i dettagli dell’accordo alla pratica. La disponibilità, il rifiuto di un singolo candidato e le controproposte appartengono ai contatti. La pratica resta `In programmazione` mentre il Banco interpella un erogatore alla volta e concorda i dettagli.

| Stato di partenza | Comando e condizioni | Esito |
| --- | --- | --- |
| Richiesta da creare | L’OPT autenticata invia il modulo valido. | `In programmazione` |
| `In programmazione` | Il Banco conferma l’accordo con i dati completi per la modalità scelta e assegna la pratica alla OPT erogatrice o all’Operatore del Banco. | `Confermata` |
| `In programmazione` | Il Banco conclude che manca una soluzione praticabile, valutato anche il remoto quando compatibile. | `Rifiutata`. La OPT vede «Nessun erogatore disponibile». |
| `Confermata` | Un account abilitato dalla matrice registra un evento eccezionale e il motivo. | `Annullata`. I dettagli si conservano e la ricerca rimane chiusa. |

Per l’accordo in presenza servono data, ora, luogo, nome e cognome del referente e telefono. Per l’accordo a distanza servono data, ora e link. Il comando del Banco registra dettagli, assegnazione e `Confermata` nella stessa transazione. Dopo il commit la OPT titolare legge stato e dettagli nello stesso esito. I dettagli diventano immutabili con la conferma.

Il server consente soltanto le transizioni della tabella e verifica stato, autorizzazione e dati obbligatori prima di aggiornare la pratica. La verifica dello stato e la sua modifica devono essere atomiche anche con comandi concorrenti. Per l’annullamento valgono i permessi per tipo di assegnatario e il motivo obbligatorio. [Matrice di annullamento](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/2bb87e6c7c5b454d40af4a2b1b9635f07e8dfb2c/docs/requirements/regole-funzionali.md#matrice-di-annullamento)

## Consequences

- Gli utenti vedono un solo stato coerente con l’accordo. I contatti e le risposte restano registrati separatamente dallo stato della pratica.
- Il modulo `richieste` deve coordinare contatti e transizioni. Le prove verificano il contatto sequenziale, la disponibilità che lascia aperta la pratica, il rifiuto definitivo, la conferma con dati completi e la conservazione dello stato se due comandi concorrenti risultano incompatibili.
- Un eventuale requisito futuro di riprogrammazione o tracciamento della prestazione richiederebbe di rivalutare un ciclo separato per l’appuntamento.
- Le prove di annullamento seguono la #54 e QS-01B. La visibilità del motivo e gli avvisi restano in Q-04. Negoziazione e gestione del contatto senza risposta restano in Q-09 e Q-19. [Registro dei chiarimenti](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/2bb87e6c7c5b454d40af4a2b1b9635f07e8dfb2c/docs/requirements/open-questions.md)
