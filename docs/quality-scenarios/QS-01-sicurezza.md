# QS-01 Isolamento degli accessi

Versione 0.1 del 25/10/2026. Stato: **proposta da validare**; nessuna prova software già attestata.

**Area e priorità proposta:** Sicurezza / P1

**Fonti e collegamenti:** F02 §§3 e 7; US-28; tutte le storie del percorso MVP.

## Sorgente

Un utente OPT o Operatore, autenticato oppure privo di una sessione valida.

## Stimolo

Tenta di leggere o modificare una richiesta, un appuntamento o una scheda di un altro titolare, anche alterando l’identificativo inviato al server.

## Ambiente

Funzionamento ordinario con due OPT A/B, due Operatori X/Y e un utente Banco; dati sintetici assegnati a titolari distinti. Le prove si aggiungono quando ciascuna risorsa è disponibile.

## Artefatto

Controlli di autenticazione, autorizzazione e appartenenza sulle risorse protette.

## Risposta

Il server nega gli accessi non consentiti prima di restituire dati o applicare modifiche. La sessione determina ruolo e titolare. Il Banco mantiene la visibilità necessaria al coordinamento.

## Misura della risposta

100% dei casi negativi della matrice di prova negati; 0 record estranei restituiti e 0 modifiche non autorizzate. 401 senza autenticazione valida; 403 con identità valida e permesso mancante. Tutti i casi positivi previsti dalla stessa matrice sono consentiti.

## Stato delle soglie

Vincolo di isolamento dalle fonti; codici e matrice sono la specifica proposta dal team. È un piano di verifica, non un esito già osservato.

## Metodo di verifica previsto

Provare UI e richieste dirette al server per ogni combinazione ruolo/titolare/azione. Confrontare i dati prima e dopo i tentativi negati e registrare versione e casi eseguiti.
