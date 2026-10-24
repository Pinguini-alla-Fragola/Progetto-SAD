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
- [Documento di progetto Word](docs/Documentazione_SAD_2026.docx)
- [Esportazione PDF del documento](docs/Documentazione_SAD_2026.pdf)
- [Regole di contribuzione](.github/CONTRIBUTING.md)

Il Word conserva l'analisi già redatta; le cartelle raccolgono i relativi modelli
e riepiloghi. Il PDF presente è un'esportazione precedente e sarà riallineato
al Word durante la prossima revisione documentale. Il Product Backlog resta
la fonte operativa per il lavoro pianificato.

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

## Avvio locale

```bash
mvn spring-boot:run
```

Lo scheletro contiene la classe di avvio Spring Boot e il test di caricamento
del contesto del template. La configurazione iniziale contiene soltanto
`spring.application.name: picus`; le funzionalità di dominio sono da realizzare.

## 🏭 Project management

Il team adotta **Scrum**. User Story, attività e stato di avanzamento
sono tracciati su GitHub Projects:

- [Backlog Progetto-SAD](https://github.com/users/CapHello/projects/2)

<!-- BURNDOWN:START — grafico dello sprint in corso, aggiornato ogni sera dal workflow "burndown"; non modificare il link -->
## 📉 Avanzamento dello sprint

![Burndown dello sprint in corso](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/raw/burndown-data/burndown-latest.svg)

*Aggiornato ogni sera. Gli sprint precedenti sono nel branch [`burndown-data`](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/tree/burndown-data).*
<!-- BURNDOWN:END -->
