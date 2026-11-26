# Demo del coordinamento delle richieste

La demo mostra il percorso dall’invio della richiesta alla consultazione
dell’accordo con dati sintetici.

## Avviare l’ambiente di demo

Avvia Docker, configura `.env` come indicato in
[Avvio locale](../../README.md#avvio-locale) e verifica che la porta 8080
sia libera prima del primo avvio. Dalla radice del repository, esegui:

```console
python scripts/demo_coordinamento.py
```

Se Python 3 si avvia con `python3`, usa quel comando al posto di `python`.
Lo script avvia il progetto Compose `picus-demo-coordinamento` con un volume
proprio. Attende PICUS e carica la [fixture SQL](../../scripts/db/demo-coordinamento.sql).
La fixture abilita la OPT B per `M01` e l’operatore del Centro PICUS per
`M01` e `M04`, che diventa disponibile anche a distanza.

Quando compare **Demo pronta**, apri <http://localhost:8080>.
Puoi ripetere il comando sullo stesso volume. A fine demo, arresta
i servizi conservando i dati:

```console
docker compose -p picus-demo-coordinamento down
```

## Riprodurre il percorso

Apri tre profili del browser o sessioni separate. Usa la password impostata
in `PICUS_DEMO_PASSWORD` per accedere con gli account sintetici.

1. **OPT A** (`opt-a@picus.test`). Invia una richiesta per `M01` a Napoli
   usando dati sintetici. Annota l’ID e verifica lo stato **Creata**.
2. **Banco** (`banco@picus.test`). Prendi in carico la richiesta. Tra i
   candidati compaiono l’operatore di Napoli e la OPT B di Pozzuoli. Invia
   il contatto di disponibilità all’operatore.
3. **Operatore** (`operatore@picus.test`). Apri il contatto ricevuto e rispondi
   positivamente. La richiesta resta **Presa in carico** fino all’assegnazione.
4. **Banco**. Assegna la richiesta all’operatore. Quando passa **In programmazione**,
   registra un accordo in presenza con dati di esempio.
5. **OPT A**. Riapri lo stesso ID e verifica lo stato **Confermata** e i dettagli
   dell’accordo.

Per provare la variante a distanza, crea una richiesta per `M04` a Portici.
Il Banco vede due opzioni per l’operatore, una nel comune limitrofo e una a
distanza. Dopo il diniego dell’opzione locale, può contattare l’operatore per
quella a distanza e registrare l’accordo con data, ora e link.
