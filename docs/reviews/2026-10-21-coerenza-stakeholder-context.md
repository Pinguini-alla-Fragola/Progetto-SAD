# Verifica della documentazione dopo il confronto con lo stakeholder

Data della verifica: **21 ottobre 2026**. Iterazione: **S1 - Preparazione**.
Ambito: visione, perimetro, glossario, stakeholder e Context Diagram **CTX-01**.

**Esito:** l'impostazione è coerente con il dominio, ma il confronto con lo
stakeholder è stato recepito solo in parte. Prima di considerare consolidata
S1-01 occorre allineare terminologia, stato delle funzionalità e fonti.
Il diagramma rappresenta correttamente le interazioni applicative principali;
richiede alcune precisazioni e una presentazione più leggibile.

Questa verifica propone correzioni; non modifica i requisiti concordati né
attesta approvazioni del committente. Le sezioni successive ad A2 sono state
consultate soltanto per individuare riferimenti da aggiornare. Il fatto che
as-is, to-be, backlog e MVP siano ancora da compilare è coerente con
l'avanzamento dichiarato e non costituisce un rilievo.

## Materiale confrontato

- `BA.docx`, nel workspace padre della repository: appunti del confronto con lo
  stakeholder. La data effettiva dell'incontro e il referente non sono indicati
  nel corpo del verbale e devono essere registrati senza dedurli dalla data del
  file. La proposta di identificativo per questa fonte è **F06**.
- [Documentazione Word corrente](../Documentazione_SAD_2026.docx): sezioni
  introduttive, A1, A2 e immagine incorporata di CTX-01; controlli incrociati
  limitati ad A5, A6, D1 e D2.
- [Esportazione PDF presente nella repository](../Documentazione_SAD_2026.pdf):
  versione precedente, ancora senza il diagramma e con circa 200 OPT.
- `01-PICUS_richieste_informali_del_committente (1).pdf`, §§2–3, 5–8 e
  appendice B; `00-Presentazione Progetto Picus.pdf`, slide 11 e 18.

## Elementi coerenti

Il Banco mantiene il coordinamento delle richieste e la scelta dell'erogatore.
La stessa OPT può richiedere una misura e dichiararsi erogatrice. La tabella
stakeholder recepisce che la richiesta al Banco nasce quando la OPT non riesce
a soddisfare autonomamente il bisogno. Il diagramma distingue richieste,
disponibilità, appuntamenti, comunicazioni e monitoraggio trimestrale.

Sono preservati i vincoli di privacy: nessun account degli assistiti e nessun
loro dato identificativo. La scheda trimestrale dell'operatore resta distinta
dalla richiesta. Il catalogo rimane configurabile dal Banco. L'inserimento nel
portale ministeriale è descritto come manuale, senza introdurre un'integrazione
applicativa non richiesta.

## Rilievi e correzioni proposte

### REV-CTX-01 Numeri del contesto e significato di utenti

**Evidenza:** A1 riporta circa 450 OPT, ma cita F02, che ne descrive circa 200.
Il verbale aggiorna il dato a **400–450 OPT**, conferma circa 200 comuni e
riporta «230.000 Utenti». In A1 il numero 230.000 compare subito dopo
«utenti contemporanei», creando un'ambiguità sul carico applicativo.

**Correzione proposta:** citare F06 per l'aggiornamento a 400–450 OPT e separare
quel dato dal dimensionamento software. Registrare il significato e il periodo
di riferimento dei 230.000 come chiarimento ancora necessario: il verbale non
consente di presentarli come accessi contemporanei. L'interpretazione come
persone assistite è plausibile, ma va esplicitamente confermata.

**Verifica:** A1 non attribuisce a F02 i dati del nuovo incontro; non deriva
concorrenza né numero di account dal numero di persone raggiunte dal programma.
Quando si aggiornerà A5, sostituire anche il richiamo residuo alle circa 200 OPT.

### REV-CTX-02 Operatori PICUS e operatori delle OPT

**Evidenza:** A2 delimita già l'operatore agli interni del Centro PICUS e ai
professionisti esterni. Il glossario A1 usa invece una definizione generale
della persona che eroga e presenta la scheda; CTX-01 mostra il solo nome
«OPERATORE». BA esclude esplicitamente l'invio della scheda al Banco da parte
degli operatori delle OPT e la gestione dei rapporti diretti con tali operatori.

**Correzione proposta:** qualificare nel glossario e nel diagramma l'attore
«Operatore PICUS / professionista esterno». Aggiungere che gli operatori e
volontari interni alle OPT restano nella gestione dell'organizzazione e non
sono un ulteriore ruolo applicativo previsto da questo flusso. Mantenere la
distinzione anche nella documentazione di dominio.

**Verifica:** l'invio della scheda personale e l'accesso dell'attore Operatore
non vengono estesi implicitamente al personale interno delle OPT.

### REV-CTX-03 Importazione massiva ancora opzionale

**Evidenza:** BA qualifica come «opzionale» l'inserimento annuale massivo delle
credenziali tramite CSV. A2 include «anche con caricamento massivo» e CTX-01
lo affianca alla creazione/revoca ordinaria senza indicarne lo stato.

**Correzione proposta:** marcare l'importazione CSV come funzionalità opzionale
da prioritizzare oppure rinviarla alle storie di dettaglio. Conservare come
capacità ordinaria la gestione degli account da parte del Banco. Registrare
separatamente le indicazioni del verbale sulla mancata scadenza automatica delle
credenziali e sull'intervento manuale del Banco per le OPT cessate.

**Verifica:** l'importazione CSV non appare obbligatoria o già inclusa nell'MVP.
La rimozione dell'accesso non viene equiparata alla cancellazione dei dati
storici, i cui tempi di conservazione restano da chiarire.

### REV-CTX-04 Ricezione e lettura delle comunicazioni

**Evidenza:** BA richiede comunicazioni importanti con conferma di ricezione e
possibile selezione di un sottogruppo di OPT. A2 parla di ricezione; CTX-01 usa
alternativamente «lettura/ricezione» e «ricezione/lettura». La frase del verbale
«No messaggi Broadcast» è accostata alla necessità di raggiungere le
organizzazioni e non definisce da sola il comportamento atteso.

**Correzione proposta:** usare per ora «conferma di ricezione» in modo uniforme.
Chiarire se sia una conferma esplicita dell'OPT o un riscontro tecnico e se
serva anche una distinta conferma di lettura. Mantenere la selezione dei
destinatari; precisare con lo stakeholder il significato di broadcast, senza
dedurne automaticamente il divieto di inviare lo stesso avviso a tutte le OPT.

**Verifica:** consegna, ricezione e lettura non sono trattate come sinonimi;
il ritorno informativo verso il Banco ha un significato verificabile.

### REV-CTX-05 Una email non definisce da sola il modello degli account

**Evidenza:** BA conferma «una singola email per OPT». A2 traduce questo punto
in «Un solo accesso per OPT (una email)». F02 presenta l'account unico come
ipotesi iniziale e Q-01 in A6 lascia ancora aperto il confronto con utenti
nominativi e continuità del referente.

**Correzione proposta:** distinguere il recapito unico confermato dal modello
degli accessi. Se nel confronto si è confermato anche un account unico, integrare
il verbale con tale risposta e aggiornare Q-01. In assenza di questa evidenza,
marcare l'equivalenza fra email unica e account condiviso come ipotesi.

**Verifica:** A2, Q-01 e il verbale non danno stati diversi alla medesima
decisione; recupero delle credenziali e cambio del referente restano distinguibili.

### REV-CTX-06 Indicazioni del round da registrare e dati trimestrali OPT

**Evidenza:** BA indica richieste in ordine di arrivo, nessun documento richiesto
per la registrazione e intervento manuale del Banco sulle OPT cessate.
Questi punti non sono ancora raccolti in una sintesi tracciabile del round.
Il verbale cita inoltre l'interesse per servizi e «scheda trimestrale» delle OPT,
ma esclude la scheda personale dei loro operatori. In CTX-01 il flusso OPT usa
la formula «dati quantitativi aggregati sulle attività svolte / scheda».

**Correzione proposta:** registrare le prime tre indicazioni come riscontri
del confronto, senza aggiungere altre frecce al context diagram. Distinguere
nel flusso OPT i dati quantitativi dell'organizzazione dalla scheda personale
dell'operatore PICUS. Chiarire se sia prevista una specifica scheda trimestrale
della OPT e con quali contenuti: BA non ne definisce ancora uno schema.

**Verifica:** il futuro processo rispetta l'ordine di arrivo senza introdurre
priorità di urgenza come requisito concordato; non richiede documenti di
registrazione; non trasferisce al Banco la gestione interna del personale OPT.
Le due forme di monitoraggio hanno nomi e responsabilità distinti.

### REV-CTX-07 Tracciabilità e versioni non allineate

**Evidenza:** D2 elenca F01–F05 ma non BA; A1 e A2 citano soltanto F02 anche per
informazioni nuove. Il frontespizio indica 0.2 del 18 ottobre, mentre il registro
contiene una riga 0.3 del 21 ottobre ancora priva di descrizione. D1 descrive
CTX-01 come «Da produrre o revisionare»; mancano i metadati del diagramma e
la revisione effettiva. La cartella `VisualParadigm/` locale non contiene file.
Il PDF della repository precede gli aggiornamenti e non contiene CTX-01.

**Correzione proposta:** aggiungere BA come F06 con data dell'incontro e
referente effettivi; collegare i singoli riscontri agli artefatti. Completare
la voce 0.3 e allineare il frontespizio. Indicare CTX-01 come «prodotto, da
revisionare» finché la revisione non è avvenuta; collegare sorgente modificabile,
esportazione, versione e data quando disponibili. Rigenerare il PDF dopo
l'integrazione delle correzioni. Aggiornare le sole parti di A6 interessate
dalle risposte ricevute, mantenendo aperti i sottoquesiti non risolti.

**Verifica:** ogni dato nuovo rimanda al confronto corretto; Word e PDF
rappresentano la stessa versione; non vengono inventati revisori o approvazioni.

### REV-CTX-08 Confini e leggibilità del diagramma

**Evidenza:** CTX-01 mostra Banco, OPT e Operatore come interlocutori diretti.
Assistiti, Ministero e responsabile privacy sono descritti nella tabella ma non
nel diagramma. Nell'esportazione del Word corrente, il diagramma è ridotto
a circa 17 × 6,9 cm nella pagina numerata 2, con etichette molto piccole.
La riga di revisione occupa da sola la pagina numerata 3.

**Correzione proposta:** conservare le interazioni applicative dirette e
aggiungere una nota che renda espliciti gli stakeholder senza accesso e il
passaggio manuale del Banco al portale ministeriale. Se si rappresentano anche
relazioni organizzative esterne, distinguerle in legenda e non disegnare un
collegamento applicativo diretto PICUS–assistito o PICUS–Ministero.

Raggruppare le etichette per capacità, spostando i campi dei moduli nei relativi
requisiti. Usare una pagina dedicata, eventualmente orizzontale, e tenere insieme
figura, didascalia e metadati di revisione. La disponibilità dello storico delle
schede, già disegnata, va identificata come proposta se non ulteriormente
confermata; il termine «transazioni» va definito come scambi/richieste di
servizio, evitando ambiguità con operazioni economiche.

**Verifica:** il diagramma è leggibile alla normale dimensione di pagina;
l'assenza di accesso degli assistiti e l'assenza di integrazione ministeriale
risultano esplicite. I dettagli opzionali non sembrano requisiti già approvati.

## Chiusura proposta per S1-01

Prima di consolidare l'artefatto, recepire i riscontri certi, qualificare le
ipotesi e registrare come domande i punti ambigui. Le precisazioni più rilevanti
da recuperare sono il significato dei 230.000 utenti, il rapporto fra email e
account, la semantica della conferma di ricezione e l'eventuale scheda della OPT.
Questi punti non impediscono di proseguire l'analisi as-is usando ipotesi visibili.

Il context diagram descrive stakeholder, confine del sistema e scambi
informativi; i dettagli dei singoli moduli di richiesta restano nei requisiti.

## Verifiche eseguite

Sono stati letti il verbale, le sezioni pertinenti delle fonti e il Word salvato.
Per leggere il Word aperto è stata usata una copia con accesso condiviso, senza
salvare modifiche all'originale. La copia è stata esportata in PDF con Microsoft
Word in sola lettura e rasterizzata con il renderer della skill documenti.
Sono state ispezionate le prime sei pagine, fino al diagramma e alla sua riga di
revisione, oltre all'immagine incorporata estratta dal Word.

In questa verifica non sono stati modificati il Word, il PDF o il README.
Le modifiche preesistenti ai documenti sono state preservate.
Non sono stati eseguiti test Maven: il controllo è documentale e la repository
non contiene ancora POM, wrapper o codice applicativo. Non sono stati eseguiti
commit, push o operazioni di pubblicazione.
