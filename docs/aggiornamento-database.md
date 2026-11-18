# Aggiornamento del database

Questa guida serve quando Flyway ferma l’aggiornamento di un database
PostgreSQL già in uso. Prima di applicare nuovi vincoli, PICUS controlla
che i dati esistenti siano compatibili. La procedura permette di individuare
le anomalie, correggerle e verificare l’aggiornamento su una copia del database.

## Procedura

Prima dell'upgrade fare un backup e provare il ripristino su una copia. Eseguire
il controllo in sola lettura [preflight-richieste.sql](../scripts/db/preflight-richieste.sql),
utilizzabile da V6 in poi, prima su quella copia e poi con le scritture applicative
sospese. Lo script contiene metacomandi del client `psql` e va eseguito così:

```powershell
Get-Content -Raw scripts/db/preflight-richieste.sql | docker compose exec -T picus-db psql -U picus -d picus -v ON_ERROR_STOP=1
```

Su V6–V9 viene eseguito il report dei contatti ancora aperti. La tabella degli
avvisi non esiste prima di V10: lo script verifica la sua presenza e segnala
esplicitamente che i due controlli sugli avvisi non sono applicabili, senza
interrompersi con `ON_ERROR_STOP=1`. Da V10 esegue anche quei due report.

Il risultato deve contenere zero righe in ogni report eseguito. Il callback
Flyway ripete il controllo prima di V11 e V13 e ferma l'upgrade con un messaggio
operativo se trova anomalie, prima di creare il relativo vincolo. Non modifica
lo storico né il checksum delle migrazioni precedenti.

Con due contatti `IN_ATTESA` sulla stessa pratica, non scegliere un vincitore
per data o UUID. Il Banco raccoglie i riscontri effettivi dai destinatari e
li fa registrare tramite il normale comando di risposta della versione
precedente (prima di sospendere le scritture). Una risposta negativa conserva
la pratica Presa in carico e il contatto nello storico. Ripetere il preflight
finché rimane al massimo un contatto aperto, poi sospendere le scritture e
aggiornare. Non trasformare una mancata risposta in indisponibilità.
Se i riscontri non sono ottenibili, rinviare l'upgrade e coinvolgere il PO
per [Q-19](requirements/open-questions.md#q-19-erogatore-senza-risposta).
Fino al chiarimento, evitare il ritiro automatico e la scadenza automatica del contatto.

Per avvisi orfani, duplicati o indirizzati ad altri account, sospendere
l'upgrade e far verificare al responsabile dei dati ogni riga riportata.
Conservare gli originali in un archivio di audit fuori dalla casella operativa
e concordare una correzione puntuale e tracciata prima di ripetere il controllo.
Il software non riassegna né elimina automaticamente avvisi già consegnati.
Ripristino e aggiornamento devono essere provati sulla copia riconciliata
prima del database condiviso; non usare `flyway repair` per aggirare il controllo.

Durante l'aggiornamento a V14, PostgreSQL verifica lo stato, l'assegnazione
e i dettagli degli accordi già presenti. Se trova una riga incoerente,
interrompe la migrazione e conserva i dati. Riconciliare le righe con i dati
concordati e verificare l'aggiornamento su una copia prima di riprenderlo.
