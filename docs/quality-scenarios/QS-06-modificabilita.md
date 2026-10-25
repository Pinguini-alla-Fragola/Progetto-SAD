# QS-06 Modifica del catalogo senza rilascio software

Versione 0.1 del 25/10/2026. Stato: **proposta da validare**; nessuna prova software già attestata.

**Area e priorità proposta:** Modificabilità / P2

**Fonti e collegamenti:** F02 §5.2 e §7; US-D03; catalogo iniziale delle 12 misure.

## Sorgente

Un utente Banco autorizzato alla gestione del catalogo.

## Stimolo

Deve aggiungere una nuova misura o correggere la descrizione di una misura esistente.

## Ambiente

Servizio in esecuzione e modulo di gestione catalogo disponibile. L’MVP iniziale usa dati precaricati e non dichiara implementato il relativo modulo amministrativo.

## Artefatto

Catalogo configurabile, interfaccia Banco e viste di selezione della misura.

## Risposta

Il Banco aggiorna il catalogo tramite l’applicazione; le OPT vedono le nuove informazioni senza intervento dello sviluppatore. Le richieste precedenti conservano il significato della misura scelta.

## Misura della risposta

Soglie proposte: operazione completata entro 5 minuti da un utente Banco e visibilità alla OPT al successivo aggiornamento della vista, entro 10 secondi. Nessuna modifica al codice, ricompilazione, riavvio o aggiornamento manuale del database.

## Stato delle soglie

Configurabilità senza sviluppatore dalle fonti; tempi proposti dal team. Comportamento sulle misure già usate e sulla disattivazione da dettagliare in US-D03.

## Metodo di verifica previsto

Partire da un catalogo di prova, aggiungere una misura e modificarne una esistente; verificare con una sessione OPT e con una richiesta storica. Eseguire la prova quando US-D03 è disponibile.
