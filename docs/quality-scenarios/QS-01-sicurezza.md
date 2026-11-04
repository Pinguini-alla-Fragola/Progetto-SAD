# QS-01 Isolamento degli accessi

**Area e priorità:** Sicurezza / P1

## Sorgente

Un utente OPT o Operatore, autenticato oppure privo di una sessione valida.

## Stimolo

Tenta di leggere o modificare una richiesta, un appuntamento o una scheda di un altro titolare, anche alterando l’identificativo inviato al server. Un erogatore interpellato per una richiesta tenta di consultare altre pratiche della OPT richiedente; un account tenta di annullare una richiesta Confermata che non spetta al suo erogatore.

## Ambiente

Funzionamento ordinario con due OPT A/B, un operatore esterno collegato alla OPT A, un operatore interno e uno esterno pagati dal Banco e un utente Banco con ruolo di coordinamento; dati sintetici assegnati a titolari distinti.

## Artefatto

Controlli di autenticazione, autorizzazione e appartenenza sulle risorse protette.

## Risposta

Il server nega gli accessi non consentiti prima di restituire dati o applicare modifiche. La sessione determina ruolo e titolare. Il Banco mantiene la visibilità necessaria al coordinamento. Essere interpellato come erogatore non dà accesso alle altre pratiche della OPT richiedente. L’annullamento è consentito solo all’account legato all’erogatore assegnato (OPT, OPT di appartenenza dell’operatore esterno, Banco).

## Misura della risposta

100% dei casi negativi della matrice di prova negati; 0 record estranei restituiti e 0 modifiche non autorizzate. 401 senza autenticazione valida; 403 con identità valida e permesso mancante. Tutti i casi positivi previsti dalla stessa matrice sono consentiti.

## Stato delle soglie

Vincolo di isolamento dalle fonti; codici e matrice sono la specifica proposta dal team. È un piano di verifica, non un esito già osservato.

## Metodo di verifica previsto

Provare UI e richieste dirette al server per ogni combinazione ruolo/titolare/azione. Confrontare i dati prima e dopo i tentativi negati e registrare versione e casi eseguiti.
