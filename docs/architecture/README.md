# Architettura

- [CTX-01 v0.7 · Diagramma di contesto](../requirements/diagrams/context/Context%20Diagram.drawio) · [Anteprima PNG](../requirements/diagrams/context/Context%20Diagram.png)
- [PICUS - System Context](diagrams/PICUS-System-Context.mmd) · [Anteprima PNG](diagrams/PICUS-System-Context.png)
- [PICUS - Flusso dell'architettura MVC](diagrams/PICUS-Architettura-MVC.mmd) · [Anteprima PNG](diagrams/PICUS-Architettura-MVC.png)
- [PICUS - Module&Package View](diagrams/PICUS-Module-Package-View.mmd) · [Anteprima PNG](diagrams/PICUS-Module-Package-View.png)
- [PICUS - Deployment & Container View](diagrams/PICUS-Deployment-Container-View.mmd) · [Anteprima PNG](diagrams/PICUS-Deployment-Container-View.png)
- [PICUS - Sequenza del walking skeleton](diagrams/PICUS-Walking-Skeleton.puml) · [Anteprima PNG](diagrams/PICUS-Walking-Skeleton.png)
- [PICUS - Schema ER PostgreSQL](diagrams/PICUS-Schema-ER.puml) · [Anteprima PNG](diagrams/PICUS-Schema-ER.png)

La vista dei moduli riprende i package di `src/main/java` presenti in `main` al commit `f3c4e95`. Le dipendenze `«import»` corrispondono agli import Java delle API; le dipendenze `«use»` rappresentano le collaborazioni previste con `ComunicazioniApi` e `CatalogoApi`. I contratti pubblici sono presenti, mentre le relative implementazioni sono ancora da completare.

Sotto la radice Java `it.unina.picus` i moduli corrispondono ai package `utenze` (M1), `catalogo` (M2), `richieste` (M3), `comunicazioni` (M4) e `monitoraggio` (M5). Ogni modulo contiene `web`, `api` e `interno`. In `richieste.interno` sono predisposti `applicazione`, `dominio` e `persistenza`; in `utenze.interno` e `catalogo.interno` è presente `persistenza`. `shared` è predisposto per i componenti tecnici condivisi. Ogni collaborazione tra moduli passa per le interfacce e i DTO del package `api` del modulo chiamato.

A regime, i controller di `web` invocano i casi d’uso del proprio modulo. In `interno.dominio` risiedono le regole e le interfacce dei repository; gli adattatori di `interno.persistenza` le implementano con JPA. L’eventuale package `interno.integrazioni` resta un’estensione prevista, non ancora presente in `main`. Spring collega le implementazioni ai servizi tramite dependency injection via costruttore. [Dependency injection in Spring](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)

PICUS usa un unico database PostgreSQL per tutti i moduli, inclusi file e metadati degli allegati. Ciascun modulo accede ai propri dati tramite i suoi repository. [ADR-001](../adr/ADR-001-monolite-modulare-mvc.md) · [Task di predisposizione dell’ambiente](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/77)

## API Java dei moduli

I package `api` espongono le interfacce e i DTO usati nelle collaborazioni
interne di PICUS. I moduli possiedono le proprie regole e i propri dati. Il
modulo che riceve un comando verifica il ruolo e il rapporto dell'account con
la risorsa. Le identità usate nei comandi provengono dal server dopo la
verifica della sessione.

| Interfaccia | Contratti |
| --- | --- |
| [UtenzeApi](../../src/main/java/it/unina/picus/utenze/api/UtenzeApi.java) | Identità, account e profili usati dagli altri moduli. |
| [CatalogoApi](../../src/main/java/it/unina/picus/catalogo/api/CatalogoApi.java) | Misure, comuni e opzioni di erogazione compatibili. |
| [RichiesteApi](../../src/main/java/it/unina/picus/richieste/api/RichiesteApi.java) | Invio, coda, contatti, assegnazione e transizioni della richiesta. |
| [ComunicazioniApi](../../src/main/java/it/unina/picus/comunicazioni/api/ComunicazioniApi.java) | Comunicazioni del Banco, riscontri e avvisi operativi per conto di Richieste. |
| [MonitoraggioApi](../../src/main/java/it/unina/picus/monitoraggio/api/MonitoraggioApi.java) | Conteggi delle OPT, schede degli operatori e riepiloghi trimestrali. |

Il walking skeleton usa Utenze e Catalogo per l'identità della OPT e i
riferimenti a misura e comune. Per la ricerca, Catalogo consulta Utenze e
combina i profili degli operatori con le offerte delle OPT. Richieste ordina
le opzioni usando anche il carico di appuntamenti e determina a chi far
recapitare gli avvisi. Monitoraggio usa Utenze e Catalogo per risolvere i
riferimenti dei dati trimestrali. [Vista dei moduli](diagrams/PICUS-Module-Package-View.mmd)

Le interfacce descrivono gli esiti attesi per le successive implementazioni.
Negoziazione, ritiro dei contatti e tracciato definitivo delle schede si
dettagliano nelle relative storie. [ADR-002](../adr/ADR-002-ciclo-di-vita-richiesta.md) · [Registro dei chiarimenti](../requirements/open-questions.md)

## Contratti del walking skeleton

La sequenza descrive il flusso previsto per lo Sprint 2 usando i contratti Java di `main` al commit `e7a39aa`. Mostra l’accesso e la chiusura della sessione, l’invio in stato `CREATA`, l’elenco della OPT titolare, la coda del Banco e la consultazione del dettaglio con il diniego a una OPT estranea. I casi negativi di accesso e validazione sono ripresi nelle verifiche della [#78](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/78).

| Confine | Dati scambiati |
| --- | --- |
| Spring Security → `utenze.api` | Verifica delle credenziali e, per le richieste protette, verifica dell’account ricavato dalla sessione. Il controller riceve l’identità verificata e la passa al caso d’uso. |
| `richieste` → `catalogo.api` | Identificativo della misura e codice del comune. Il catalogo restituisce i riferimenti validi e lo stato della misura. |
| Form → `richieste.web` | DTO di invio con misura, tipo di destinatario (persona o nucleo), comune e `description` facoltativa. |
| `richieste` → viste Thymeleaf | DTO di elenco e dettaglio con identificativo, data, misura e stato. Il dettaglio include la descrizione per la OPT titolare e il Banco autorizzato. La coda del Banco è in ordine di arrivo. |

Il server ricava la titolarità dalla sessione e dai dati persistiti. Controller e viste ricevono DTO, mentre le entità JPA restano nel modulo che le gestisce. [ADR-003](../adr/ADR-003-isolamento-e-validazione-richieste.md)

## Persistenza iniziale

Lo schema ER rappresenta le sette tabelle di `catalogo`, `utenze` e `richieste` definite dalle migrazioni Flyway per PostgreSQL. `PK` indica la chiave primaria, `FK` la chiave esterna e `UK` un vincolo di unicità sulla singola colonna. In `catalogo_adiacenze` la coppia `(comune_a, comune_b)` è unica e rispetta il vincolo `comune_a < comune_b`. I campi facoltativi sono indicati nel diagramma. [Migrazioni comuni](../../src/main/resources/db/migration/) · [Migrazioni PostgreSQL](../../src/main/resources/db/postgresql/)

La #77 ha introdotto in `main` lo schema PostgreSQL, le mappature JPA e i dati demo. Flyway applica le migrazioni all’avvio. `RichiestaJpa` inizializza lo stato a `CREATA`. La colonna `erogatore_id` è facoltativa e riferisce l’account assegnato. Il flusso previsto la valorizza al passaggio a In programmazione, quando il Banco individua l’erogatore disponibile. [ADR-002](../adr/ADR-002-ciclo-di-vita-richiesta.md) · [PR #83](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/pull/83)

Le migrazioni PostgreSQL V4 e V5 allineano il vincolo sullo stato ai sei valori dell’ADR-002. V4 abilita `CREATA` e lo imposta come default. V5 aggiunge `PRESA_IN_CARICO`. [V4](../../src/main/resources/db/postgresql/V4__aggiungi_stato_creata.sql) · [V5](../../src/main/resources/db/postgresql/V5__allinea_stati_richiesta.sql)

## Flusso dell'architettura MVC

- **Ingresso.** Il browser invia una richiesta HTTPS con metodo HTTP e percorso. Spring Security riconosce la sessione e verifica l’accesso alla funzione. Se la richiesta prosegue, il `DispatcherServlet` usa metodo e percorso per individuare il metodo del controller a cui inoltrarla. [Flusso di Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/sequence.html)
- **Dal Controller al Modello e alla Vista.** Il controller espone la funzione al browser tramite un endpoint HTTP. Chiama un servizio Java del modulo, riceve il risultato e lo usa per preparare i dati da mostrare. Consegna a Spring MVC il nome della vista e questi dati. Thymeleaf è il motore di template confermato e genera la pagina HTML per il browser. [Risoluzione della vista](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/viewresolver.html)
- **Validazione.** Jakarta Bean Validation verifica i vincoli dichiarati sul DTO di ingresso, per esempio la presenza del comune. Il modulo applicativo verifica le regole che richiedono i dati del sistema, come lo stato della misura e la titolarità di una richiesta. [Validazione in Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)
- **Servizio email.** M1 usa un adattatore per inviare i link di attivazione e recupero. La configurazione del servizio esterno resta da definire.

## Come leggere la Deployment and Container View

- **Ruolo della vista.** Mostra i container eseguibili di PICUS, cioè l’applicazione web e il database, e dove vengono eseguiti. Gli attori e il servizio email esterno sono nel System Context.
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
- Il campo `description` può contenere identificativi inseriti come testo libero. La gestione di questo caso resta da definire. [ADR-003](../adr/ADR-003-isolamento-e-validazione-richieste.md)
- Per la futura estrazione via gRPC di `monitoraggio` (M5), il modulo dei report trimestrali confermato dalla docente, restano da definire contratti, proprietà dei dati, transazioni, autorizzazioni e gestione dei guasti di rete. [ADR-001](../adr/ADR-001-monolite-modulare-mvc.md)

## Generare il Javadoc

Dalla radice del clone, usare questi comandi in PowerShell con il JDK 25 nel
`PATH`.

```powershell
javadoc -d target/apidocs '@scripts/javadoc.args'
Copy-Item -LiteralPath docs/architecture/picus-logo.svg -Destination target/apidocs/resource-files/picus-logo.svg
```

Aprire `target/apidocs/index.html`. Aggiornare nei sorgenti il Javadoc dei
contratti quando cambiano dati, permessi, precondizioni o esiti. Il comando
verifica la documentazione e rigenera le pagine HTML in `target`.

Il [foglio di stile del Javadoc](picus-javadoc.css) riprende la palette e la
tipografia della landing. Il generatore lo include con `--add-stylesheet`
nelle anteprime locali e nei file pubblicati su GitHub Pages. Il pulsante
nell'intestazione alterna i temi chiaro e scuro. Il browser salva la scelta
per le visite successive quando lo storage locale è disponibile.

Il workflow usa lo stesso file di argomenti e include il logo SVG che
collega alla landing page. Quando una PR viene integrata in `main`, rigenera
la documentazione e la pubblica su GitHub Pages. Le istruzioni
di attivazione sono nella [configurazione GitHub](../../.github/SETUP.md#pubblicazione-del-javadoc).
