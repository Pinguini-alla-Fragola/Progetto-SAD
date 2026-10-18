# PICUS - Sintesi dell'architettura e dell'organizzazione

Aggiornato al **16 ottobre 2026**. Riepilogo delle scelte concordate e della configurazione organizzata su GitHub. Le funzionalità applicative sono ancora da implementare; le aree funzionali non rappresentano già moduli software definitivi.

## 1. Dominio e attori

**PICUS (Programma Integrativo Contro Urgenze Sociali)** supporta il Banco nel coordinamento delle Organizzazioni Partner Territoriali (OPT), delle misure di accompagnamento e degli erogatori.

- **Banco:** coordina richieste, disponibilità, appuntamenti, comunicazioni e rendicontazione.
- **OPT:** richiede servizi e può offrire misure; mantiene i contatti con gli assistiti.
- **Operatori:** erogano misure, confermano appuntamenti e compilano schede trimestrali.
- **Persone assistite:** stakeholder, senza account nell'applicazione né dati identificativi registrati.

Le aree da analizzare comprendono account e ruoli, catalogo configurabile delle misure, OPT ed erogatori, richieste e appuntamenti, comunicazioni, monitoraggio trimestrale e reporting. La scheda trimestrale è distinta dalle singole richieste e non richiede necessariamente un collegamento a OPT, misura o prestazione.

## 2. Architettura software adottata

Le scelte tecniche, stilistiche e sui pattern appartengono al team. L'impostazione è un **monolite modulare MVC**, predisposto all'estrazione di un sottosistema in un **microservizio gRPC** nelle iterazioni finali.

| Livello | Responsabilità |
| --- | --- |
| View | Rendering e interazione con HTML5, CSS3, JavaScript vanilla e template lato server |
| Controller | Routing HTTP, validazione degli input e delega; nessuna logica di business |
| Application / Domain | Casi d'uso, regole di business e responsabilità del modello |
| Persistenza | Repository del modulo, Spring Data JPA / Hibernate e MySQL |

- **Stack:** Java 25, Maven Wrapper, Spring Boot / Spring Web MVC e Jakarta Bean Validation.
- **Confini:** chiamate tra moduli tramite interfacce e DTO immutabili; niente accessi diretti ai repository altrui o relazioni JPA tra moduli diversi.
- **Dati:** le entità JPA non sono input/output dei Controller; usare DTO. Per gli scambi tra moduli, record e tipi standard compatibili con la futura mappatura Protobuf.
- **GRASP:** Information Expert, Creator, Controller, Low Coupling, High Cohesion, Polymorphism, Pure Fabrication, Indirection e Protected Variations.
- **GoF:** Strategy, State, Factory Method / Builder, Observer, Adapter / Facade e Decorator sono riferimenti da applicare dove appropriati. Le partecipazioni concrete ai pattern devono essere motivate e documentate in Javadoc e UML con Visual Paradigm.
- **Test:** JUnit 5 senza Mockito; oggetti reali, Fake/Stub manuali e test di integrazione quando pertinenti. Injection tramite costruttore.
- **Deployment previsto:** Docker e Docker Compose, con predisposizione a Kubernetes. Configurazione tramite variabili d'ambiente; nessun file persistente o stato di sessione sul filesystem locale dei container.

**Da definire:** confini e nomi dei moduli, motore di template (Thymeleaf è un'opzione), applicazioni concrete dei pattern, sottosistema da estrarre e contratti gRPC. Le decisioni significative saranno motivate tramite ADR.

## 3. Repository e collaborazione

- Repository pubblica: [CapHello/Progetto-SAD](https://github.com/CapHello/Progetto-SAD). Il PO ha confermato che le protezioni sono già applicate.
- Lavoro su branch dedicati nel formato `<type>/<task-slug>` e integrazione tramite Pull Request e review, rispettando le protezioni di `main`.
- Conventional Commits per i commit effettuati dai membri umani.
- Gli agenti creano un nuovo branch locale per ogni attività; lasciano modifiche locali senza commit, push, PR o attribuzioni come autori/coautori.
- Nessuna credenziale o dato personale reale nei file, nelle issue o negli allegati. I dati dimostrativi sono fittizi; i dati degli operatori richiedono protezione.

## 4. GitHub Project

Project: [Progetto-SAD](https://github.com/users/CapHello/projects/2), privato negli screenshot condivisi. La sua visibilità è indipendente da quella della repository.

### Label

| Label | Uso |
| --- | --- |
| `type:epic` | Area funzionale ampia |
| `type:story` | Funzionalità dal punto di vista dell'utente |
| `type:task` | Attività di analisi, documentazione o sviluppo |
| `type:question` | Domanda al committente |
| `blocked` | Impedimento esplicitato nell'issue |

### Campi e stati

- **Status:** Backlog → Ready → In progress → In review → Done.
- **Priority:** P1 - Alta, P2 - Media, P3 - Bassa.
- **Ordine:** numero crescente per ordinare precisamente il Product Backlog; usare 10, 20, 30 per consentire inserimenti intermedi.
- **Iteration:** iterazione del lavoro. Assignees e Milestone completano la pianificazione.

Gli stati del Project descrivono il lavoro del team; il ciclo di vita delle richieste PICUS è un modello distinto, da precisare durante l'analisi.

### Viste principali

| Vista | Layout | Filtro e organizzazione |
| --- | --- | --- |
| Product Backlog | Table | `label:"type:story"`; Ordine crescente |
| Sprint 1 - Preparazione | Board | `iteration:"S1 - Preparazione"`; colonne per Status |
| Domande al committente | Table | `label:"type:question"`; priorità alta per prima |

Le viste rappresentano gli stessi elementi filtrati: non duplicare le issue. Una tabella senza filtri può servire per assegnare i campi agli elementi ancora esclusi dalle viste.

## 5. Ruoli e gestione del lavoro

- **PO:** chiarisce obiettivi e valore, mantiene il backlog e ne definisce l'ordine.
- **Scrum Master:** facilita planning ed eventi, aiuta a far emergere e rimuovere impedimenti.
- **Team di sviluppo:** concorda il lavoro sostenibile, si organizza e prepara/revisiona gli output.

Gli assegnatari identificano chi svolge effettivamente l'attività: possono restare vuoti fino al planning. Coinvolgere tutto il team non richiede assegnare ogni issue a tutti. Una task di raffinamento completata non implica che la User Story del prodotto sia già implementata.

## 6. Sprint preparatorio e calendario

Obiettivo S1: comprendere il dominio e produrre analisi, backlog verificabile, scenari di qualità, domande aperte e proposta di MVP. **In questa iterazione non è richiesta l'implementazione.**

Milestone: **S1 - Preparazione PICUS**, scadenza **5 novembre 2026**.

| Codice attività | Risultato atteso |
| --- | --- |
| S1-01 | Stakeholder e Context Diagram |
| S1-02 | Processo as-is e criticità |
| S1-03 | Processo to-be e miglioramenti motivati |
| S1-04 | Epic del prodotto |
| S1-05 | Product Backlog prioritizzato |
| S1-06 | Verifica INVEST e criteri di accettazione dettagliati |
| S1-07 | Quality Scenario Catalogue |
| S1-08 | Open Questions Log |
| S1-09 | Proposta di MVP motivata |
| S1-10 | Coerenza degli artefatti e presentazione della review |

Tutte sono task di preparazione con label `type:task`, milestone e iterazione S1. I codici S1-xx sono identificativi concordati, non i numeri GitHub. Le domande individuali raccolte da S1-08 avranno label `type:question`.

| Iterazione | Intervallo operativo inclusivo | Review / passaggio successivo |
| --- | --- | --- |
| S1 - Preparazione | 15 ottobre - 4 novembre | 5 novembre |
| S2 - Costruzione 1 | 5 - 18 novembre | 19 novembre |
| Costruzione 2 (prevista) | 19 novembre - 2 dicembre | 3 dicembre |
| Costruzione 3 e finalizzazione (prevista) | 3 - 16 dicembre | 17 dicembre |

GitHub mostra l'ultimo giorno incluso: la data della review chiude didatticamente l'iterazione e avvia la successiva. S1 e S2 sono state configurate; le successive restano da configurare nel Project.

## 7. Criteri comuni e fonti

Ogni attività deve collegare il proprio output, citare le fonti, distinguere fatti/proposte/ipotesi, mantenere coerenza con gli altri artefatti e prevedere una revisione. Le User Story indicano ruolo, obiettivo, beneficio, Epic, criteri di accettazione e dipendenze. Gli scenari di qualità specificano sorgente, stimolo, ambiente, artefatto, risposta e misura.

Riferimenti indicativi della preparazione: **5-8 Epic, 15-25 storie, almeno 8 storie con criteri dettagliati, 6-10 scenari, 5-10 domande e MVP di 4-7 storie**. Sono suggerimenti della traccia, non quote rigide. La review preparatoria dura 10 minuti.

Fonti: `AGENTS.md` nella cartella padre della repository; `00-Presentazione Progetto Picus.pdf` (in particolare slide 11 e 18-29); `01-PICUS_richieste_informali_del_committente (1).pdf`, entrambi nella cartella locale `Progetto di Esame`; decisioni e configurazione condivise dal PO in chat.
