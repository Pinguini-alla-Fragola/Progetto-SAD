# QS-06 Modifica del catalogo senza rilascio software

**Area e priorità:** Modificabilità / P2

## Sorgente

Un utente Banco autorizzato alla gestione del catalogo.

## Stimolo

Deve aggiungere una nuova misura o correggere la descrizione di una misura esistente.

## Ambiente

Applicazione completa in esecuzione, con il catalogo iniziale delle 12 misure.

## Artefatto

Catalogo configurabile, interfaccia Banco e viste di selezione della misura nelle richieste e nella dichiarazione dell’offerta.

## Risposta

Il Banco aggiorna il catalogo tramite l’applicazione; le OPT vedono le nuove informazioni senza intervento dello sviluppatore. Le richieste precedenti e le offerte già dichiarate conservano il significato della misura scelta.

## Misura della risposta

Soglie proposte: operazione completata entro 5 minuti da un utente Banco e visibilità alla OPT al successivo aggiornamento della vista, entro 10 secondi. Nessuna modifica al codice, ricompilazione, riavvio o aggiornamento manuale del database.

## Stato delle soglie

Configurabilità senza sviluppatore dalle fonti; tempi proposti dal team. Comportamento sulle misure già usate, sulle offerte già dichiarate dalle OPT e sulla disattivazione da dettagliare in US-D03.

## Metodo di verifica previsto

Partire da un catalogo di prova, aggiungere una misura e modificarne una esistente; verificare con una sessione OPT e con una richiesta storica. Eseguire la prova sull’applicazione completa a fine costruzione.
