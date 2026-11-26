# Contribuire a PICUS

PICUS è il progetto del team Pinguini alla Fragola per Software Architecture
Design. Consultare la [documentazione](../docs/README.md) e le issue pertinenti
prima di proporre modifiche.

## Issue e pianificazione

Usare i sette form del template del corso: Epic, User Story, Quality Scenario,
Architecture Task, Technical Task, Spike e Bug. Le etichette di riferimento
sono elencate in [labels.yml](labels.yml). Il form Epic mantiene il tipo
nativo `Epic`; l'allineamento dei campi del Project sarà concordato dal team.

Per le domande al committente usare il form **Domanda al committente**:
assegna il tipo nativo `Question` e richiede soltanto il testo della domanda.

La label `blocked` segnala un impedimento e si accompagna al motivo e alla
dipendenza nell'issue. Il tipo rimane quello del lavoro da svolgere.

Priorità e ordine del Product Backlog sono curati dal PO; il team concorda il
lavoro sostenibile e si organizza durante il planning. Lo Scrum Master facilita
il processo e la rimozione degli impedimenti. Assegnatari e iterazione non sono
precompilati dai template. Gli assegnatari indicano chi svolge davvero il lavoro.

Gli stati del Project sono `Backlog`, `Ready`, `In progress`, `In review`, `Done`.
Lo stato `Done` richiede il risultato atteso, le verifiche pertinenti e la review;
completare una task di analisi non equivale a implementare la relativa Story.

## Branch, commit e Pull Request

1. Collegare il lavoro a un'issue e controllare branch e modifiche locali.
2. Creare un branch dedicato: `<type>/<issue-o-task-slug>`, per esempio
   `docs/s1-01-contesto` oppure `feat/catalogo-misure`.
3. Preservare il lavoro altrui e mantenere la modifica focalizzata.
4. Eseguire le verifiche appropriate e controllare il diff.
5. Usare Conventional Commits: `<type>(<scope>): <descrizione>`.
6. Aprire una PR con il template. Usare lo stato Draft se è ancora in lavorazione.
   Quando è pronta, verificare chi è già stato richiesto da GitHub e, se serve,
   chiedere la review a una persona che conosce l'area interessata. Attendere
   la review e i controlli richiesti.

Il filtro [Le mie review](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/pulls?q=is%3Apr+is%3Aopen+user-review-requested%3A%40me)
mostra le PR aperte per cui ti è stata richiesta direttamente una review.

Usare `Closes #123` solo quando la PR completa effettivamente quell'issue;
per un contributo parziale usare `Refs #123`. Non chiudere un'Epic soltanto
perché è terminata una sua attività preparatoria.

Evitare push diretti ai branch protetti, force push e cambiamenti di storia
non concordati.

## Proprietari della configurazione

I code owner di `.gitignore` e dell'intera cartella `.github/`, compreso
`CODEOWNERS`, sono esclusivamente **@CapHello e @giob22**. Le modifiche a
questi percorsi vanno concordate con loro e approvate da uno dei due, diverso
dall'autore della PR, prima dell'integrazione.

`CODEOWNERS` assegna le revisioni e non vieta tecnicamente ad altri account di
creare un branch, modificare file o proporre una PR. Non costituisce un sistema
di permessi di scrittura per singolo percorso.

## Verifiche e criteri di completamento

Durante la preparazione si verificano fonti, termini, processi as-is/to-be,
criteri misurabili e tracciabilità tra requisiti, storie e scenari. Separare
richieste della fonte, requisiti concordati, proposte, ipotesi e domande aperte.
L'implementazione non è richiesta nell'iterazione preparatoria.

Quando esistono `pom.xml` e Maven Wrapper, eseguire i test pertinenti e almeno:

```bash
bash ./mvnw test
```

In PowerShell:

```powershell
.\mvnw.cmd test
```

La CI esegue `bash ./mvnw --batch-mode --no-transfer-progress verify` con
Java 25 quando rileva il progetto Maven completo del wrapper. Il report JaCoCo
si genera automaticamente su `main` o tramite l'[avvio manuale di Java CI](SETUP.md#workflow-e-dipendenze).
Per le PR che modificano soltanto il README principale, file Markdown in `docs/` o
`.github/`, oppure PDF in `docs/`, il check Java indica nel riepilogo che la
suite è stata saltata. Se i test Java falliscono, il riepilogo elenca i casi
falliti e i comandi per ripeterli in locale. Se manca il POM, il check indica
che la compilazione e i test non sono stati eseguiti. Il workflow di
[avvio Docker](workflows/docker-smoke.yml) costruisce l'immagine e verifica
`/login` con un database vuoto quando cambiano configurazione o migrazioni.
Lo scheletro mantiene il test di caricamento del contesto del template,
con JUnit Jupiter 5 o successivo nella versione gestita da Spring Boot.
Per il codice aggiungere o aggiornare i test pertinenti; per la documentazione
fornire una verifica coerente con l'artefatto. Nessuna credenziale deve essere
inserita nel codice o nei file condivisi.

## Dati e sicurezza

Usare dati sintetici. Le persone assistite non sono utenti dell'applicazione
e non se ne registrano dati identificativi. I dati personali degli operatori
richiedono controllo degli accessi e non vanno inseriti in esempi pubblici.
Le segnalazioni di vulnerabilità seguono la [policy di sicurezza](SECURITY.md).
