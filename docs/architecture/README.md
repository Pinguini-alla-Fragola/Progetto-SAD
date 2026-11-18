# Architettura

## Diagrammi

| Diagramma | Anteprima | Descrizione |
| --- | --- | --- |
| System Context | ![System Context](diagrams/system-context/PICUS-System-Context.png) | Confini di PICUS e soggetti che interagiscono con il sistema. |
| Flusso dell’architettura MVC | ![Flusso MVC](diagrams/architettura-mvc/PICUS-Architettura-MVC.png) | Percorso di una richiesta dal browser alla pagina di risposta. |
| Module & Package View | ![Vista di moduli e package](diagrams/module-package-view/PICUS-Module-Package-View.png) | Organizzazione dei moduli e dei package Java. |
| Deployment & Container View | ![Vista di deployment](diagrams/deployment-container-view/PICUS-Deployment-Container-View.png) | Applicazione e database nell’ambiente Docker Compose. |
| CC-01 · Struttura runtime | ![Struttura runtime](diagrams/C%26C/Diagramma-C%26C.png) | Collegamenti tra browser, applicazione, database e servizio email. |
| CC-02 · Ingresso web e accesso ai dati | ![Ingresso web e accesso ai dati](diagrams/C%26C/Monolite-C%26C.png) | Passaggio delle richieste dal web ai moduli e accesso a PostgreSQL. |
| CC-03 · Collaborazioni tra componenti | ![Collaborazioni tra componenti](diagrams/C%26C/Collaborazioni-C%26C.png) | Interazioni tra i cinque componenti applicativi. |
| Walking skeleton 1 · Accesso e invio | ![Accesso e invio](diagrams/walking-skeleton/01-accesso-invio/PICUS-Walking-Skeleton.png) | Accesso della OPT e invio di una richiesta. |
| Walking skeleton 2 · Presa in carico e contatto | ![Presa in carico e contatto](diagrams/walking-skeleton/02-presa-in-carico/PICUS-Walking-Skeleton-2-Presa-in-carico.png) | Presa in carico, ricerca e contatto degli erogatori. |
| Walking skeleton 3 · Risposta e assegnazione | ![Risposta e assegnazione](diagrams/walking-skeleton/03-assegnazione/PICUS-Walking-Skeleton-3-Assegnazione.png) | Risposta dell’erogatore e assegnazione della richiesta. |
| Walking skeleton 4 · Conferma o rifiuto | ![Conferma o rifiuto](diagrams/walking-skeleton/04-esito/PICUS-Walking-Skeleton-4-Esito.png) | Conferma dell’appuntamento o rifiuto della richiesta. |
| Stati della richiesta | ![Stati della richiesta](diagrams/stati-richiesta/PICUS-Stati-Richiesta.png) | Stati della richiesta e passaggi da uno stato all’altro. |
| Schema ER PostgreSQL | ![Schema ER](diagrams/schema-er/PICUS-Schema-ER.png) | Tabelle e relazioni della persistenza iniziale. |

## Javadoc

Il [Javadoc di PICUS](https://pinguini-alla-fragola.github.io/Progetto-SAD/) documenta i package e le classi Java.
