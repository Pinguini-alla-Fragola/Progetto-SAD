# Registro dei chiarimenti

Qui teniamo traccia delle domande ancora aperte e delle risposte che hanno
chiarito i requisiti. Lo stato delle issue è nella bacheca
[Domande al committente](https://github.com/orgs/Pinguini-alla-Fragola/projects/2/views/3).

## Q-01 Continuità del referente OPT

Ogni OPT ha un solo account. Resta da stabilire chi aggiorna il referente e
l’indirizzo email e come avviene il passaggio di consegne. [#65](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/65)

## Q-02 Modulo minimo della richiesta

Per il team sono obbligatori misura, tipo di destinatario (persona o nucleo)
e comune. La descrizione è facoltativa e va scritta senza identificativi
degli assistiti. Restano da precisare le validazioni di dettaglio.
[#48](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/48)

## Q-03 Disponibilità preventiva

La disponibilità si verifica sulla singola richiesta. La #73 ha chiarito che
il calendario per misura è facoltativo. Resta da capire quale dato temporale
minimo occorra raccogliere. [#73](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/73)

## Q-04 Confine e annullamento

La [matrice di annullamento](regole-funzionali.md#matrice-di-annullamento)
indica chi può annullare. Il motivo è facoltativo solo quando la richiesta è
Creata. Restano da definire il formato del motivo e chi può leggerlo.
[#37](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/37) · [#54](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/54)

## Q-05 Storico dopo revoca

La revoca blocca l’accesso e lascia intatti i dati. Restano da stabilire i
tempi di conservazione, chi può consultare lo storico e quando cancellarlo.
[#42](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/42)

## Q-06 Scheda personale e controlli

La scheda trimestrale personale riguarda gli operatori del Banco, inclusi
gli esterni pagati dal Banco. Da chiarire firma, campi obbligatori,
controlli, correzioni, invii ripetuti e voci da aggregare.
[#41](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/41)

## Q-07 Qualità e dimensionamento

Il team deve confermare con il PO i picchi di utilizzo, le priorità di
ripristino e i tempi accettabili prima di verificare gli
[scenari di qualità](../quality-scenarios/README.md).

## Q-08 Ricezione delle comunicazioni

Il Banco può scrivere a tutte le OPT, a tutti gli operatori o a un gruppo di
destinatari. Il team ha scelto una conferma esplicita di ricezione e mostra
al Banco data e ora della prima conferma. Restano da definire i limiti degli
allegati e la composizione dei gruppi.
[#21](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/21) · [#55](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/55)

## Q-09 Negoziazione dell’appuntamento

Banco ed erogatore concordano l’appuntamento tramite il modulo di contatto.
Il Banco lo registra e lo conferma. Restano da definire proposte,
controproposte, dati minimi e vincoli, e da chiarire se la stessa domanda
riguardi anche gli appuntamenti a distanza.
[#38](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/38)

## Q-10 Priorità degli erogatori

Il Banco sceglie e contatta un erogatore alla volta. Il team deve chiarire con
il PO quali stati e quale periodo usare per calcolare il carico e come
sciogliere le parità. [#49](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/49)

## Q-11 Account degli operatori

Gli operatori del Banco, interni ed esterni pagati dal Banco, hanno account
personali. La OPT risponde e riceve assegnazioni con il proprio account anche
quando affida il servizio a un professionista esterno. Come conteggiare
queste attività resta da chiarire in
[Q-13](#q-13-dati-quantitativi-opt).
[#40](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/40) · [#45](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/45#issuecomment-6708235623)

## Q-12 Utenze del Banco

La #64 assegna al Banco la gestione delle utenze. Restano da chiarire gli
accessi di chi svolge anche il ruolo di operatore, le eventuali altre utenze
e come amministrarle. [#64](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/64)

## Q-13 Dati quantitativi OPT

Le OPT registrano i dati per anno e trimestre. Restano da definire voci, conteggio
di persone e accessi, distinzione fra zero e dato mancante e gestione delle
correzioni. Va chiarito come attribuire le attività dei professionisti che
lavorano per più OPT. [#41](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/41)

## Q-14 Conservazione delle schede

Per le copie conservate in PICUS, anche dopo l’invio al Ministero o la revoca
di un account, vanno concordati tempi, accessi e cancellazione con chi segue
la privacy. [#42](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/42)

## Q-15 Riattivazione e manutenzione profili

L’importazione da CSV conserva lo stato revocato. Restano da definire la
riattivazione, il riuso dell’indirizzo email e l’aggiornamento di recapiti,
misure e territorio. [#65](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/65)

## Q-16 Credenziali e link

La risposta alla #34 richiama le norme vigenti. Le soglie proposte nelle
[regole di accesso](regole-accessi.md) restano da validare con il referente
competente. [#34](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/34)

## Q-17 Indicazione del rifiuto

La #36 ha chiarito che basta il messaggio «Nessun erogatore disponibile»,
anche automatico. Il motivo dell’annullamento è trattato in
[Q-04](#q-04-confine-e-annullamento).
[#36](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/36)

## Q-18 Subentro nelle pratiche dopo revoca

Le pratiche e lo storico restano conservati dopo la revoca. Bisogna decidere
chi riassegna le richieste aperte o gli accordi confermati, chi subentra e
quali passaggi registrare. [#67](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/67)

## Q-19 Erogatore senza risposta

Il Banco gestisce il contatto in attesa. Il contesto non fissa una scadenza
automatica. Resta da stabilire quando può ritirare il contatto, se deve
avvisare l’erogatore e come trattare una risposta tardiva.
[#66](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/66)

## Q-20 Catalogo ritirato e attività pregresse

Il ritiro di un’offerta o la disattivazione di una misura lascia intatti i
riferimenti già registrati. Resta da stabilire come proseguire le pratiche
aperte e come rendicontare l’attività già svolta.

## Q-21 Titolarità dell’account del Banco

Resta da chiarire se l’account del Banco spetta al direttore o se più
segretari lo usano per coordinare le richieste. Da questa scelta dipendono
gli accessi per la gestione delle utenze e la tracciabilità delle operazioni.
[#84](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/84)
