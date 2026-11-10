# ADR-001: Adottare un monolite modulare MVC

- **Status:** Proposed
- **Date:** 2026-11-07
- **Decision owners:** @CapHello
- **Related issues:** [#76](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/76), [#77](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/77), [#25](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/25), [#28](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/28), [#48](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/48), [#49](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/49), [#51](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/51), [#52](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/52)

## Context

PICUS parte da un unico progetto Spring Boot con Java 25 e Maven Wrapper. Il walking skeleton dello Sprint 2 copre accesso, invio e consultazione delle richieste e coda del Banco. Il percorso dell’MVP prosegue con ricerca dell’erogatore, risposta alla disponibilità e conferma dell’accordo o rifiuto definitivo. [Proposta di MVP](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/2bb87e6c7c5b454d40af4a2b1b9635f07e8dfb2c/docs/requirements/proposta-mvp.md)

La struttura deve permettere di evolvere i flussi di coordinamento, comunicazione e monitoraggio con responsabilità distinte. Per il primo percorso servono controlli di accesso server per ogni richiesta, validazione dei campi ammessi e un’interfaccia utilizzabile da smartphone. La #77 prevede un ambiente riproducibile con un’applicazione e un database PostgreSQL. [QS-01A](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/2bb87e6c7c5b454d40af4a2b1b9635f07e8dfb2c/docs/quality-scenarios/QS-01A-consultazione-richieste.md) · [QS-02A](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/2bb87e6c7c5b454d40af4a2b1b9635f07e8dfb2c/docs/quality-scenarios/QS-02A-identificativi-assistiti.md) · [QS-04A](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/2bb87e6c7c5b454d40af4a2b1b9635f07e8dfb2c/docs/quality-scenarios/QS-04A-invio-richiesta-smartphone.md)

## Considered options

1. **Monolite MVC organizzato per strati globali.** Un solo rilascio e una struttura iniziale semplice. Con la crescita dei flussi, controller, servizi e repository condivisi rendono meno chiara la responsabilità sui dati.
2. **Monolite modulare MVC.** Un solo rilascio con moduli delimitati per responsabilità. Le chiamate restano nello stesso processo e i confini richiedono regole di dipendenza verificabili.
3. **Microservizi fin dallo Sprint 2.** Rilascio e scalabilità separati per sottosistema. L’identità, i guasti di rete e la consistenza delle operazioni tra servizi aumentano il lavoro necessario per dimostrare il primo percorso.

## Decision

Il team conferma il monolite modulare con Spring Boot. I moduli che espongono interfacce web adottano MVC. Il team ha confermato Thymeleaf per le viste web e PostgreSQL come database condiviso, accessibile tramite pgJDBC. Il deployment della #77 comprende `picus-webapp` e `picus-db` in Docker Compose.

| Modulo | Responsabilità |
| --- | --- |
| `utenze` | Account, sessioni, ruoli e profili OPT e Operatore del Banco. |
| `catalogo` | Misure, offerta delle OPT, comuni e relazioni di adiacenza. |
| `richieste` | Richieste, coda del Banco, contatti con gli erogatori, assegnazione ed esito. |
| `comunicazioni` | Comunicazioni generali e conferme di ricezione. |
| `monitoraggio` | Conteggi trimestrali delle OPT, schede degli operatori e consultazione per il monitoraggio. |

Ogni modulo espone in `api` le interfacce e i DTO che gli altri moduli possono usare. `interno` contiene `applicazione` per i casi d’uso, `dominio` per entità e regole, `persistenza` per repository e mappature JPA e `integrazioni` per gli adattatori richiesti. `web` traduce richieste HTTP e moduli HTML in chiamate applicative e costruisce le viste con DTO dedicati. `shared` raccoglie soltanto componenti tecnici comuni.

Nel primo flusso `richieste` usa le API di `utenze` e `catalogo` per ottenere le informazioni necessarie. Le regole di titolarità e assegnazione della singola pratica appartengono a `richieste`. Ogni modulo accede alle proprie entità e tabelle. Gli altri moduli passano dalla sua API. Le dipendenze tra moduli restano acicliche.

## Consequences

- Il team può sviluppare e verificare i flussi in un solo ambiente. Le operazioni sulle richieste possono usare transazioni locali.
- I moduli condividono processo, database e ciclo di rilascio. Un guasto dell’applicazione può interessare più funzionalità e le modifiche di schema vanno coordinate.
- In review si controlla che i moduli accedano solo alle API pubbliche, che le dipendenze siano acicliche e che le firme pubbliche e le viste usino DTO. La vista dei moduli e la sequenza del walking skeleton in `docs/architecture/` documentano questi confini.
- Le API tra moduli preparano un punto di separazione. Se emerge l’esigenza di un rilascio autonomo, il team può valutare gRPC e dovrà definire i contratti e rivedere proprietà dei dati, confini transazionali, autorizzazioni e gestione dei guasti di rete.
