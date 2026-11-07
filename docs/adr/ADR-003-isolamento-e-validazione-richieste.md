# ADR-003: Applicare l’isolamento delle richieste e validare i campi ammessi

- **Status:** Proposed
- **Date:** 2026-11-07
- **Decision owners:** @giallo04
- **Related issues:** [#76](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/76), [#78](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/78), [#25](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/25), [#28](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/28), [#48](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/48), [#49](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/49), [#51](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/51), [#52](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/52), [#54](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/54)

## Context

Il ruolo distingue Banco di coordinamento, OPT e Operatore del Banco. L’accesso a una pratica dipende anche dalla relazione con quella richiesta. La OPT titolare consulta le proprie richieste, l’erogatore interpellato vede i soli dati necessari al contatto e l’assegnatario vede i dettagli della propria assegnazione. Le OPT operano tramite l’account organizzativo anche quando erogano tramite un professionista esterno. [Regole funzionali](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/2bb87e6c7c5b454d40af4a2b1b9635f07e8dfb2c/docs/requirements/regole-funzionali.md#permessi-per-ruolo-e-relazione-con-la-risorsa)

Per inviare una richiesta bastano misura, persona o nucleo e comune. QS-01A richiede che la modifica dell’identificativo da parte di una OPT estranea restituisca `403` e zero dati protetti. QS-02A richiede di respingere un invio con un campo identificativo extra e di escluderne il valore da risposta, dati salvati, log ed eventuali esportazioni. [QS-01A](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/2bb87e6c7c5b454d40af4a2b1b9635f07e8dfb2c/docs/quality-scenarios/QS-01A-consultazione-richieste.md) · [QS-02A](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/2bb87e6c7c5b454d40af4a2b1b9635f07e8dfb2c/docs/quality-scenarios/QS-02A-identificativi-assistiti.md)

## Considered options

1. **Controllo per ruolo sulle rotte e form a campi fissi.** Riduce il lavoro iniziale. Due OPT con lo stesso ruolo potrebbero accedere alle risorse reciproche se manca il controllo sulla singola pratica.
2. **Controllo sulla risorsa e binding che ignora le chiavi sconosciute.** Protegge la pratica e riduce i campi persistibili. Un invio con un identificativo extra potrebbe comunque creare una richiesta valida, contro la risposta richiesta da QS-02A.
3. **Controllo server per ruolo e relazione con la risorsa, con lista esplicita dei campi ammessi.** Rende verificabili sia il diniego di accesso alla singola pratica sia il rifiuto dell’intero invio. Richiede controlli espliciti anche quando il client chiama direttamente gli endpoint.

## Decision

Proponiamo di autenticare gli account con Spring Security e sessione server. `account` fornisce identità e ruolo. Il modulo proprietario della risorsa applica nel caso d’uso la regola su titolarità, contatto o assegnazione prima di restituire dati o applicare modifiche. `request` limita le liste al perimetro dell’account e riserva la coda di coordinamento e la conferma o il rifiuto definitivo al ruolo Banco. L’identificativo inviato dal client individua la risorsa e i permessi derivano da sessione e dati persistiti. Per le risorse protette il server risponde `401` in assenza di una sessione valida e `403` se la sessione è valida ma l’accesso è negato. [Regole di accesso](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/2bb87e6c7c5b454d40af4a2b1b9635f07e8dfb2c/docs/requirements/regole-accessi.md#verifiche-per-incremento)

L’invio usa un DTO dedicato con misura, tipo di destinatario e comune. Il server verifica i campi applicativi ricevuti prima che il binding possa scartare quelli inattesi, separandoli dai parametri tecnici del framework. Rifiuta l’intero invio quando trova campi extra o valori non validi. Dopo la validazione ricava la OPT titolare dalla sessione e genera identificativo, data di invio e stato iniziale. Risposta di errore e log descrivono l’errore senza riportare i valori identificativi respinti.

Il server costruisce i DTO di risposta per i dati consentiti a quel ruolo e a quella relazione. Nei dettagli di un accordo in presenza il DTO per la OPT titolare include data, ora e luogo. Per il referente include solo nome, cognome e telefono. La scheda personale dell’operatore segue i permessi del modulo `reporting`. [QS-02B](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/blob/2bb87e6c7c5b454d40af4a2b1b9635f07e8dfb2c/docs/quality-scenarios/QS-02B-visibilita-dati-operatore.md)

## Consequences

- Il walking skeleton verifica l’accesso del titolare e del Banco, il logout e il diniego a una OPT estranea con zero dati restituiti e nessuna modifica. Con #49 si aggiunge il caso QS-01A completo, con una richiesta consultabile dall’OPT interpellata e una estranea.
- La #78 esercita il punto di ingresso del server con un invio di controllo valido e almeno 20 invii altrimenti validi con campi identificativi extra. Gli invii respinti devono produrre zero nuove richieste e zero occorrenze dei valori identificativi nelle risposte, nei dati salvati, nei log e nelle eventuali esportazioni.
- Ogni nuova operazione deve avere una regola di accesso e un DTO di ingresso o di uscita coerente con i dati che tratta. L’annullamento e l’invalidazione delle sessioni dopo revoca dell’account saranno verificati quando saranno disponibili i relativi flussi, con QS-01B e QS-01C.
