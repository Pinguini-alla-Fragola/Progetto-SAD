# Regole funzionali di riferimento

Versione 0.1 del **04/11/2026**. La revisione del team recepita in questa data fissa il riferimento di progetto; non attribuisce al committente l’approvazione di ogni scelta del gruppo. Le risposte GitHub #21/#29/#36/#40/#45 restano distinguibili dalle scelte e dai dubbi nel [registro](open-questions.md).

## Richiesta e accordo

- Campi minimi scelti: misura del catalogo, persona/nucleo e comune. ID, data di invio, OPT titolare e stato sono gestiti dal sistema. Nessun identificativo assistiti, testo libero o allegato degli assistiti nel modulo minimo.
- Coda in ordine di arrivo. Per ogni richiesta: misura compatibile, stesso comune, poi limitrofi; a pari prossimità operatori del programma prima delle OPT; fra equivalenti minor carico. Senza soluzione locale si valuta il remoto PICUS compatibile e infine il rifiuto. Periodo/stati del carico e spareggi da definire col PO.
- Il Banco vede gli erogatori compatibili ordinati, decide chi interpellare e ne contatta uno per volta. Un assenso non conferma l’accordo e un singolo rifiuto non rifiuta la pratica. Ritiro del contatto senza risposta in Q-19; nessun timeout concordato.
- Banco ed erogatore concordano i dettagli; solo il Banco li registra e conferma nell’app. Presenza: data, ora, luogo, nome/cognome e telefono del referente. Distanza: data, ora e link. Non serve una seconda approvazione finale dell’erogatore nell’app.
- Conferma, stato e visibilità costituiscono un solo esito: l’accordo completo diventa Confermata e la OPT consulta i dettagli; li comunica all’assistito fuori dall’app. Nessuna verifica della prestazione svolta.
- Dettagli non modificabili dopo Confermata. Annullamento eccezionale con motivo e senza riapertura automatica. Esatta visibilità del motivo e avvisi da dettagliare in Q-04.

| Stato iniziale | Azione | Stato risultante |
| --- | --- | --- |
| Nessuna richiesta | Invio valido della OPT | In programmazione |
| In programmazione | Banco conferma accordo completo | Confermata |
| In programmazione | Banco conclude che manca una soluzione | Rifiutata, con Nessun erogatore disponibile |
| Confermata | Account della matrice annulla per evento eccezionale con motivo | Annullata |

Non sono definite altre transizioni. In programmazione/Rifiutata non passano ad Annullata; ripetere l’annullamento non riapre la pratica. Il rifiuto non richiede un motivo libero obbligatorio (#36).

## Permessi per appartenenza e assegnazione

| Operazione o dato | Account consentiti | Limiti |
| --- | --- | --- |
| Dati e richieste di una OPT | Account titolare e Banco autorizzato | Un erogatore interpellato vede soltanto i dati necessari alla propria assegnazione |
| Risposta alla disponibilità | OPT o Operatore destinatario | Non abilita alle altre pratiche della richiedente |
| Conferma accordo e rifiuto finale | Banco di coordinamento | L’account Operatore interno non equivale al ruolo Banco |
| Annullamento di un accordo assegnato a una OPT | Account della stessa OPT | Solo da Confermata, evento eccezionale e motivo |
| Annullamento con esterno OPT assegnato | Account della OPT di appartenenza | Non il singolo Operatore |
| Annullamento con interno/esterno pagato dal Banco | Banco di coordinamento | Non il singolo Operatore |
| Scheda personale | Titolare pagato dal Banco e Banco autorizzato | Esterni OPT senza accesso alla rendicontazione (#45) |
| Conteggi OPT | OPT titolare e Banco autorizzato | Viste aggregate del monitoraggio al Banco; estensione ad altre OPT non definita |
| Comunicazione e riscontro | Destinatario e Banco autorizzato | Ogni destinatario conferma soltanto per sé |

La sola qualità di OPT richiedente non concede l’annullamento; serve una delle relazioni della matrice. Il server verifica identità, ruolo e relazione con la risorsa. Il flag Legato all’OPT non assegna il ruolo OPT ed è gestito dal Banco, non dal client.

Il vincolo #28 è trasversale: ciascun incremento verifica la porzione di matrice pertinente con casi consentiti e negati. Completare il login non dimostra permessi su moduli non ancora realizzati. QS-01 descrive la verifica del prodotto, non un risultato già osservato.

## Offerta e disponibilità

Il progetto sceglie di dichiarare le misure offerte senza mostrare disponibilità preventive con giorni o fasce; la disponibilità concreta segue il contatto del Banco. La seconda tornata richiedeva il dato «quando»: Q-03 mantiene il confronto col PO/committente sulla semplificazione o su un dato temporale minimo. La scelta del gruppo non cancella la richiesta del cliente né impone un calendario a slot.

Il ritiro dell’offerta o la disattivazione di una misura non cancellano riferimenti già usati. Continuazione delle pratiche in corso e registrazione di attività pregresse da precisare in Q-20.

## Comunicazioni e monitoraggio

Destinatari: tutti o gruppi OPT e tutti o gruppi Operatori (#21), con notifica nell’app. Pulsante scelto «Confermo la ricezione»: una conferma per account/comunicazione con data e ora; ripetere conserva la prima. La sola apertura non conferma e il riscontro non certifica lettura o comprensione.

Conteggi OPT: anno/trimestre, misura e voce. Vista trimestrale e cumulativa sul periodo derivano dagli stessi dati; nessun secondo inserimento. Cumulativo di conteggi dichiarati, non persone uniche; categorie sovrapponibili restano distinte e dato mancante non equivale a zero. [Tracciati](tracciati-monitoraggio.md) e #41 per controlli, correzioni e significato delle voci.

Schede personali: solo interni/esterni pagati dal Banco, indipendenti dalle richieste. Gli esterni OPT non le compilano; attribuzione delle loro attività nei conteggi ancora da chiarire. #57 riepiloga il singolo trimestre delle schede, non il cumulativo OPT. Inserimento ministeriale manuale.
