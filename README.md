# PICUS

Progetto di Software Architecture Design del gruppo **Pinguini alla Fragola**,
a.a. 2026/27, Università degli Studi di Napoli Federico II.

**Docente:** Prof. Anna Rita Fasolino.

PICUS supporta il Banco nel coordinamento delle Organizzazioni Partner
Territoriali, delle richieste di misure di accompagnamento e del monitoraggio.
Il gruppo sta definendo i processi as-is e to-be nell'iterazione preparatoria.

## Team

| Nome | GitHub | Scrum Role |
|------|--------|------------|
| Antonio Caprio *(Referente)* | [@CapHello](https://github.com/CapHello) | Product Owner |
| Gianluca Cinquegrana | [@giallo04](https://github.com/giallo04) | Developer |
| Salvatore Di Costanzo | [@salvatoredc04](https://github.com/salvatoredc04) | Scrum Master |
| Alessandro Fontana | [@AlessandroFontana04](https://github.com/AlessandroFontana04) | Developer |
| Christian Formicola | [@Darkessence97](https://github.com/Darkessence97) | Developer |
| Manuel Fornataro 🐧 | [@man-for04](https://github.com/man-for04) | Developer |
| Giovanni Fragola | [@giob22](https://github.com/giob22) | Developer |
| Roberta Giovengo 🍓 | [@bobby273](https://github.com/bobby273) | Developer |

## Documentazione

- [Requisiti e modelli di analisi](docs/requirements/README.md)
- [Scenari di qualità](docs/quality-scenarios/README.md)
- [Architettura](docs/architecture/README.md)
- [Registro delle decisioni](docs/adr/README.md)
- [Review delle iterazioni](docs/reviews/README.md)
- [Esportazione PDF del documento](docs/Documentazione_SAD_2026.pdf)
- [Regole di contribuzione](.github/CONTRIBUTING.md)


## Interfaccia web MVP

Con il database e il profilo `demo` avviati come descritto sotto, aprire
`http://localhost:8080/login`. L'interfaccia usa le sessioni e la protezione
CSRF di Spring Security.

- La OPT può inviare una richiesta selezionando misura e comune dal catalogo,
  ritrovare le proprie richieste e consultarne il dettaglio. La conferma di invio
  riepiloga misura e comune e permette di aprire subito il dettaglio.
- Il Banco vede la coda in ordine di arrivo e può scegliere l'ordine
  inverso nei filtri. Ogni riga mostra OPT richiedente, stato,
  data e ora; l'identificativo è consultabile nei riferimenti espandibili della riga,
  anche su mobile. Prende esplicitamente in carico
  una richiesta, consulta gli erogatori compatibili e interpella un account
  per volta. Dopo una risposta positiva può assegnare l'erogatore e passare
  la pratica a **In programmazione**.
  La presa in carico richiede una conferma: Annulla ed Escape conservano lo stato.
- In **In programmazione** il Banco registra l’accordo in presenza (luogo e
  referente con telefono) o a distanza (link), con data e ora nel fuso del
  dispositivo indicato nel modulo. Gli errori del server compaiono accanto ai
  campi e conservano i dati. La conferma richiede un riepilogo esplicito e rende
  l’accordo consultabile, senza modifiche, dalla OPT titolare e dall’erogatore.
- Il Banco può rifiutare definitivamente una pratica in ricerca o in
  programmazione, con conferma esplicita. Durante la ricerca il comando rimane
  disabilitato finché ci sono alternative compatibili da valutare o contatti
  in attesa. La OPT titolare vede **Rifiutata** e «Nessun erogatore disponibile».
- La OPT o l'operatore interpellato trova il contatto nella propria area e
  comunica disponibilità o indisponibilità. Dal contatto può aprire la pratica
  dopo l'assegnazione. Il solo candidato vede i dati minimi, mentre la
  descrizione del bisogno resta riservata alla OPT titolare e al Banco.
- Il Banco consulta anche lo storico dei contatti, gli account attivi e il
  catalogo delle misure attive. I pulsanti **Aggiorna** ricaricano gli elenchi.

La disponibilità del candidato e l'assegnazione sono passaggi distinti.
L'apertura del dettaglio conserva lo stato. Se la risposta all'invio viene
persa, **Riprova lo stesso invio** riusa dati e `Idempotency-Key`, senza creare
una seconda pratica. Dopo un ricaricamento, nella stessa scheda, il modulo
recupera il riferimento della OPT autenticata. Reinserendo gli stessi dati si
verifica l'invio originale. Dopo averlo controllato in **Le mie richieste**,
**Ho verificato, inizia una nuova richiesta** consente di inviare dati diversi
e conserva il riferimento precedente per un ritento con gli stessi dati.
La memoria di sessione conserva chiavi, impronte e stato dei riferimenti.
La conferma o un rifiuto certo rimuovono il riferimento del singolo invio.
Con memoria non disponibile il modulo blocca l'invio. Se la sessione cambia
in un'altra scheda, occorre ricaricare il modulo prima di inviare.
Il pulsante viene abilitato solo dopo l'inizializzazione dello script;
il fallback nativo usa POST per tenere i dati fuori dall'URL.

La disponibilità registrata resta visibile anche se il candidato non può
consultare il dettaglio: un `403` non determina lo stato della pratica né
segnala un'assegnazione ancora in attesa.

I controlli senza endpoint restano disattivati: annullamento, archivio degli esiti, gestione delle
utenze e del catalogo, offerta, comunicazioni generali e monitoraggio.
Il messaggio «Funzione non ancora implementata» compare al passaggio del cursore
o con il focus da tastiera, anche sui campi delle anteprime. Il cursore di divieto
segnala i controlli non disponibili; il tooltip flottante segue il puntatore lasciando
un po’ di spazio, può sovrapporsi al riquadro e resta nei bordi della finestra.
Escape lo chiude.
Le regole di autorizzazione di #28 non equivalgono all'implementazione di
tutte queste operazioni. Per una demo della ricerca degli erogatori usare
la [fixture sintetica e la procedura di coordinamento](docs/architecture/api-coordinamento-richieste.md).

I test delle viste e dei permessi sono in `RichiesteVisteTest`; i test del
client HTTP, dei dati dell’accordo e dei vincoli di rifiuto si eseguono con Node.js 22 o successivo:

```bash
node --test src/test/js/*.test.mjs
```

Il login presenta il logo accanto al modulo su desktop e sopra al modulo su
mobile. Il dettaglio separa riepilogo, bisogno, coordinamento e riferimenti;
una pagina 404 dedicata gestisce percorsi e pratiche inesistenti nelle viste,
mentre gli endpoint API conservano gli errori JSON.

## Prerequisiti

- Java **25 LTS**.
- Maven **3.9 o successivo**; il Maven Wrapper permette di usare la versione
  fissata per il progetto senza installare Maven separatamente.
- Spring Boot **4.1.2**, fissato nel POM come base della linea **4.1+**.
- JUnit Jupiter **5 o successivo**, nella versione gestita da Spring Boot.

## Build e test

```bash
mvn verify
```

Con il Maven Wrapper:

```bash
bash ./mvnw verify
```

In PowerShell:

```powershell
.\mvnw.cmd verify
```

`verify` esegue tutti i test, compresi quelli di integrazione su PostgreSQL.
Questi test avviano da soli un container `postgres:17` tramite Testcontainers,
quindi richiedono **Docker in esecuzione** sulla macchina (Docker Desktop su
Windows e macOS). Non serve avviare `docker compose` né configurare un
database: il container viene creato all'inizio dei test e rimosso alla fine.

Senza Docker i test di integrazione falliscono con un errore esplicito di
Testcontainers e la build risulta fallita: non vengono saltati in silenzio.
La CI usa lo stesso comando su un runner che mette a disposizione Docker,
quindi li esegue a ogni pull request.

## Avvio locale con Docker Compose

Da un clone pulito, copia `.env.example` in `.env` e imposta le due password
locali di prova (vedi [Configurazione `.env`](#configurazione-env)). Esegui
dalla radice del progetto:

```bash
docker compose up --build
```

`picus-webapp` risponde su `http://localhost:8080` e usa PostgreSQL nel
servizio `picus-db`. Flyway crea lo schema e carica le 12 misure
dell’Appendice A, i comuni e le adiacenze di esempio. Il profilo `demo`
inserisce quattro account sintetici attivi:

- `banco@picus.test`
- `opt-a@picus.test`
- `opt-b@picus.test`
- `operatore@picus.test`

`PICUS_DEMO_PASSWORD` è la password iniziale degli account demo, salvata
come hash. Il [backend del login](#contratto-http-del-login) risponde su `http://localhost:8080`.
Il volume `picus-dati` conserva le tabelle e i dati fra gli avvii.

Per verificare la predisposizione, esegui `./mvnw clean package`
(`./mvnw.cmd clean package` in PowerShell) e controlla l’avvio dei servizi
con `docker compose ps`.

### Aggiornare una demo avviata con la V5 del login

Se il database locale ha già applicato `V5__blocco_accessi.sql`, ferma la
demo dal progetto Compose usato in precedenza e avvia la versione aggiornata
con un nuovo nome di progetto.

```bash
docker compose down
docker compose -p picus-demo-v2 up --build
```

Il nuovo progetto usa il volume separato `picus-demo-v2_picus-dati`.
Usa `-p picus-demo-v2` anche con i successivi comandi `ps`, `logs` e `down`.
Se la demo precedente usava un nome personalizzato, passalo con `-p` al
primo comando.

### Configurazione `.env`

Il file `.env` sta nella radice del progetto, accanto a `docker-compose.yml`, e
non va versionato. `.env.example` ne elenca le variabili, entrambe obbligatorie:
`docker compose` si ferma con un errore se una è vuota.

| Variabile | Uso |
|-----------|-----|
| `PICUS_DB_PASSWORD` | Password dell’utente `picus` di PostgreSQL, usata da `picus-db` e da `picus-webapp`. |
| `PICUS_DEMO_PASSWORD` | Password iniziale dei quattro account demo, salvata come hash BCrypt. Vale al massimo 72 byte UTF-8. |

## Avvio dello skeleton senza database

Lo *skeleton* avvia l’applicazione con le utenze in memoria: login, area per
ruolo e logout sono verificabili senza PostgreSQL e senza Docker. Dalla radice del
progetto:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=skeleton
```

Con il Maven Wrapper: `bash ./mvnw spring-boot:run -Dspring-boot.run.profiles=skeleton`
(`.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=skeleton"` in PowerShell).

L’app risponde su `http://localhost:8080`. Gli account attivi sono
`banco@picus.test`, `opt@picus.test` e `operatore@picus.test`; la password è
`picus-demo`, modificabile con la proprietà `picus.skeleton.password`. Il
cookie di sessione non è `Secure`, per l’uso locale in http. Il profilo è solo
per sviluppo e test.

## Contratto HTTP del login

Il client usa la sessione Spring Security tramite cookie. Prima di un POST
recupera il token da `GET /api/auth/csrf` nella stessa sessione e invia
`token` nell’header indicato da `headerName`.

- `POST /api/auth/login` riceve un JSON con `email` e `password`. Risponde
  `204` su successo, `401` con `errore` per credenziali errate o account
  non attivo e `429` con `errore` per il blocco temporaneo.
- Dopo il login, `GET /` reindirizza a `/banco/dashboard`, `/opt/dashboard`
  o `/operatore/dashboard` in base al ruolo. Le viste d’area fanno parte
  dell’integrazione dell’interfaccia.
- `POST /api/auth/logout` termina la sessione e risponde `204`. Recupera un
  nuovo token CSRF dopo il login, prima di eseguire il logout o altri POST.
- Spring Security gestisce anche i form su `POST /login` e `POST /logout`.
  Gli script dell’interfaccia sono serviti come risorse dello stesso sito
  secondo la Content-Security-Policy.

### Risposte di accesso negato

Una richiesta è di **API** se il percorso è sotto `/api/`, se è XHR
(`X-Requested-With: XMLHttpRequest`) o se `Accept` preferisce JSON
(`application/json` o un tipo `+json` con qualità maggiore di zero e maggiore
di quella di HTML). Tutto il resto è una **pagina HTML**.

| Caso | Pagina HTML | API |
|------|-------------|-----|
| Senza sessione | `302` verso `/login` | `401`, senza redirect |
| `POST` senza sessione e senza token CSRF | `403` | `401` |
| Sessione revocata (qualunque area o metodo alla prima richiesta) | `302` verso `/login?error`, sessione chiusa | `401`, sessione chiusa |
| Sessione valida, ruolo non abilitato | `403` | `403` |
| Sessione valida, `POST` senza token CSRF | `403` | `403` |

Il controllo della sessione avviene prima di CSRF e dei permessi di ruolo.
Le responsabilità sono in `shared/SecurityConfig` e `shared/RichiestaApi`.

## 🏭 Project management

Il team adotta **Scrum**. User Story, attività e stato di avanzamento
sono tracciati su GitHub Projects:

- [Backlog Progetto-SAD](https://github.com/orgs/Pinguini-alla-Fragola/projects/2)

<!-- BURNDOWN:START — grafico dello sprint in corso, aggiornato ogni sera dal workflow "burndown"; non modificare il link -->
## 📉 Avanzamento dello sprint

![Burndown dello sprint in corso](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/raw/burndown-data/burndown-latest.svg)

*Aggiornato ogni sera. Gli sprint precedenti sono nel branch [`burndown-data`](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/tree/burndown-data).*

Il calcolo esclude le Pull Request e le issue di tipo nativo `Epic`, anche
quando appartengono all'iterazione. Gli altri item mantengono la stima del
Project: `Estimate`, altrimenti `Size`, altrimenti 1 punto.
<!-- BURNDOWN:END -->
