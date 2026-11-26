# PICUS

Progetto di Software Architecture Design del gruppo **Pinguini alla Fragola**,
a.a. 2026/27, Università degli Studi di Napoli Federico II.

**Docente:** Prof. Anna Rita Fasolino.

PICUS è un’applicazione web per il Banco, le Organizzazioni Partner Territoriali (OPT) e gli operatori.
Le OPT inviano le richieste di misure di accompagnamento e ne seguono lo stato.
Il Banco prende in carico le richieste e contatta le OPT o gli operatori che possono erogare la misura.
Chi viene contattato indica la propria disponibilità e il Banco conferma gli appuntamenti.
L’applicazione è pensata anche per consentire al Banco di inviare comunicazioni alle OPT e agli operatori
e di raccogliere, per la rendicontazione, i conteggi trimestrali delle OPT e le schede degli operatori.

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

- [Indice della documentazione](docs/README.md)
- [Documento di progetto (PDF)](docs/Documentazione_SAD_2026.pdf)
- [Requisiti e modelli di analisi](docs/requirements/README.md)
- [Scenari di qualità](docs/quality-scenarios/README.md)
- [Architettura](docs/architecture/README.md)
- [Registro delle decisioni](docs/adr/README.md)
- [Review delle iterazioni](docs/reviews/README.md)
- [Regole di contribuzione](.github/CONTRIBUTING.md)

## 🏭 Project management

Il team usa Scrum e segue le storie, le attività e il loro avanzamento nel
[backlog su GitHub Projects](https://github.com/orgs/Pinguini-alla-Fragola/projects/2).

<!-- BURNDOWN:START — grafico dello sprint in corso, aggiornato ogni sera dal workflow "burndown"; non modificare il link -->
## 📉 Avanzamento dello sprint

![Burndown dello sprint in corso](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/raw/burndown-data/burndown-latest.svg)

*Il grafico si aggiorna ogni giorno. Gli sprint precedenti sono nel branch [`burndown-data`](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/tree/burndown-data).*
<!-- BURNDOWN:END -->

## Build e test

Per compilare PICUS ed eseguire i test, installa Java 25 e avvia Docker.
Poi apri il terminale nella cartella del progetto ed esegui il comando.

```bash
bash ./mvnw verify
```

Su Windows, in PowerShell, usa `.\mvnw.cmd verify`.

## Avvio locale

Copia [`.env.example`](.env.example) in `.env` e imposta le due password
`PICUS_DB_PASSWORD` e `PICUS_DEMO_PASSWORD`. Dalla cartella del progetto avvia
PICUS con Docker Compose.

```bash
docker compose up --build
```

Apri <http://localhost:8080> e accedi con `banco@picus.test` e la password
che hai impostato in `PICUS_DEMO_PASSWORD`.

Per provare il percorso dalla richiesta all'appuntamento, usa il comando della
[demo di coordinamento](docs/reviews/demo-coordinamento.md).
