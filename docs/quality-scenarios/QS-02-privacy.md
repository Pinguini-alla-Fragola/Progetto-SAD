# QS-02 Minimizzazione e riservatezza dei dati

Versione 0.1 del 25/10/2026. Stato: **proposta da validare**; nessuna prova software già attestata.

**Area e priorità proposta:** Privacy / P1

**Fonti e collegamenti:** F02 §§1, 5 e 7; F06 del 22/10; US-28, US-D05 e US-D13.

## Sorgente

Una OPT che compila una richiesta o un utente che consulta dati degli operatori.

## Stimolo

Invia campi identificativi degli assistiti non previsti dallo schema oppure tenta di consultare dati personali di un operatore senza permesso.

## Ambiente

MVP con campi strutturati e senza testo libero o allegati relativi agli assistiti; controlli estesi a schede ed export quando introdotti. Dataset di prova interamente sintetici.

## Artefatto

Schema dei moduli e delle richieste, validazione sul server, registri applicativi, viste e future esportazioni.

## Risposta

PICUS non offre campi anagrafici degli assistiti; rifiuta i campi non ammessi senza riprodurne il contenuto nei log o nei messaggi. Dati personali dell’operatore accessibili solo al Banco autorizzato e all’operatore titolare.

## Misura della risposta

0 campi identificativi degli assistiti nel modello e nei contratti. Tutti i payload di prova con campi non ammessi sono respinti senza persistenza o eco del valore. Nessun identificativo di assistito rilevato nei dati, log ed export campionati; 100% degli accessi non consentiti ai dati degli operatori negati.

## Stato delle soglie

Vincoli di minimizzazione dalle fonti. La verifica dello schema, i campi consentiti e il campione di 20 payload negativi sono proposte del team. Nessuna classificazione automatica universale del testo viene presunta.

## Metodo di verifica previsto

Ispezionare schema e contratti; inviare 20 payload negativi sintetici; verificare database, messaggi e log. Applicare la matrice QS-01 ai dati personali degli operatori. Conservazione e cancellazione restano in Q-05.
