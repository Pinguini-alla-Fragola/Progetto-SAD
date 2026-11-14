# Architettura

- [CTX-01 v0.7 · Diagramma di contesto](diagrams/contesto/Context%20Diagram.drawio) · [Anteprima PNG](diagrams/contesto/Context%20Diagram.png)
- [Diagramma dei container C4 di PICUS a regime](diagrams/PICUS-C4-Container.mmd) · [Anteprima PNG](diagrams/PICUS-C4-Container.png)
- [ARCH-01 · Architettura MVC di PICUS a regime](diagrams/PICUS-Architettura-MVC.mmd) · [Anteprima PNG](diagrams/PICUS-Architettura-MVC.png)
- [Diagramma dei moduli e dei package Java](diagrams/PICUS-Diagramma-Moduli.mmd) · [Anteprima PNG](diagrams/PICUS-Diagramma-Moduli.png)
- [Vista di deployment dello Sprint 2 con Docker Compose](diagrams/PICUS-Deployment.mmd) · [Anteprima PNG](diagrams/PICUS-Deployment.png)

Nel diagramma dei moduli, ogni freccia parte dal modulo chiamante e punta al modulo di cui usa il package `api`. M1 fornisce identità, ruoli e profili. Ogni modulo applica i controlli di titolarità, assegnazione o ricezione sulle risorse che gestisce. Il tratteggio indica la collaborazione per le notifiche operative, da dettagliare.

Sotto la radice Java `it.unina.picus` i moduli corrispondono ai package `utenze` (M1), `catalogo` (M2), `richieste` (M3), `comunicazioni` (M4) e `monitoraggio` (M5). Ogni collaborazione tra moduli passa per le interfacce e i DTO del package `api` del modulo chiamato. I controller di `web` invocano i casi d’uso del proprio modulo in `interno.applicazione`.

In `interno.dominio` risiedono le regole e le interfacce dei repository. L’applicazione usa queste interfacce e gli adattatori di `interno.persistenza` le implementano con JPA. Gli adattatori di `interno.integrazioni` implementano i contratti richiesti dall’applicazione. Spring collega le implementazioni ai servizi tramite dependency injection via costruttore. [Dependency injection in Spring](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)

PICUS usa un unico database PostgreSQL per tutti i moduli, inclusi file e metadati degli allegati. Ciascun modulo accede ai propri dati tramite i suoi repository. [ADR-001](../adr/ADR-001-monolite-modulare-mvc.md) · [Task di predisposizione dell’ambiente](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/77)

## Contratti del walking skeleton

I contratti previsti coprono accesso, invio, elenco della OPT titolare e coda del Banco.

| Confine | Dati scambiati |
| --- | --- |
| `richieste` → `utenze.api` | Identificativo dell’account autenticato e ruolo ricavati dalla sessione. |
| `richieste` → `catalogo.api` | Identificativo della misura e codice del comune. Il catalogo restituisce i riferimenti validi e lo stato della misura. |
| Form → `richieste.web` | DTO di invio con misura, tipo di destinatario (persona o nucleo), comune e `description` facoltativa. |
| `richieste` → viste Thymeleaf | DTO di elenco e dettaglio con identificativo, data, misura e stato. Il dettaglio include la descrizione per la OPT titolare e il Banco autorizzato. La coda del Banco è in ordine di arrivo. |

Il server ricava la titolarità dalla sessione e dai dati persistiti. Controller e viste ricevono DTO, mentre le entità JPA restano nel modulo che le gestisce. [ADR-003](../adr/ADR-003-isolamento-e-validazione-richieste.md)

## Persistenza iniziale

La #77 ha introdotto in `main` lo schema PostgreSQL, le mappature JPA e i dati demo. Flyway applica le migrazioni all’avvio. `RichiestaJpa` inizializza lo stato a `CREATA`. La colonna `erogatore_id` è facoltativa e riferisce l’account assegnato. Il flusso previsto la valorizza al passaggio a In programmazione, quando il Banco individua l’erogatore disponibile. [ADR-002](../adr/ADR-002-ciclo-di-vita-richiesta.md) · [PR #83](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/pull/83)

## Come leggere ARCH-01

- **Ingresso.** Il browser invia una richiesta HTTPS con metodo HTTP e percorso. Spring Security riconosce la sessione e verifica l’accesso alla funzione. Se la richiesta prosegue, il `DispatcherServlet` usa metodo e percorso per individuare il metodo del controller a cui inoltrarla. [Flusso di Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/sequence.html)
- **Dal Controller al Modello e alla Vista.** Il controller espone la funzione al browser tramite un endpoint HTTP. Chiama un servizio Java del modulo, riceve il risultato e lo usa per preparare i dati da mostrare. Consegna a Spring MVC il nome della vista e questi dati. Thymeleaf è il motore di template confermato e genera la pagina HTML per il browser. [Risoluzione della vista](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/viewresolver.html)
- **Validazione.** Jakarta Bean Validation verifica i vincoli dichiarati sul DTO di ingresso, per esempio la presenza del comune. Il modulo applicativo verifica le regole che richiedono i dati del sistema, come lo stato della misura e la titolarità di una richiesta. [Validazione in Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)
- **Servizio email.** M1 usa un adattatore per inviare i link di attivazione e recupero. La configurazione del servizio esterno resta da definire.

## Come leggere la vista di deployment

- **Notazione.** La vista segue la notazione UML dei diagrammi di deployment. I `«device»` sono le macchine fisiche, gli `«execution environment»` sono gli ambienti di esecuzione annidati e l’`«artifact»` è il file eseguito. Il sorgente usa il layout ELK di Mermaid.
- **Nodi e container.** Docker Compose esegue sulla macchina host due container. In `picus-webapp` la JVM Java 25 esegue `picus.jar`. In `picus-db` PostgreSQL 17 gestisce il database `picus` e conserva i dati nel volume `picus-dati`. La nota indica che `picus-webapp` parte solo quando `picus-db` è pronto. [docker-compose.yml](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/main/docker-compose.yml)
- **Collegamenti.** Il browser raggiunge l’applicazione via HTTP su `127.0.0.1:8080`. L’applicazione usa JDBC verso `picus-db:5432`, raggiungibile solo sulla rete Compose.
- **Fuori dallo Sprint 2.** HTTPS, servizio email e ambiente di produzione non fanno ancora parte del deployment. La porta pubblicata solo in locale non consente l’accesso da un altro dispositivo.

## Scenari di qualità e decisioni

| Scenario | Decisione e verifica prevista |
| --- | --- |
| [QS-01A · Consultazione](../quality-scenarios/QS-01A-consultazione-richieste.md) e [QS-02A · Dati identificativi](../quality-scenarios/QS-02A-identificativi-assistiti.md) | [ADR-003](../adr/ADR-003-isolamento-e-validazione-richieste.md). Il server verifica i permessi sulla singola richiesta e rifiuta gli invii con campi identificativi extra. Le verifiche del walking skeleton sono nella [#78](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/78). |
| [QS-01B · Annullamento](../quality-scenarios/QS-01B-annullamento-richieste.md) | [ADR-002](../adr/ADR-002-ciclo-di-vita-richiesta.md) e [ADR-003](../adr/ADR-003-isolamento-e-validazione-richieste.md). Il server verifica la transizione e i permessi sulla richiesta secondo la [#54](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/54). |
| [QS-04A · Invio da smartphone](../quality-scenarios/QS-04A-invio-richiesta-smartphone.md) | [ADR-001](../adr/ADR-001-monolite-modulare-mvc.md). Verificare che il modulo Thymeleaf sia utilizzabile a 360 × 800 pixel CSS senza scorrimento orizzontale. |
| [QS-05B · Ripristino del database](../quality-scenarios/QS-05B-ripristino-da-backup.md) | [ADR-001](../adr/ADR-001-monolite-modulare-mvc.md). Un guasto a PostgreSQL coinvolge tutti i moduli. La prova richiede di ripristinare una copia separata e verificare le richieste recuperate. |

## Rischi e debito tecnico

- Il database e il rilascio sono condivisi. Le migrazioni vanno coordinate tra i moduli e il ripristino da backup resta da provare.
- Lo schema risultante dalle migrazioni di `main` ammette cinque stati. Il passaggio a `Presa in carico` richiede l’allineamento della persistenza con l’[ADR-002](../adr/ADR-002-ciclo-di-vita-richiesta.md).
- Il campo `description` può contenere identificativi inseriti come testo libero. La gestione di questo caso resta da definire. [ADR-003](../adr/ADR-003-isolamento-e-validazione-richieste.md)
- L’eventuale estrazione di un sottosistema via gRPC richiede di definire contratti, proprietà dei dati, transazioni e gestione dei guasti di rete. [ADR-001](../adr/ADR-001-monolite-modulare-mvc.md)
