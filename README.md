# Progetto SAD

Applicazione web per la gestione di un **ente di beneficenza**, sviluppata
per l’esame di **Software Architecture Design** — a.a. 2026/27.

**Docente:** Prof. Anna Rita Fasolino  
**Corso:** Laurea Magistrale in Ingegneria Informatica  
**Università:** Università degli Studi di Napoli Federico II

Il progetto è in fase di avvio. I moduli funzionali e il relativo perimetro
saranno definiti attraverso le User Story del backlog.

## 👥 Team

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

## 🏗️ Architettura

L’architettura prevista è un **monolite modulare** basato sul pattern
**MVC (Model–View–Controller)**.

I moduli mantengono confini espliciti e comunicano attraverso interfacce
dedicate e DTO. Questa organizzazione prepara l’estrazione di un
sottosistema in un **microservizio**, prevista nelle ultime iterazioni
Scrum, con comunicazione tramite **gRPC e Protocol Buffers**.

La progettazione applica i principi **GRASP** e i design pattern **GoF**
dove appropriato.

## 🛠️ Stack tecnologico previsto

| Area | Tecnologie |
|------|------------|
| Linguaggio e build | Java 25, Maven e Maven Wrapper |
| Backend | Spring Boot, Spring Web MVC |
| Persistenza | MySQL, Spring Data JPA / Hibernate |
| Validazione | Jakarta Bean Validation |
| Interfaccia web | HTML5, CSS3, JavaScript vanilla, template lato server |
| Testing | JUnit 5, senza Mockito |
| Comunicazione con il microservizio | gRPC, Protocol Buffers — fase successiva |
| Containerizzazione | Docker, Docker Compose |
| Predisposizione al deployment | Architettura pronta per Kubernetes |
| Modellazione UML | Visual Paradigm |
| Documentazione | Markdown, Microsoft Word / PDF |
| Versioning e project management | GitHub, GitHub Projects |

Il motore di template lato server deve essere confermato:
Thymeleaf è una delle opzioni indicate nelle linee guida.

## 🧭 Principi di sviluppo

- Separazione delle responsabilità tra Controller, Model e View.
- Logica di business nel dominio e nel livello applicativo.
- Uso di DTO per gli input e gli output dei Controller, senza esporre
  direttamente le entità JPA.
- Comunicazione tra moduli attraverso interfacce dedicate, senza accessi
  diretti ai repository o relazioni JPA tra moduli differenti.
- Dependency injection tramite costruttore.
- Configurazione esterna tramite proprietà e variabili d’ambiente.
- Test con JUnit 5 e Fake/Stub scritti a mano quando necessari.

## 🏭 Project management

Il team adotta **Scrum**. User Story, attività e stato di avanzamento
sono tracciati su GitHub Projects:

- [Backlog Progetto-SAD](https://github.com/users/CapHello/projects/2)

<!-- BURNDOWN:START — grafico dello sprint in corso, aggiornato ogni sera dal workflow "burndown"; non modificare il link -->
## 📉 Avanzamento dello sprint

![Burndown dello sprint in corso](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/raw/burndown-data/burndown-latest.svg)

*Aggiornato ogni sera. Gli sprint precedenti sono nel branch [`burndown-data`](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/tree/burndown-data).*
<!-- BURNDOWN:END -->

## 🚀 Avvio locale

Le istruzioni di avvio saranno completate quando sarà disponibile
lo scheletro eseguibile dell’applicazione. Includeranno i prerequisiti,
la configurazione di MySQL, le variabili d’ambiente e i comandi di avvio.
