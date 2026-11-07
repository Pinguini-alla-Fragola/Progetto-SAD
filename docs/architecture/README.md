# Architettura

- [CTX-01 · System Context](../requirements/diagrams/contesto/Context%20Diagram.drawio)
- [Diagramma dei container C4 di PICUS a regime](diagrams/PICUS-C4-Container.mmd)
- [ARCH-01 · Architettura MVC di PICUS a regime](diagrams/PICUS-Architettura-MVC.mmd)
- [Diagramma dei moduli e dei package Java](diagrams/PICUS-Diagramma-Moduli.mmd)

Nel diagramma dei moduli, ogni freccia parte dal modulo chiamante e punta al modulo di cui usa il package `api`. M1 fornisce identità, ruoli e profili. Ogni modulo applica i controlli di titolarità, assegnazione o ricezione sulle risorse che gestisce. Il tratteggio indica la collaborazione per le notifiche operative, da dettagliare.

La struttura proposta parte dalla radice Java `it.unina.picus` e assegna un package a ciascun modulo. `web` riceve le richieste HTTP e usa le interfacce Java di `api`, che espone i contratti e i DTO accessibili agli altri moduli. In `interno`, `applicazione` realizza quei contratti e coordina i casi d’uso. `dominio` contiene entità e regole, `persistenza` contiene repository e mappature JPA e `integrazioni` contiene gli adattatori dei moduli che li richiedono.

I dati indicati in ogni riquadro sono di competenza del modulo. PostgreSQL è condiviso e ciascun modulo accede ai propri dati tramite i propri repository. Per collaborare, i moduli si scambiano i DTO dichiarati nel package `api` del modulo chiamato.

## Come leggere ARCH-01

- **Ingresso.** Il browser invia una richiesta HTTPS con metodo HTTP e percorso. Spring Security riconosce la sessione e verifica l’accesso alla funzione. Se la richiesta prosegue, il `DispatcherServlet` usa metodo e percorso per individuare il metodo del controller a cui inoltrarla. [Flusso di Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/sequence.html)
- **Dal Controller al Modello e alla Vista.** Il controller espone la funzione al browser tramite un endpoint HTTP. Chiama un servizio Java del modulo, riceve il risultato e lo usa per preparare i dati da mostrare. Consegna a Spring MVC il nome della vista e questi dati. La vista risolta genera la pagina HTML per il browser. [Risoluzione della vista](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/viewresolver.html)
- **Validazione.** Jakarta Bean Validation verifica i vincoli dichiarati sul DTO di ingresso, per esempio la presenza del comune. Il modulo applicativo verifica le regole che richiedono i dati del sistema, come lo stato della misura e la titolarità di una richiesta. [Validazione in Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)
- **Confini dei moduli.** Il controller invoca il servizio tramite l’interfaccia Java del modulo. Ogni modulo applica le proprie regole e usa i propri repository. M1 usa l’adattatore email per i link degli account. M4 usa l’adattatore allegati per i file e conserva i metadati in PostgreSQL.
- **Scelte da formalizzare.** La vista usa PostgreSQL, da registrare in un ADR e allineare con il task di predisposizione dell’ambiente. Restano aperte la conferma di Thymeleaf, la tecnologia dell’archivio allegati e la configurazione del servizio email. [Task di predisposizione dell’ambiente](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/77)
