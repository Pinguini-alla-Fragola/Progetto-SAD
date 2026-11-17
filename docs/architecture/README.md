# Architettura

- [CTX-01 v0.7 · Diagramma di contesto](../requirements/diagrams/context/Context%20Diagram.drawio) · [Anteprima PNG](../requirements/diagrams/context/Context%20Diagram.png)
- [PICUS - System Context](diagrams/PICUS-System-Context.mmd) · [Anteprima PNG](diagrams/PICUS-System-Context.png)
- [PICUS - Flusso dell'architettura MVC](diagrams/PICUS-Architettura-MVC.mmd) · [Anteprima PNG](diagrams/PICUS-Architettura-MVC.png)
- [PICUS - Module&Package View](diagrams/PICUS-Module-Package-View.mmd) · [Anteprima PNG](diagrams/PICUS-Module-Package-View.png)
- [PICUS - Deployment & Container View](diagrams/PICUS-Deployment-Container-View.mmd) · [Anteprima PNG](diagrams/PICUS-Deployment-Container-View.png)
- [Walking skeleton · WS-01 Accesso, sessione e logout](diagrams/PICUS-Walking-Skeleton.puml) · [PNG](diagrams/PICUS-Walking-Skeleton.png) · [SVG](diagrams/PICUS-Walking-Skeleton.svg)
- [Walking skeleton · WS-02 Invio e ripetizione senza duplicati](diagrams/PICUS-Walking-Skeleton-Invio.puml) · [PNG](diagrams/PICUS-Walking-Skeleton-Invio.png) · [SVG](diagrams/PICUS-Walking-Skeleton-Invio.svg)
- [Walking skeleton · WS-03 Consultazione e dinieghi](diagrams/PICUS-Walking-Skeleton-Consultazione.puml) · [PNG](diagrams/PICUS-Walking-Skeleton-Consultazione.png) · [SVG](diagrams/PICUS-Walking-Skeleton-Consultazione.svg)
- [PICUS - Schema ER PostgreSQL](diagrams/PICUS-Schema-ER.puml) · [Anteprima PNG](diagrams/PICUS-Schema-ER.png)
- [PICUS - C&C: panoramica](diagrams/C%26C/Diagramma-C%26C.dot) · [PNG](diagrams/C%26C/Diagramma-C%26C.png) · [SVG](diagrams/C%26C/Diagramma-C%26C.svg)
- [PICUS - C&C: Web Tier](diagrams/C%26C/web-C%26C.dot) · [PNG](diagrams/C%26C/web-C%26C.png) · [SVG](diagrams/C%26C/web-C%26C.svg)
- [PICUS - C&C: Core Services](diagrams/C%26C/core-C%26C.dot) · [PNG](diagrams/C%26C/core-C%26C.png) · [SVG](diagrams/C%26C/core-C%26C.svg)

La vista dei moduli riprende i package di `src/main/java` presenti in `main` al commit `f3c4e95`. Le dipendenze `«import»` corrispondono agli import Java delle API; le dipendenze `«use»` rappresentano le collaborazioni previste con `ComunicazioniApi` e `CatalogoApi`. I contratti pubblici sono presenti, mentre le relative implementazioni sono ancora da completare.

Sotto la radice Java `it.unina.picus` i moduli corrispondono ai package `utenze` (M1), `catalogo` (M2), `richieste` (M3), `comunicazioni` (M4) e `monitoraggio` (M5). Ogni modulo contiene `web`, `api` e `interno`. In `richieste.interno` sono predisposti `applicazione`, `dominio` e `persistenza`; in `utenze.interno` e `catalogo.interno` è presente `persistenza`. `shared` è predisposto per i componenti tecnici condivisi. Ogni collaborazione tra moduli passa per le interfacce e i DTO del package `api` del modulo chiamato.

A regime, i controller di `web` invocano i casi d’uso del proprio modulo. In `interno.dominio` risiedono le regole e le interfacce dei repository; gli adattatori di `interno.persistenza` le implementano con JPA. L’eventuale package `interno.integrazioni` resta un’estensione prevista, non ancora presente in `main`. Spring collega le implementazioni ai servizi tramite dependency injection via costruttore. [Dependency injection in Spring](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)

PICUS usa un unico database PostgreSQL per tutti i moduli, inclusi file e metadati degli allegati. Ciascun modulo accede ai propri dati tramite i suoi repository. [ADR-001](../adr/ADR-001-monolite-modulare-mvc.md) · [Task di predisposizione dell’ambiente](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/77)

## Come leggere le viste Componenti e Connettori

Le tre viste C&C descrivono la struttura runtime del monolite a partire da
`main` al commit `39fe03d`. Comprendono anche componenti previsti, indicati
in giallo, con tratteggio o con l'etichetta `[previsto]`. La panoramica
raggruppa Web Tier e Core Services nello stesso processo JVM; le viste 2 e 3
ne dettagliano i componenti e mantengono gli stessi contratti sul confine.
I gruppi rappresentano responsabilità runtime, non singole classi o processi
separati. Le frecce indicano l'invocante e il destinatario; per le chiamate
sincrone il risultato o l'errore torna sullo stesso connettore. I codici
rimandano alle legende, mantenendo le linee orizzontali o verticali e le
etichette fuori dai collegamenti. Le altre viste hanno un proprio riferimento
di versione.

| Contratto aggregato | API Java e stato al riferimento della vista |
| --- | --- |
| API Utenze | `UtenzeApi`: credenziali, verifica dell'account e consultazione dei profili disponibili; gestione completa, attivazione e recupero previsti. |
| API Catalogo | `ConsultazioneCatalogoApi` e `RicercaErogatoriApi` disponibili; le restanti operazioni di `CatalogoApi` sono previste. |
| API Richieste | `InvioRichiesteApi`, `CoordinamentoRichiesteApi` e servizio di risposta alla disponibilità disponibili; `RichiesteApi` raccoglie anche contratti per incrementi successivi. |
| API Comunicazioni | `AvvisiOperativiApi` disponibile; invii generali, allegati e riscontri di `ComunicazioniApi` previsti. |
| API Monitoraggio | Contratti di `MonitoraggioApi` e regole di dominio presenti; controller, casi d'uso e persistenza previsti. |

Le interfacce aggregate rendono leggibile la panoramica: non richiedono una
facciata Java aggiuntiva. Le API scambiano DTO, identificativi e altri valori
di contratto; le entità JPA rimangono nel modulo proprietario.

| Connettore | Ruoli, scambi e comportamento |
| --- | --- |
| Accesso web | Browser client e Web Tier server; HTTP nella demo locale, HTML, JSON, risorse statiche e cookie di sessione. |
| Chiamata API Java | Invocante e fornitore nello stesso processo; chiamata sincrona con risultato o errore restituito al chiamante. |
| Accesso ai dati | Repository/adattatori del modulo proprietario verso PostgreSQL tramite JPA, Hibernate e pgJDBC; un database condiviso. |
| Invio email | Collaborazione prevista dell'adattatore di M1 con un servizio esterno ancora da configurare. |

Nella Vista 2 Spring Security consulta Utenze per autenticare e verificare
l'account della sessione, prima dell'accesso ai controller. Login e logout
sono gestiti dalla catena di sicurezza. Spring MVC seleziona l'handler e
coordina binding, validazione e produzione della risposta: risoluzione della
vista e Thymeleaf per HTML, conversione dei DTO per JSON, oppure redirect ed
esiti senza corpo. Le pagine applicative aggiuntive, compresa la composizione
delle viste Richieste con dati di Utenze e Catalogo, sono indicate come
previste rispetto al riferimento di `main`. I controller JSON di Richieste
già presenti delegano ai servizi del proprio modulo.

Nella Vista 3 Richieste consulta Catalogo, che risolve i profili tramite
Utenze, e invoca Comunicazioni per gli avvisi operativi. Comunicazioni usa
la porta `DestinatariAvvisi`, implementata da Richieste, per verificare la
coerenza di pratica, contatto e destinatario. Questo callback è una
collaborazione runtime: l'interfaccia è definita in `comunicazioni.api`,
senza un import degli interni di Richieste da parte di Comunicazioni.

La creazione di un contatto e il relativo avviso in-app condividono la
transazione locale. La casella degli avvisi viene consultata tramite HTTP;
il diagramma non introduce consegna in background. La persistenza è
raggruppata graficamente: ciascun modulo conserva l'accesso ai propri dati.
Il database unico ospiterà anche file e metadati degli allegati previsti.
I controlli su titolarità e assegnazione restano nei casi d'uso, oltre ai
permessi sulle funzioni verificati dalla sicurezza web.
[ADR-001](../adr/ADR-001-monolite-modulare-mvc.md) ·
[ADR-003](../adr/ADR-003-isolamento-e-validazione-richieste.md)

### Rigenerare le immagini C&C

I sorgenti C&C usano Graphviz DOT per fissare le righe della griglia e i
connettori ortogonali. La palette riprende le viste MVC, moduli e deployment.
Le versioni SVG permettono di ingrandire testo e linee senza perdere nitidezza.
Con Graphviz disponibile nel `PATH`, dalla radice del progetto:

```powershell
$ccDirectory = Join-Path (Get-Location) 'docs/architecture/diagrams/C&C'
foreach ($ccSource in (Get-ChildItem -LiteralPath $ccDirectory -Filter '*.dot')) {
    dot -Tsvg $ccSource.FullName -o ([IO.Path]::ChangeExtension($ccSource.FullName, '.svg'))
    if ($LASTEXITCODE -ne 0) { throw "Errore SVG: $($ccSource.Name)" }
    dot -Gdpi=140 -Tpng $ccSource.FullName -o ([IO.Path]::ChangeExtension($ccSource.FullName, '.png'))
    if ($LASTEXITCODE -ne 0) { throw "Errore PNG: $($ccSource.Name)" }
}
```

Il secondo comando richiede un'installazione Graphviz con supporto PNG.
Il rendering verifica anche la sintassi dei sorgenti. Controllare le tre
immagini dopo la rigenerazione, in particolare frecce, allineamenti e legende.

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

Le tre sequenze descrivono accesso, invio in `CREATA` e consultazione rispetto
al codice di `main` al commit `39fe03d`, lo stesso riferimento delle viste C&C.
Il percorso resta quello del walking skeleton: non introduce le operazioni di
coordinamento degli incrementi successivi. Le risposte applicative mostrate
sono JSON; le pagine aggiuntive restano previste rispetto a questa baseline.
I casi negativi sono collegati alla [#78](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/78).

- **WS-01** mostra accesso, controllo comune delle richieste protette e logout.
  Il controllo precede il binding HTTP: un diniego termina quella richiesta.
  Ogni account usa una sessione distinta. Il login JSON risponde `204`, `401`
  per credenziali errate/account non attivo e `429` per il blocco temporaneo.
  Dopo cinque fallimenti in cinque minuti, i tentativi successivi entro
  sessanta secondi dall'ultimo sono bloccati. Per una sessione non valida le
  API rispondono `401`, mentre le pagine HTML rimandano al login con `302`.
  Ruolo non autorizzato: `403`. Il logout JSON invalida la sessione e risponde
  `204`; il form HTML usa il reindirizzamento al login. Le protezioni tecniche
  dei comandi restano implicite nel ruolo di Spring Security.
- **WS-02** mostra le due letture del catalogo, indipendenti, e l'invio della
  OPT. I campi vengono controllati prima di costruire il DTO, poi il caso
  d'uso verifica e normalizza i dati. Un invio riconosciuto con gli stessi
  dati restituisce la richiesta già salvata; con dati diversi restituisce
  `409`. Il riferimento del singolo invio è facoltativo e si riutilizza se
  l'esito del tentativo precedente è incerto. Un nuovo invio resta distinto,
  anche con contenuto uguale. Il vincolo nel database gestisce anche i
  tentativi concorrenti. Il successo restituisce `201`, la sintesi e
  l'indirizzo del dettaglio dopo il commit della transazione.
- **WS-03** mostra elenco della OPT titolare, coda del Banco e dettaglio con
  verifica della relazione con la pratica. Le letture non cambiano lo stato.
  Il caso d'uso risolve i nomi delle misure tramite Catalogo. La coda comprende
  Creata, Presa in carico e In programmazione, ordinate per data di invio e ID.
  Il dettaglio inesistente produce `404`; l'accesso estraneo produce `403`.
  L'eventuale erogatore assegnato riceve solo i dati autorizzati.

I riquadri `ref` di WS-02 e WS-03 richiamano il controllo comune di WS-01:
l'operazione prosegue soltanto se ammessa. Gli errori dei dati d'invio o degli
identificativi HTTP mostrati in WS-02 e WS-03 restituiscono `400`, senza nuova
richiesta. I riquadri `break` interrompono l'interazione
nel caso indicato. Frecce continue per le invocazioni, tratteggiate per le
risposte; colori coerenti con il C&C e ordine dei partecipanti stabile nelle
sequenze che mostrano gli stessi componenti.

| Confine | Dati scambiati |
| --- | --- |
| Spring Security → `utenze.api` | Verifica delle credenziali e, per le richieste protette, verifica dell’account ricavato dalla sessione. Il controller riceve l’identità verificata e la passa al caso d’uso. |
| `richieste` → `catalogo.api` | `ConsultazioneCatalogoApi` risolve misura, comune e nome della misura da includere nei DTO. |
| Browser → `richieste.web` | JSON con misura, persona/nucleo, comune e `description` facoltativa; il controller costruisce `DatiInvio` e chiama `InvioRichiesteApi`. |
| `richieste` → browser | DTO JSON di elenco e dettaglio con identificativo, data, misura e stato. Descrizione e identificativo del titolare sono riservati a OPT titolare e Banco. |

Il server ricava la titolarità dalla sessione e dai dati persistiti. Le entità
JPA restano nel modulo che le gestisce. Le frecce verso PostgreSQL riassumono
l'accesso tramite i repository JPA del modulo. La descrizione è restituita
come testo: il client la visualizza senza interpretarla come HTML. I valori
respinti non sono riportati negli errori o nei log applicativi.
[ADR-003](../adr/ADR-003-isolamento-e-validazione-richieste.md)

I tre sorgenti condividono lo [stile del walking skeleton](diagrams/PICUS-Walking-Skeleton-style.iuml).
Per rigenerare PNG e SVG con Java e PlantUML, dalla radice del progetto:

```powershell
java -jar <percorso-plantuml.jar> -charset UTF-8 -tpng 'docs/architecture/diagrams/PICUS-Walking-Skeleton*.puml'
java -jar <percorso-plantuml.jar> -charset UTF-8 -tsvg 'docs/architecture/diagrams/PICUS-Walking-Skeleton*.puml'
```

Controllare tutte le esportazioni dopo la rigenerazione: frecce orizzontali,
testi leggibili, assenza di sovrapposizioni e corrispondenza con i sorgenti.

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
`PATH`. Maven risolve il classpath di compilazione per includere anche le
classi applicative che usano librerie esterne.

```powershell
.\mvnw.cmd dependency:build-classpath '-DincludeScope=compile' '-Dmdep.outputFile=target/javadoc-classpath.txt'
$javadocClasspath = (Get-Content -Raw target/javadoc-classpath.txt).Trim()
javadoc -classpath $javadocClasspath -d target/apidocs '@scripts/javadoc.args'
Copy-Item -LiteralPath docs/architecture/picus-logo.svg -Destination target/apidocs/resource-files/picus-logo.svg
```

Aprire `target/apidocs/index.html`. Il sito include i package di
`src/main/java`, compresi i componenti con visibilità di package. La panoramica
offre collegamenti ai moduli e alla ricerca. Le schede dell'elenco dei package
filtrano per modulo. La generazione verifica la sintassi e i riferimenti dei
commenti in tutto il codice applicativo. Il workflow controlla anche la
completezza del Javadoc dei contratti pubblici con `scripts/javadoc-api.args`.

Il [foglio di stile del Javadoc](picus-javadoc.css) riprende la palette e la
tipografia della landing. Il generatore lo include con `--add-stylesheet`
nelle anteprime locali e nei file pubblicati su GitHub Pages. Il pulsante
nell'intestazione alterna i temi chiaro e scuro. Il browser salva la scelta
per le visite successive quando lo storage locale è disponibile.

Il workflow usa lo stesso file di argomenti e include il logo SVG che
collega alla landing page. Quando una PR viene integrata in `main`, rigenera
la documentazione e la pubblica su GitHub Pages. Le istruzioni
di attivazione sono nella [configurazione GitHub](../../.github/SETUP.md#pubblicazione-del-javadoc).
