# ADR-002: Ciclo della richiesta e assegnazione dell’erogatore

- **Status:** Accepted
- **Date:** 2026-11-07
- **Decision owners:** @salvatoredc04
- **Related issues:** [#76](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/76), [#29](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/29), [#48](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/48), [#49](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/49), [#51](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/51), [#52](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/52), [#54](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/54)

## Context

La OPT invia una richiesta di misura. Il Banco la prende in carico, cerca un erogatore e coordina l’accordo. Il team ha definito il ciclo a sei stati e le regole di annullamento il 14 novembre 2026, aggiornando le scelte discusse nella #29. [Regole funzionali](../requirements/regole-funzionali.md#richiesta-e-accordo)

Il walking skeleton registra la richiesta e la presenta alla OPT titolare e al Banco. Le storie #49, #51 e #52 completano il percorso dell’MVP fino alla conferma o al rifiuto definitivo. L’annullamento ha un proprio incremento, tracciato nella #54.

## Considered options

1. **Ciclo unico con presa in carico esplicita.** Distingue la coda dalla ricerca e consente di aggiornare stato, assegnazione e accordo insieme.
2. **Ciclo separato per l’appuntamento.** Consente di rappresentare appuntamenti che evolvono in modo autonomo. Richiede regole di sincronizzazione fra appuntamento e richiesta anche quando i due esiti coincidono.
3. **Stati di richiesta per ogni fase del contatto.** Rende visibile l’avanzamento di ricerca e negoziazione, ma moltiplica le transizioni per ogni candidato e confonde il rifiuto di un erogatore con l’esito della pratica.

## Decision

La ricerca si svolge in `Presa in carico`. Il rifiuto di un candidato mantiene questo stato. Dopo l’assegnazione, le controproposte mantengono la richiesta `In programmazione`. L’appuntamento usa lo stato della richiesta.

| Stato di partenza | Comando e condizioni | Esito |
| --- | --- | --- |
| Richiesta da creare | L’OPT autenticata invia il modulo valido. | `Creata` |
| `Creata` | Il Banco prende esplicitamente in carico la richiesta. | `Presa in carico` |
| `Presa in carico` | Il Banco individua e assegna un erogatore disponibile. | `In programmazione` |
| `In programmazione` | Il Banco conferma l’accordo con i dati completi per la modalità scelta. | `Confermata` |
| `Presa in carico` o `In programmazione` | Il Banco conclude che manca una soluzione praticabile, valutato anche il remoto quando compatibile. | `Rifiutata`. La OPT vede «Nessun erogatore disponibile». |
| Qualsiasi stato | Un soggetto coinvolto e autorizzato annulla. Il motivo è facoltativo in `Creata` e obbligatorio negli altri stati. | `Annullata`. I dettagli si conservano, la ricerca resta chiusa e il sistema avvisa i soggetti coinvolti. |

In persistenza `richieste.erogatore_id` è un UUID facoltativo con chiave esterna verso `utenze_account.id`. Il passaggio a `In programmazione` registra atomicamente l’account della OPT o dell’operatore del Banco assegnato, dopo la verifica del ruolo.

Per l’accordo in presenza servono data, ora, luogo, nome e cognome del referente e telefono. Per l’accordo a distanza servono data, ora e link. Il comando del Banco registra dettagli e `Confermata` nella stessa transazione. Dopo il commit la OPT titolare legge stato e dettagli nello stesso esito. I dettagli diventano immutabili con la conferma.

Il server consente soltanto le transizioni della tabella e verifica stato, autorizzazione e dati obbligatori prima di aggiornare la pratica. La verifica dello stato e la sua modifica devono essere atomiche anche con comandi concorrenti. L’annullamento verifica i permessi sulla singola richiesta. Una richiesta già `Annullata` rimane tale. [Matrice di annullamento](../requirements/regole-funzionali.md#matrice-di-annullamento)

## Consequences

- Gli utenti vedono un solo stato coerente con l’accordo. I contatti e le risposte restano registrati separatamente dallo stato della pratica.
- Il modulo `richieste` deve coordinare contatti e transizioni. Le prove verificano il contatto sequenziale, il passaggio a `In programmazione` con l’erogatore assegnato, il rifiuto definitivo, la conferma con dati completi e la conservazione dello stato se due comandi concorrenti risultano incompatibili.
- Un eventuale requisito futuro di riprogrammazione o tracciamento della prestazione richiederebbe di rivalutare un ciclo separato per l’appuntamento.
- Le prove di annullamento seguono la #54 e QS-01B. Forma e visibilità del motivo restano in [Q-04](../requirements/open-questions.md#q-04-confine-e-annullamento). La negoziazione è in [Q-09](../requirements/open-questions.md#q-09-negoziazione-dellappuntamento). La gestione del contatto senza risposta è in [Q-19](../requirements/open-questions.md#q-19-erogatore-senza-risposta).
