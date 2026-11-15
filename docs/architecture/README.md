# Architettura

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
| [MonitoraggioApi](../../src/main/java/it/unina/picus/monitoraggio/api/MonitoraggioApi.java) | Conteggi delle OPT e schede trimestrali degli operatori. |

Il walking skeleton usa Utenze e Catalogo per l'identità della OPT e i
riferimenti a misura e comune. Per la ricerca, Catalogo consulta Utenze e
combina i profili degli operatori con le offerte delle OPT. Richieste ordina
le opzioni usando anche il carico di appuntamenti e determina a chi far
recapitare gli avvisi. Monitoraggio usa Utenze e Catalogo per risolvere i
riferimenti dei dati trimestrali. [Vista dei moduli](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/baseline-architetturale-viste-e-adr/docs/architecture/diagrams/PICUS-Diagramma-Moduli.mmd)

Le interfacce descrivono gli esiti attesi per le successive implementazioni.
Prima di rendere disponibile la presa in carico occorre adeguare la
persistenza allo stato `PRESA_IN_CARICO`. Negoziazione, ritiro dei contatti e
tracciato definitivo delle schede si dettagliano nelle relative storie.
[ADR-002](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/baseline-architetturale-viste-e-adr/docs/adr/ADR-002-ciclo-di-vita-richiesta.md)
· [Registro dei chiarimenti](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/baseline-architetturale-viste-e-adr/docs/requirements/open-questions.md)

## Generare il Javadoc

Dalla radice del clone, con il comando `javadoc` del JDK 25 disponibile nel
`PATH`, eseguire:

```text
javadoc -d target/apidocs '@scripts/javadoc.args'
```

Aprire `target/apidocs/index.html`. Aggiornare nei sorgenti il Javadoc dei
contratti quando cambiano dati, permessi, precondizioni o esiti. Il comando
verifica la documentazione e rigenera le pagine HTML in `target`.

Il [foglio di stile del Javadoc](picus-javadoc.css) riprende la palette e la
tipografia della landing. Il generatore lo include con `--add-stylesheet`
nelle anteprime locali e nei file pubblicati su GitHub Pages.

Il workflow usa lo stesso file di argomenti. Quando una PR viene integrata in
`main`, rigenera la documentazione e la pubblica su GitHub Pages. Le istruzioni
di attivazione sono nella [configurazione GitHub](../../.github/SETUP.md#pubblicazione-del-javadoc).
