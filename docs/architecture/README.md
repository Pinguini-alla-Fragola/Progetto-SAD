# Architettura

- [CTX-01 · System Context](../requirements/diagrams/contesto/Context%20Diagram.drawio)
- [Diagramma dei container C4 di PICUS a regime](diagrams/PICUS-C4-Container.mmd) · [Anteprima PNG](diagrams/PICUS-C4-Container.png)
- [ARCH-01 · Architettura MVC di PICUS a regime](diagrams/PICUS-Architettura-MVC.mmd) · [Anteprima PNG](diagrams/PICUS-Architettura-MVC.png)
- [Diagramma dei moduli e dei package Java](diagrams/PICUS-Diagramma-Moduli.mmd) · [Anteprima PNG](diagrams/PICUS-Diagramma-Moduli.png)

Nel diagramma dei moduli, ogni freccia parte dal modulo chiamante e punta al modulo di cui usa il package `api`. M1 fornisce identità, ruoli e profili. Ogni modulo applica i controlli di titolarità, assegnazione o ricezione sulle risorse che gestisce. Il tratteggio indica la collaborazione per le notifiche operative, da dettagliare.

Sotto la radice Java `it.unina.picus` i moduli corrispondono ai package `utenze` (M1), `catalogo` (M2), `richieste` (M3), `comunicazioni` (M4) e `monitoraggio` (M5). Ogni collaborazione tra moduli passa per le interfacce e i DTO del package `api` del modulo chiamato. I controller di `web` invocano i casi d’uso del proprio modulo in `interno.applicazione`.

In `interno.dominio` risiedono le regole e le interfacce dei repository. L’applicazione usa queste interfacce e gli adattatori di `interno.persistenza` le implementano con JPA. Gli adattatori di `interno.integrazioni` implementano i contratti richiesti dall’applicazione. Spring collega le implementazioni ai servizi tramite dependency injection via costruttore. [Dependency injection in Spring](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)

PostgreSQL è il database condiviso confermato dal team. Ciascun modulo usa i propri repository per accedere ai dati di sua competenza. Per collaborare, i moduli si scambiano i DTO dichiarati nel package `api` del modulo chiamato. [ADR-001](../adr/ADR-001-monolite-modulare-mvc.md) · [Task di predisposizione dell’ambiente](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/77)

## Primo modulo nel codice

Il modulo `richieste` contiene il modello della richiesta, il contratto `RichiestaRepository` e il servizio applicativo `ServizioRichieste`. Il servizio riceve il repository e l’orologio nel costruttore. Il test Spring usa un repository finto per verificare il salvataggio e lo stato iniziale. `web`, `persistenza` e `api` delimitano i punti in cui inserire controller, adattatore JPA e contratti tra moduli. [ServizioRichieste](../../src/main/java/it/unina/picus/richieste/interno/applicazione/ServizioRichieste.java) · [Test di wiring](../../src/test/java/it/unina/picus/richieste/interno/applicazione/ServizioRichiesteTests.java)

## Come leggere ARCH-01

- **Ingresso.** Il browser invia una richiesta HTTPS con metodo HTTP e percorso. Spring Security riconosce la sessione e verifica l’accesso alla funzione. Se la richiesta prosegue, il `DispatcherServlet` usa metodo e percorso per individuare il metodo del controller a cui inoltrarla. [Flusso di Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/sequence.html)
- **Dal Controller al Modello e alla Vista.** Il controller espone la funzione al browser tramite un endpoint HTTP. Chiama un servizio Java del modulo, riceve il risultato e lo usa per preparare i dati da mostrare. Consegna a Spring MVC il nome della vista e questi dati. Thymeleaf è il motore di template confermato e genera la pagina HTML per il browser. [Risoluzione della vista](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/viewresolver.html)
- **Validazione.** Jakarta Bean Validation verifica i vincoli dichiarati sul DTO di ingresso, per esempio la presenza del comune. Il modulo applicativo verifica le regole che richiedono i dati del sistema, come lo stato della misura e la titolarità di una richiesta. [Validazione in Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)
- **Confini dei moduli.** Il controller invoca il caso d’uso del proprio modulo. Ogni modulo applica le proprie regole e usa i propri repository. M1 usa l’adattatore email per i link degli account. M4 usa l’adattatore allegati per i file e conserva i metadati in PostgreSQL.
- **Scelte da formalizzare.** Restano aperte la tecnologia dell’archivio allegati e la configurazione del servizio email.
