# Regole funzionali di riferimento

Versione 0.2 del **07/11/2026**, allineata al contesto del 5 novembre. Il [registro](open-questions.md) distingue le decisioni dai quesiti residui.

## Richiesta e accordo

- Il modulo richiede misura del catalogo, persona/nucleo e comune. Accetta anche `description`, testo semplice facoltativo per descrivere il bisogno senza identificativi degli assistiti. Il dettaglio con la descrizione è riservato alla OPT titolare e al Banco autorizzato. Il sistema gestisce ID, data di invio, OPT titolare e stato. Il modulo esclude gli allegati.
- Coda in ordine di arrivo. L’elenco include le OPT compatibili, anche se erogano tramite professionisti, e gli operatori interni o esterni pagati dal Banco. Prima stesso comune, poi limitrofi. A parità di prossimità precedono gli operatori del Banco, poi si considera il minor carico. In assenza di una soluzione locale si valuta il remoto PICUS compatibile e infine il rifiuto. Periodo/stati del carico e spareggi restano da definire.
- Il Banco sceglie chi interpellare e contatta un erogatore per volta. La notifica e la risposta competono all’account organizzativo della OPT o all’account personale dell’operatore del Banco interpellato. Un assenso lascia la pratica In programmazione e un singolo rifiuto consente di cercare un’alternativa. Ritiro, avviso e risposte tardive in #66/Q-19.
- Banco ed erogatore concordano i dettagli tramite il modulo di contatto. Solo il Banco di coordinamento registra e conferma, assegnando la pratica alla OPT o all’operatore del Banco. In presenza registra data, ora, luogo, nome/cognome e telefono del referente. A distanza registra data, ora e link. Scambio di proposte, dati e vincoli in #38/Q-09.
- Conferma, stato e visibilità costituiscono un solo esito: l’accordo completo diventa Confermata e la OPT consulta i dettagli; li comunica all’assistito fuori dall’app. Nessuna verifica della prestazione svolta.
- Dettagli non modificabili dopo Confermata. Annullamento eccezionale con motivo e senza riapertura automatica. Esatta visibilità del motivo e avvisi da dettagliare in Q-04.

| Stato iniziale | Azione | Stato risultante |
| --- | --- | --- |
| Nessuna richiesta | Invio valido della OPT | In programmazione |
| In programmazione | Banco conferma accordo completo | Confermata |
| In programmazione | Banco conclude che manca una soluzione | Rifiutata, con Nessun erogatore disponibile |
| Confermata | Account della matrice annulla per evento eccezionale con motivo | Annullata |

Non sono definite altre transizioni. In programmazione/Rifiutata non passano ad Annullata; ripetere l’annullamento non riapre la pratica. Il rifiuto non richiede un motivo libero obbligatorio (#36).

## Permessi per ruolo e relazione con la risorsa

| Operazione o dato | Account consentiti | Limiti |
| --- | --- | --- |
| Dati e richieste di una OPT | Account titolare e Banco autorizzato | L’erogatore interpellato vede i dati minimi della singola richiesta. L’assegnatario vede i dettagli della propria assegnazione. |
| Risposta alla disponibilità | Account OPT o account personale del Banco interpellato | Soltanto per il contatto ricevuto |
| Conferma accordo e rifiuto finale | Banco di coordinamento | L’account Operatore interno non equivale al ruolo Banco |
| Annullamento | Account della matrice di annullamento | Solo da Confermata, evento eccezionale e motivo obbligatorio |
| Scheda personale | Interno o esterno pagato dal Banco titolare della scheda e Banco autorizzato | Invio del solo titolare. Accesso negato all’account OPT e agli altri operatori. |
| Conteggi OPT | OPT titolare e Banco autorizzato | Viste aggregate del monitoraggio al Banco; estensione ad altre OPT non definita |
| Comunicazione e riscontro | Destinatario e Banco autorizzato | Ogni destinatario conferma soltanto per sé |

Il server ricava identità, ruolo, organizzazione, titolarità e assegnazione da sessione e dati persistiti. I parametri del client identificano la risorsa richiesta e non determinano i permessi.

Il vincolo #28 si verifica per incremento con casi consentiti e negati. Il [catalogo degli scenari](../quality-scenarios/README.md) descrive le verifiche pianificate, con QS-01A per consultazione, QS-01B per annullamento e QS-01C per revoca.

### Matrice di annullamento

| Erogatore assegnato | Account autorizzato ad annullare da Confermata |
| --- | --- |
| OPT erogatrice, anche se la misura è svolta tramite un professionista esterno | Account della stessa OPT |
| Operatore interno del Banco / Centro PICUS | Account personale dell’operatore assegnato oppure Banco di coordinamento |
| Operatore esterno pagato dal Banco | Account personale dell’operatore assegnato oppure Banco di coordinamento |

L’annullamento è eccezionale e richiede un motivo. L’esito è Annullata. I dettagli si conservano e la ricerca rimane chiusa. Il server nega l’operazione a OPT soltanto richiedenti o interpellate, colleghi e Banco di coordinamento su assegnazioni OPT. Verificare anche sessione assente o scaduta, motivo mancante e stato iniziale diverso da Confermata. In caso di diniego stato e dati restano invariati. Esempi di evento, visibilità del motivo e avvisi restano in [Q-04](open-questions.md#q-04-confine-e-annullamento-eccezionale).

## Offerta e disponibilità

Nel percorso base l’offerta dichiara le misure e la disponibilità concreta si verifica sulla singola richiesta. Il calendario facoltativo per misura è un’estensione (#73). Il dato temporale minimo richiesto nella seconda tornata resta da decidere in Q-03.

Il ritiro dell’offerta o la disattivazione di una misura non cancellano riferimenti già usati. Continuazione delle pratiche in corso e registrazione di attività pregresse da precisare in Q-20.

## Comunicazioni e monitoraggio

Le comunicazioni possono raggiungere tutte le OPT o un loro gruppo tramite account organizzativi, oppure tutti gli operatori del Banco (interni ed esterni pagati dal Banco) o un loro gruppo tramite account personali. «Confermo la ricezione» registra la prima data e ora per account e comunicazione. Per la OPT il riscontro è dell’organizzazione. La sola apertura lascia il riscontro in sospeso.

Conteggi OPT: anno/trimestre, misura e voce. Vista trimestrale e cumulativa sul periodo derivano dagli stessi dati; nessun secondo inserimento. Cumulativo di conteggi dichiarati, non persone uniche; categorie sovrapponibili restano distinte e dato mancante non equivale a zero. [Tracciati](tracciati-monitoraggio.md) e #41 per controlli, correzioni e significato delle voci.

Interni ed esterni pagati dal Banco inviano ciascuno la propria scheda personale, indipendente dalle richieste. #57 riepiloga il singolo trimestre delle schede, #69 consulta il monitoraggio organizzativo OPT. Inclusione e attribuzione delle attività svolte tramite professionisti nei conteggi OPT restano in #41/Q-13. Inserimento ministeriale manuale.
