# QS-02 Minimizzazione e riservatezza dei dati

**Area e priorità:** Privacy / P1

## Sorgente

Una OPT che compila una richiesta o un utente che consulta dati degli operatori.

## Stimolo

Invia campi identificativi degli assistiti non previsti dallo schema oppure tenta di consultare dati personali di un operatore senza permesso.

## Ambiente

Form con campi strutturati e senza testo libero o allegati relativi agli assistiti; controlli estesi a schede e dati quantitativi delle OPT. Dataset di prova interamente sintetici.

## Artefatto

Schema dei moduli e delle richieste, validazione sul server, registri applicativi, viste ed eventuali esportazioni.

## Risposta

PICUS non offre campi anagrafici degli assistiti; rifiuta i campi non ammessi senza riprodurne il contenuto nei log o nei messaggi. Dati personali ed economici dell’operatore (anagrafica, tipologia contrattuale, costo orario lordo) accessibili solo al Banco autorizzato e all’operatore titolare. Nome e telefono del referente dell’erogatore, mostrati nell’appuntamento in presenza, sono ammessi e non riguardano gli assistiti.

## Misura della risposta

0 campi identificativi degli assistiti nel modello e nei contratti. Tutti i payload di prova con campi non ammessi sono respinti senza persistenza o eco del valore. Nessun identificativo di assistito rilevato nei dati, log ed export campionati; 100% degli accessi non consentiti ai dati personali ed economici degli operatori negati.

## Stato delle soglie

Vincoli di minimizzazione dalle fonti. La verifica dello schema, i campi consentiti e il campione di 20 payload negativi sono proposte del team. Nessuna classificazione automatica universale del testo viene presunta.

## Metodo di verifica previsto

Ispezionare schema e contratti; inviare 20 payload negativi sintetici; verificare database, messaggi e log. Applicare la matrice QS-01 ai dati personali degli operatori. Conservazione e cancellazione restano in Q-05.
