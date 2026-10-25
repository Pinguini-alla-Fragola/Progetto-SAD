# QS-05 Ripristino del servizio dopo un fermo

Versione 0.1 del 25/10/2026. Stato: **proposta da validare**; nessuna prova software già attestata.

**Area e priorità proposta:** Disponibilità / P2

**Fonti e collegamenti:** F02 §7; richieste del percorso MVP.

## Sorgente

Un guasto del processo applicativo o dell’istanza che lo esegue.

## Stimolo

PICUS diventa temporaneamente indisponibile dopo che alcune richieste sono state salvate e confermate.

## Ambiente

Fermo del servizio con database integro e dati persistiti disponibili. Perdita del database e disastro dell’intera infrastruttura richiedono uno scenario e una politica di backup separati.

## Artefatto

Applicazione, connessione alla persistenza e procedura di ripristino.

## Risposta

Il servizio viene ripristinato mediante una procedura documentata; gli utenti ritrovano le richieste già confermate e gli invii non riusciti non appaiono falsamente completati.

## Misura della risposta

Obiettivo proposto: ripristino entro 4 ore dal fermo, con 100% dei record già confermati ancora disponibili nel caso di database integro. Il tempo è misurato fino al completamento riuscito di accesso, lettura e nuovo invio di prova.

## Stato delle soglie

F02 tollera alcune ore e, eccezionalmente, un paio di giorni; non stabilisce un SLA. Le 4 ore sono una proposta da concordare in Q-07. Nessuna garanzia sulla perdita del database viene dedotta.

## Metodo di verifica previsto

In un ambiente di prova interrompere il solo servizio, eseguire il runbook e confrontare i record prima e dopo. Registrare tempi e limiti quando la procedura di esecuzione sarà disponibile.
