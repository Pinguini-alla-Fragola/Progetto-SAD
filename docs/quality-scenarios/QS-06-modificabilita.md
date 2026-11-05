# QS-06 Aggiornamento del catalogo senza rilascio software

**Attributo di qualità** · Modificabilità del catalogo

**Priorità proposta** · P2

**Motivazione.** Il Banco deve poter aggiornare il catalogo direttamente in PICUS. Le OPT usano lo stesso catalogo per chiedere una misura e per dichiarare quali misure offrono.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
|---|---|---|---|---|---|
| Un utente del Banco autorizzato a gestire il catalogo. | Il Banco aggiunge una nuova misura dalla schermata di gestione del catalogo e salva. | PICUS è in normale operatività. Il catalogo contiene le 12 misure iniziali, alcune già associate a richieste e offerte delle OPT. | Il catalogo, la schermata di gestione del Banco e le schermate in cui le OPT selezionano una misura. | PICUS salva la nuova misura e la rende selezionabile sia per inviare una richiesta sia per dichiarare un’offerta. | Dopo la conferma del salvataggio, la nuova misura è selezionabile **alla prima riapertura di entrambe le schermate OPT**. L’aggiornamento richiede **zero modifiche al codice, ricompilazioni, rilasci, riavvii o interventi manuali sul database**. |

## Approccio di verifica

Usare un catalogo con dati sintetici e due sessioni distinte, una del Banco e una OPT. Dopo ogni salvataggio, controllare l’esito dalla sessione OPT già aperta.

| Caso | Esito atteso |
| --- | --- |
| **Aggiunta di una misura.** Il Banco inserisce una nuova misura e salva. | Alla prima riapertura delle schermate, la OPT può selezionare la misura e usarla per inviare una richiesta e per dichiarare un’offerta. |
| **Correzione di un refuso.** Il Banco corregge la descrizione di una misura già associata a una richiesta e a un’offerta. | Il catalogo mostra la descrizione corretta. La misura conserva il proprio identificativo e sia la richiesta precedente sia l’offerta già registrata continuano a riferirsi a quell’identificativo. |
