# Registro dei chiarimenti e delle ipotesi

Versione 0.1 del 25/10/2026. Registro documentale della v0.5; collegamenti operativi da mantenere insieme al Word. Dieci quesiti prioritari, con ID precedenti conservati.

## Q-04 Confine della richiesta e stati

PICUS termina il coordinamento quando l’appuntamento è confermato, oppure il Banco deve registrare anche l’avvenuta erogazione? Quali annullamenti devono essere trattati e da chi?

**Priorità proposta:** P1. **Fonte e stato:** F07 e proposta #20; domanda GitHub #29, aperta senza risposta alla consultazione della v0.5.

**Ipotesi provvisoria:** IP-01: Accettata chiude il coordinamento dopo la conferma dell’appuntamento; Completata resta una variante sospesa. Non sono definite transizioni di annullamento.

**Impatto:** TOBE-01A/B, US-D08–US-D10, MVP e criteri di chiusura. **Referente proposto:** PO per il follow-up con il committente.

[Domanda #29](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/29)

## Q-09 Responsabilità sull’appuntamento

Il Banco può fissare data e luogo oppure propone e registra dettagli concordati con l’erogatore? Chi dà la conferma finale?

**Priorità proposta:** P1. **Fonte e stato:** F07 descrive il coordinamento dei dettagli; F06 del 22/10 limita il Banco alla proposta.

**Ipotesi provvisoria:** IP-02: il Banco registra una proposta concordata e la pubblica solo dopo conferma dell’erogatore, senza assumere una gestione autonoma della sua agenda.

**Impatto:** TOBE-01B, US-D08 e dimostrazione MVP. **Referente proposto:** PO con committente ed erogatori.

## Q-03 Territorio disponibilità e vicinanza

Il divieto di associare OPT per distanza riguarda solo l’abbinamento automatico? Quali rappresentazioni di territorio e disponibilità sono utili al Banco?

**Priorità proposta:** P1. **Fonte e stato:** F02 §5.3 e F07 citano vicinanza; F06 del 22/10 contiene un divieto da precisare.

**Ipotesi provvisoria:** Scelta manuale dell’erogatore da parte del Banco, per misura e informazioni territoriali disponibili; nessun ordinamento automatico per distanza.

**Impatto:** #19, #20, US-D04 e US-D06. **Referente proposto:** Responsabile analisi con il committente.

## Q-10 Preferenza fra erogatori

Esiste una precedenza fra operatori interni PICUS, professionisti esterni e OPT erogatrici?

**Priorità proposta:** P2. **Fonte e stato:** Domanda presente in F07 senza risposta; appunti F06 non consolidati su questo punto.

**Ipotesi provvisoria:** Nessuna precedenza obbligatoria; il Banco sceglie manualmente e interpella un erogatore per volta.

**Impatto:** US-D06–US-D07; regole di selezione. **Referente proposto:** PO con il committente.

## Q-08 Destinatari e conferma delle comunicazioni

È ammesso inviare lo stesso avviso a tutte le OPT? La conferma richiesta è un gesto esplicito dell’OPT, un riscontro tecnico o una distinta conferma di lettura?

**Priorità proposta:** P2. **Fonte e stato:** F06 del 20/10; #21 aperta sul broadcast. La semantica della conferma è un chiarimento aggiuntivo da registrare.

**Ipotesi provvisoria:** Destinatari selezionati e dicitura conferma di ricezione; nessun divieto assoluto di invio a tutte le OPT viene assunto senza risposta.

**Impatto:** #22, US-D11–US-D12 e diagramma delle comunicazioni, fuori dal primo MVP. **Referente proposto:** Referente di #9 con il committente.

[Domanda #21](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/21)

## Q-11 Operatori con accesso e scheda

Quali categorie di operatori interni PICUS ed esterni ricevono un account e inviano la scheda trimestrale? Il personale interno delle OPT resta sempre escluso dal censimento individuale?

**Priorità proposta:** P1. **Fonte e stato:** F07 conferma l’accesso personale; alcuni sottoquesiti sulla rendicontazione esterna non hanno risposta esplicita. F06 esclude il personale interno delle OPT.

**Ipotesi provvisoria:** Account personali per gli erogatori accreditati individuati dal Banco; personale OPT non censito. Il perimetro definitivo della scheda resta da confermare.

**Impatto:** US-27, US-D02, US-D13 e TOBE-02. **Referente proposto:** Referenti IAM e rendicontazione con il committente.

## Q-12 Utenze del Banco

Quante persone del Banco useranno l’applicazione e chi crea o revoca le loro utenze?

**Priorità proposta:** P1. **Fonte e stato:** F02 attribuisce al Banco funzioni amministrative, ma non definisce il bootstrap dei suoi account.

**Ipotesi provvisoria:** Per la sola dimostrazione MVP si predispone un account Banco sintetico tramite configurazione iniziale; la politica operativa reale non è data per risolta.

**Impatto:** #18, US-25, avvio della dimostrazione e successiva gestione utenti. **Referente proposto:** PO con il referente del Banco.

## Q-13 Informazioni e monitoraggio delle OPT

Quali campi dell’anagrafica OPT sono necessari? Sono richiesti dati quantitativi o una specifica scheda dell’organizzazione, distinta da quella personale dell’operatore?

**Priorità proposta:** P2. **Fonte e stato:** F02 §5.2 e §6; F06 del 20/10; punti sospesi di #19.

**Ipotesi provvisoria:** Solo i campi necessari ad account, misura e territorio; la raccolta quantitativa dell’OPT è rinviata fino a definizione di contenuti e responsabilità.

**Impatto:** US-24, US-D01, #19 e schemi dati. **Referente proposto:** Referenti catalogo e rendicontazione con il committente.

## Q-05 Dati dopo la revoca

Quali tempi e regole di conservazione, cancellazione ed eventuale accesso ai dati storici si applicano dopo la revoca di OPT e Operatori?

**Priorità proposta:** P2. **Fonte e stato:** F02 §§5.1 e 7–8; F06 conferma revoca manuale e credenziali senza scadenza, non una conservazione illimitata.

**Ipotesi provvisoria:** La revoca blocca gli accessi e non cancella automaticamente i dati storici. Nessun termine di conservazione è inventato dal team.

**Impatto:** US-26, US-D02, schede e registri; interlocutore privacy. **Referente proposto:** PO e referente privacy del committente.

## Q-02 Campi non identificativi della richiesta

Quale insieme minimo di informazioni permette al Banco di lavorare una richiesta senza identificare la persona o il nucleo?

**Priorità proposta:** P1. **Fonte e stato:** F02 §5.3 e §8; F06 del 22/10; F07.

**Ipotesi provvisoria:** IP-03: misura di catalogo, indicazione persona/nucleo e comune scelto da elenco. Nessun testo libero o allegato dell’assistito nel primo MVP.

**Impatto:** US-D05, QS-02 e contratto del modulo di richiesta. **Referente proposto:** Referenti #20 con il committente.

## Risposte parziali e validazioni residue

**Q-01 Account OPT e cambio del referente**. Risposta parziale: unico account ed email unica per OPT confermati da F06/F07. Resta da precisare la continuità al cambio di referente; non si riapre il numero degli account. Recupero e gestione del referente: dettaglio da consolidare in S2.

**Q-06 Definizioni della scheda e controlli**. Risposta parziale: categorie sovrapponibili confermate da F07. Restano regole su ore, periodo, totali e correzioni; nessuna somma obbligatoria delle categorie al totale. Prima di implementare US-D13 e l’aggregazione.

**Q-07 Carichi e soglie di qualità**. Aperta: le esigenze sono nelle fonti; 50 sessioni, viewport 360, tempi e ripristino di 4 ore sono proposte dei sei QS, non misure eseguite o SLA concordati. Validare priorità e soglie prima delle prove in costruzione.

## Ipotesi provvisorie

**IP-01**. Chiusura del coordinamento come Accettata solo dopo conferma dell’appuntamento; Rifiutata quando non si trova soluzione. Nessuna gestione dell’esito della prestazione nel flusso principale. Proposta temporanea collegata a Q-04 / #29. Riesaminare alla risposta; se serve Completata, aggiornare stati, TOBE-01B, US-D09 e MVP.

**IP-02**. Il Banco registra dettagli concordati e attende conferma dell’erogatore prima della pubblicazione. Proposta collegata a Q-09. Non attribuisce al Banco il potere autonomo di fissare l’agenda.

**IP-03**. Richiesta strutturata con misura, persona/nucleo e comune; assenza di testo libero e allegati degli assistiti nel primo MVP. Proposta collegata a Q-02 e QS-02. Aggiornare lo schema alla validazione dei campi.

**IP-04**. Utenti, catalogo iniziale e offerta precaricati come dati sintetici per la dimostrazione. Prerequisito tecnico del prototipo; gestione completa degli account e dell’offerta non è dichiarata implementata. Q-12 per le utenze reali del Banco.

**IP-05**. Il dato 230.000 utenti non viene usato per dimensionare account o concorrenza. Significato e periodo da chiarire. Le soglie di QS-03 restano un profilo di prova proposto.

**IP-06**. Non si introducono timeout, priorità di urgenza, assegnazione automatica per distanza o ordine obbligatorio fra erogatori. Ordine cronologico confermato per le richieste; altre regole richiedono Q-03/Q-10 e confronto con il committente.
