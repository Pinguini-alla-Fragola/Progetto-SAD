# QS-05 Ripristino del servizio dopo un fermo

**Area e priorità:** Disponibilità / P2

## Sorgente

Un guasto del processo applicativo o dell’istanza che lo esegue.

## Stimolo

PICUS diventa temporaneamente indisponibile dopo che alcuni dati sono stati registrati dal sistema.

## Ambiente

Fermo del servizio con database integro e dati persistiti disponibili. Perdita del database e disastro dell’intera infrastruttura richiedono uno scenario e una politica di backup separati.

## Artefatto

Applicazione, connessione alla persistenza e procedura di ripristino.

## Risposta

Il servizio viene ripristinato mediante una procedura documentata; gli utenti ritrovano i dati già registrati e gli invii non riusciti non appaiono falsamente completati.

## Misura della risposta

Obiettivo proposto: ripristino entro 4 ore dal fermo, con 100% dei record già registrati ancora disponibili nel caso di database integro. Il tempo è misurato fino al completamento riuscito di accesso, lettura e nuovo invio di prova.

## Stato delle soglie

F02 tollera alcune ore e, eccezionalmente, un paio di giorni; non stabilisce un SLA. Le 4 ore sono una proposta da concordare in Q-07. Nessuna garanzia sulla perdita del database viene dedotta.

## Metodo di verifica previsto

In un ambiente di prova interrompere il solo servizio, eseguire il runbook e confrontare i record prima e dopo. Registrare tempi e limiti a fine costruzione, con la procedura di ripristino completa.
