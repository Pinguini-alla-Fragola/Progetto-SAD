# Attivazione della configurazione GitHub

I file in questa cartella preparano template, istruzioni, proprietari delle
review e workflow. Le modifiche alle impostazioni remote restano a carico dei
maintainer umani; aggiungere questi file non abilita da solo le protezioni.
I form delle issue devono essere pubblicati nel branch predefinito perché
GitHub li mostri; `CODEOWNERS` deve esistere nei branch di destinazione delle PR.

## Code owner e protezione dei branch

Il [CODEOWNERS](CODEOWNERS) assegna `.gitignore` e tutti i file di `.github/`
esclusivamente a **@CapHello e @giob22**. Sugli altri file, senza un proprietario
generale, non parte più la richiesta automatica di review a tutto il team
`developers`. Resta comunque in vigore il ruleset di `main`: richiede almeno
un'approvazione da un revisore autorizzato diverso da chi ha effettuato l'ultimo
push, su qualunque file. Sui percorsi riservati vale anche il requisito
CODEOWNERS descritto sopra, e la stessa approvazione può soddisfare entrambi i
requisiti. Il ruleset attuale non impone invece la risoluzione delle
conversazioni prima del merge.

Dopo la pubblicazione umana dei file nel branch di destinazione:

1. Verificare che CapHello e giob22 abbiano accesso Write o superiore alla repo.
2. Verificare che il team `developers` esista, sia visibile e abbia Write.
3. In **Settings → Rules → Rulesets**, configurare una regola attiva per `main`
   e per `develop` se il team lo utilizza; in alternativa adeguare le branch
   protection già esistenti.
4. Richiedere una Pull Request, almeno una approvazione e **Require review from
   Code Owners**. Per i percorsi riservati l'approvazione deve provenire da
   CapHello o giob22; se uno dei due è autore, serve l'altro.
5. Invalidare le approvazioni dopo modifiche sostanziali alla PR e richiedere
   la risoluzione delle conversazioni prima del merge.
6. Impedire force push e cancellazione dei branch protetti; controllare che
   bypass di amministratori, app o ruoli non aggirino la review richiesta.
7. Dopo la prima esecuzione dei workflow, selezionare come controlli richiesti
   **Markdown e link locali** e **Build e test Java**, verificando i nomi
   effettivamente mostrati da GitHub.

Una delle due approvazioni è sufficiente: `CODEOWNERS` non richiede che
approvino entrambi. Non limita chi può creare modifiche o proporre PR. La regola
organizzativa è che i cambiamenti ai percorsi riservati siano concordati con i
due maintainer; il blocco tecnico ottenibile con questa configurazione riguarda
l'integrazione senza la loro review. Un file YAML in `.github/` non crea permessi
GitHub di scrittura per percorso.

Riferimento: [code owner e protezioni](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners).

Con **Allow auto-merge** attivo in **Settings → General → Pull Requests**,
chi ha accesso in scrittura può scegliere **Enable auto-merge** su una singola
PR. GitHub esegue il merge dopo le approvazioni e i controlli richiesti dal
ruleset.

## Issue e Project

I form seguono le sette categorie del template del corso: Epic, User Story,
Quality Scenario, Architecture Task, Technical Task, Spike e Bug.
Verificare che il tipo nativo `Epic` esista prima di usare il relativo form.
In aggiunta è disponibile [Domanda al committente](ISSUE_TEMPLATE/question.yml),
con tipo nativo `Question` e un solo campo di testo obbligatorio. Verificare
che `Question` sia abilitato nell'organizzazione e disponibile nella repository.
Le etichette sono elencate in [labels.yml](labels.yml); il file è un elenco
di riferimento e non le crea automaticamente su GitHub.

L'allineamento dei campi, delle viste e dei filtri del Project e le eventuali
automazioni di classificazione saranno concordati insieme alla configurazione
di collaborazione. I form non assegnano persone o sprint automaticamente.
Non modificare gli elementi esistenti del backlog senza una revisione del team.

Nel [Project](https://github.com/orgs/Pinguini-alla-Fragola/projects/2/workflows),
`Item closed` è configurato per le issue e `Pull request merged` porta a
`Done` le PR integrate. Le transizioni delle issue tengono conto anche della
sincronizzazione con il loro stato nativo.

## Workflow e dipendenze

[Documentazione](workflows/docs.yml) verifica Markdown e link locali; i link web
non vengono interrogati. Le regole sono volutamente leggere e non impongono
limiti alle righe o uno stile delle tabelle. Non vengono eseguite correzioni
automatiche né commit dei risultati. Word e PDF richiedono una review visiva:
questi controlli non ne verificano l'impaginazione.

[Link esterni](workflows/link-esterni.yml) verifica gli URL HTTP e HTTPS
nella documentazione ogni lunedì alle 09:35, ora di Roma. Il risultato
compare nel riepilogo di Actions. Il workflow supporta anche **Run workflow**.

[Etichette delle PR](workflows/labeler.yml) applica
[`documentation`, `ci`, `frontend` e `test`](labeler.yml) in base ai percorsi
del diff. `documentation` richiede percorsi documentali per tutti i file
elencati da GitHub. Il job aggiorna queste etichette a ogni push sulla PR
e legge la configurazione dal ramo base tramite API.

[Java CI](workflows/ci.yml) dichiara esplicitamente l'assenza di build e test
finché non esiste `pom.xml`. Quando il POM compare, esige il Maven Wrapper e
usa Java 25 con `verify`. La revisione dei workflow è rinviata al confronto
sulla CI e sulla collaborazione.

[JavaScript CI](workflows/javascript.yml) esegue `node --test src/test/js/*.test.mjs`
con Node.js 24 sulle pull request, sui push a `main` e `develop` e su avvio manuale.

[Verifica diagrammi](workflows/diagrams.yml) rigenera i sorgenti Mermaid, PlantUML
e DOT interessati dalle modifiche. Ogni sorgente deve avere un'anteprima SVG
versionata. Il confronto usa i testi, i riferimenti dei collegamenti Mermaid,
PlantUML e DOT e l'ordine dei messaggi PlantUML, indipendentemente dalle coordinate.
Per i PNG presenti controlla il formato, i checksum dei blocchi e la decodifica.
Una modifica a uno stile `.iuml` verifica tutti i sorgenti PlantUML.
L'avvio da **Actions → Verifica
diagrammi → Run workflow** controlla l'intero insieme di diagrammi.

In **Settings → Actions → General** mantenere il token di default in sola
lettura, limitare le Action consentite a quelle revisionate e non abilitare
la creazione/approvazione automatica delle PR. Le Action nei workflow sono
fissate a SHA completo e il checkout non conserva credenziali Git. Non eseguire
codice delle PR in workflow privilegiati `pull_request_target`.

La [configurazione Dependabot](dependabot.yml) è predisposta per le Action,
con limite delle PR di versione pari a zero. Gli aggiornamenti e i commit
restano umani. Abilitare **Dependabot alerts**, ma lasciare disattivate le PR
automatiche di **Dependabot security updates** se si mantiene questa policy.
Aggiungere l'ecosistema Maven soltanto quando esiste il relativo POM.

Riferimenti: [sicurezza delle Action](https://docs.github.com/en/actions/reference/security/secure-use)
e [opzioni Dependabot](https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference).

## Pubblicazione del Javadoc e della copertura dei test

[Controllo Javadoc](workflows/javadoc-pr.yml) esegue la stessa verifica delle
API pubbliche prima del merge delle PR che modificano sorgenti o configurazione
della build e della documentazione Java.

Il workflow [Pubblica Javadoc](workflows/javadoc.yml) si avvia dopo una
[Java CI](workflows/ci.yml) riuscita su `main`. Genera i Javadoc con Java 25,
verifica le API pubbliche e aggiunge in `copertura/` il report JaCoCo dello
stesso commit, con il tema PICUS applicato dalla CI. Aggiorna il branch
`javadoc` quando i file cambiano e pubblica lo stesso sito su GitHub Pages.

Un maintainer abilita la pubblicazione in
[Settings → Pages](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/settings/pages)
selezionando **GitHub Actions** in **Build and deployment → Source**.
Se l'ambiente `github-pages` ha regole di deploy, queste devono consentire
`main`. GitHub Pages riceve l'artefatto del workflow perché i push eseguiti
con `GITHUB_TOKEN` sul branch di output non avviano una build Pages.
[Configurazione della sorgente](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)

Per ripetere la pubblicazione, usa **Actions → Pubblica Javadoc → Run workflow**
su `main` se l'artefatto della Java CI per il commit corrente è ancora
disponibile. Per rigenerarlo, avvia **Java CI** su `main`. L'URL compare
nell'ambiente `github-pages` dopo il deploy. La landing di PICUS mostra il
link **Copertura dei test** quando il report è disponibile sul dominio dei Javadoc.

## Sicurezza e accesso

### CFG-05: secret scanning e segnalazioni private

Con un account amministratore della repository, aprire
[Settings → Advanced Security](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/settings/security_analysis).

1. Cercare **Secret Protection** o **Secret scanning**. Se compare **Enable**,
   abilitare la funzione e confermare. Nelle repository pubbliche la scansione
   è gratuita; se risulta già **Enabled**, non occorre riattivarla. La sola
   assenza di alert non dimostra che la scansione sia disattivata.
2. Nella stessa pagina, abilitare **Private vulnerability reporting**. È
   l'impostazione che rende operativo il canale della
   [Security policy](SECURITY.md).
3. Verificare in **Security → Secret scanning** che la funzione sia attiva.
   In **Security → Advisories** verificare il pulsante **Report a vulnerability**,
   disponibile ai segnalanti quando le segnalazioni private sono abilitate.

Se un'impostazione è bloccata da una configurazione dell'organizzazione,
deve intervenire un owner o security manager dell'organizzazione.
La **Push protection** è una protezione aggiuntiva da verificare nella stessa
pagina: il suo stato non coincide con quello del secret scanning.

Riferimenti: [abilitare secret scanning](https://docs.github.com/en/code-security/how-tos/secure-your-secrets/detect-secret-leaks/enable-secret-scanning)
e [configurare le segnalazioni private](https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/configure-vulnerability-reporting/configure-for-a-repository).

### Altri controlli di sicurezza

Abilitare la scansione del codice quando sarà presente codice supportato,
verificando la compatibilità degli strumenti con Java 25.

Verificare i permessi dei team dopo il trasferimento: assegnare solo gli accessi
necessari e limitare il ruolo Admin ai maintainer che gestiscono la repository.
Credenziali CI eventuali vanno in GitHub Secrets; non in file, template o issue.

Riferimento: [impostazioni di sicurezza](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-security-and-analysis-settings-for-your-repository).

## Bozza di release dello sprint

In **Actions → Prepara la release dello sprint → Run workflow**, selezionare
`main` e indicare il tag dello sprint già presente su GitHub. Il
[workflow](workflows/release-sprint.yml) usa il commit del tag per generare
`PICUS.jar` e allegare il PDF di progetto. La bozza contiene le
[note di rilascio generate](release.yml) e il riepilogo della run contiene
il link per aprirla. Si può ripetere l'esecuzione per completare la bozza
con gli allegati mancanti.

## Burndown: perimetro e aggiornamento

Il [calcolo](../scripts/burndown.py) conta soltanto le issue di tipo nativo `Task`,
cioè i task tecnici. Story, Epic, Bug, Question, Pull Request e bozze non entrano
in scope, completati e rimanenti. Gli item restano nel Project e possono mantenere
l'iterazione; non occorre spostarli per correggere il grafico.

I punti di un task sono solo il valore del campo `Estimate`: la taglia `Size`
non viene usata. Un task senza `Estimate` vale 0 punti, quindi va stimato per
comparire nel grafico. Il riepilogo della run elenca i task dello sprint
senza stima e quelli `In progress` senza assegnatario, con i link alle issue.

Dopo aver integrato i file in `main`, aprire **Actions → burndown → Run workflow**,
selezionare `main` e lasciare vuoto il titolo per aggiornare l'iterazione corrente.
Il workflow verifica il filtro prima di generare CSV e SVG e pubblica il grafico
su `burndown-data`. Il README mostrerà la nuova versione dopo l'esecuzione;
gli sprint già conclusi non vengono rigenerati dal normale aggiornamento.

Per controllare i test in locale:

```bash
python -m unittest discover -s scripts -p 'test_burndown.py'
```
