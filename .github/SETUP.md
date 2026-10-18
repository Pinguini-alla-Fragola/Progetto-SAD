# Attivazione della configurazione GitHub

I file in questa cartella preparano template, istruzioni, proprietari delle
review e workflow. Le modifiche alle impostazioni remote restano a carico dei
maintainer umani; aggiungere questi file non abilita da solo le protezioni.
I form delle issue devono essere pubblicati nel branch predefinito perché
GitHub li mostri; `CODEOWNERS` deve esistere nei branch di destinazione delle PR.

## Code owner e protezione dei branch

Il [CODEOWNERS](CODEOWNERS) assegna `.gitignore` e tutti i file di `.github/`
esclusivamente a **@CapHello e @giob22**. Le regole specifiche prevalgono sulla
regola generale del team `@Pinguini-alla-Fragola/developers`.

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

## Issue e Project

Nelle impostazioni dell'organizzazione mantenere i tipi **Epic, Story, Task,
Bug, Question**. I cinque form in `ISSUE_TEMPLATE/` usano esattamente quei nomi.
`blocked` resta una label, con motivo e dipendenza espliciti nell'issue.

I form non impongono assegnatari, priorità o sprint. Questi valori si gestiscono
nel Project e nel planning. Durante la migrazione assegnare prima il tipo
nativo alle issue, poi aggiornare i filtri e infine rimuovere le label `type:*`
diventate ridondanti.

- Vista Story: usare `type:"Story"` al posto di `label:"type:story"`.
- Vista domande: usare `type:"Question"` al posto di `label:"type:question"`.
- Vista sprint: mantenere il filtro sull'iterazione concordata.
- Mostrare il campo Type e verificare la presenza degli elementi già migrati.

Il numero del nuovo Project non viene presunto: configurare l'auto-add nel
Project dell'organizzazione, limitandolo a questa repository. Verificare i
collegamenti eventualmente ancora diretti al Project personale precedente.
Il workflow delle issue non va confuso con gli stati di dominio delle richieste PICUS.

## Workflow e dipendenze

[Documentazione](workflows/docs.yml) verifica Markdown e link locali; i link web
non vengono interrogati. Le regole sono volutamente leggere e non impongono
limiti alle righe o uno stile delle tabelle. Non vengono eseguite correzioni
automatiche né commit dei risultati. Word e PDF richiedono una review visiva:
questi controlli non ne verificano l'impaginazione.

[Java CI](workflows/ci.yml) dichiara esplicitamente l'assenza di build e test
finché non esiste `pom.xml`. Quando il POM compare, esige il Maven Wrapper e
usa Java 25 con `verify`. Concordare la configurazione delle eventuali prove
con MySQL prima di aggiungere test che dipendono da un database.

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

## Sicurezza e accesso

In **Settings → Advanced Security**, verificare secret scanning e push
protection. Abilitare **Private vulnerability reporting** affinché il canale
descritto nella [Security policy](SECURITY.md) sia disponibile.
Abilitare la scansione del codice quando sarà presente codice supportato,
verificando la compatibilità degli strumenti con Java 25.

Verificare i permessi dei team dopo il trasferimento: assegnare solo gli accessi
necessari e limitare il ruolo Admin ai maintainer che gestiscono la repository.
Credenziali CI eventuali vanno in GitHub Secrets; non in file, template o issue.

Riferimento: [impostazioni di sicurezza](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-security-and-analysis-settings-for-your-repository).

## Documenti condivisi e agenti

[CONTRIBUTING.md](CONTRIBUTING.md) raccoglie il workflow pubblicabile;
[copilot-instructions.md](copilot-instructions.md) lo richiama per l'assistenza IA.
L'eventuale `AGENTS.md` nella cartella padre del workspace resta locale e non
viene incluso nel clone. Non spostarlo automaticamente o presumere che altri
cloni lo abbiano disponibile.

Gli agenti preparano modifiche su nuovi branch locali e non creano commit,
push, PR o attribuzioni da contributore. I workflow qui presenti fanno controlli
e non pubblicano modifiche. Le modalità di agente cloud con commit o push
automatici non sono compatibili con questa regola.
