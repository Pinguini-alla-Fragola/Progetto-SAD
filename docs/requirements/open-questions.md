# Registro dei chiarimenti e delle ipotesi

Versione 0.3 del **04/11/2026**, dopo il riscontro del team. Conservati Q-01–Q-17; aggiunti Q-18–Q-20 per casi operativi circoscritti. Le risposte delle issue chiuse e le scelte del gruppo non sono riproposte come quesiti generici. Le nuove domande elencate sono bozze, non issue già pubblicate.

## Riferimenti e stato

Le risposte della seconda tornata e i chiarimenti GitHub #21/#29/#36/#40/#45 aggiornano le fonti precedenti. La revisione del 4 novembre recepisce come scelte del gruppo conferma del solo Banco, immutabilità, matrice annullamento, modulo minimo, pulsante di ricezione e viste OPT. Non si attribuisce alla professoressa un’approvazione puntuale e non si usa una scelta del gruppo come prova di risposta del cliente.

La differenza sulla disponibilità preventiva resta esplicita in Q-03: la fonte richiedeva il dato temporale, il gruppo propone di non mostrarlo. [Regole funzionali](regole-funzionali.md), [accessi](regole-accessi.md) e [tracciati](tracciati-monitoraggio.md) sono i riferimenti locali delle regole e delle proposte tecniche. Stato e assegnatari del Project non sono dedotti dalle checklist.

## Q-04 Confine e annullamento eccezionale

**Stato corrente:** Definito il ciclo da #29 e dalla scelta di progetto: In programmazione, Confermata, Rifiutata, Annullata. Solo il Banco conferma; accordo e non prestazione. Matrice di annullamento e immutabilità nelle regole funzionali.

**Residuo:** Restano esempi di evento eccezionale, forma/visibilità del motivo e avvisi alle parti. Non riaprire matrice, conferma o ricerca automatica.

**Impatto e collegamenti:** #20/#52/#54; #29 e #37 chiuse.

## Q-09 Negoziazione dell’appuntamento

**Stato corrente:** Banco ed erogatore concordano; il solo Banco registra e conferma nell’app. Dettagli immutabili dopo conferma.

**Residuo:** Canale del coordinamento, controproposte e preferenze prima dell’accordo: #38. Non è richiesta una seconda approvazione finale nell’app.

**Impatto e collegamenti:** #49/#51/#52; #38 aperta.

## Q-03 Disponibilità preventiva

**Stato corrente:** Il gruppo sceglie offerta senza disponibilità preventive e contatto sulla singola richiesta. F10 richiedeva invece anche quando la OPT può erogare.

**Residuo:** Confronto col PO/committente: la semplificazione può sostituire il dato richiesto o occorre una forma minima? Divergenza esplicita, non una risposta del cliente né un obbligo di calendario a slot.

**Impatto e collegamenti:** #19/#47/#49; nuova domanda proposta, non pubblicata.

## Q-10 Priorità degli erogatori

**Stato corrente:** Misura, stesso comune/limitrofi, operatori prima delle OPT a pari prossimità, minor carico, remoto PICUS compatibile; Banco decide e contatta uno alla volta.

**Residuo:** Definire periodo/stati del carico e spareggi col PO. Non riaprire la precedenza generale.

**Impatto e collegamenti:** #49/#51; regola da seconda tornata.

## Q-08 Ricezione delle comunicazioni

**Stato corrente:** #21 chiarisce tutti o gruppi OPT/Operatori. Scelta di progetto: Confermo la ricezione, prima data/ora per account/comunicazione, visibile al Banco; sola apertura insufficiente.

**Residuo:** Limiti allegati e composizione dei gruppi da dettagliare nelle #22/#55. Automazione dei promemoria non introdotta.

**Impatto e collegamenti:** #55/#56; quesito generale sul riscontro superato dalla scelta del gruppo.

## Q-11 Account degli operatori

**Stato corrente:** #40/#45 chiuse: personali per interni Banco, esterni Banco ed esterni OPT. Gli ultimi hanno OPT di appartenenza e nessuna rendicontazione. Personale proprio/volontari OPT non censiti.

**Residuo:** Il trattamento delle attività degli esterni OPT nei conteggi rimane in Q-13, non l’account o il divieto della scheda personale.

**Impatto e collegamenti:** #18/#27/#28/#58.

## Q-12 Utenze del Banco

**Stato corrente:** Operatore interno e ruolo Banco distinti; account sintetico per la sola dimostrazione.

**Residuo:** Quante persone usano coordinamento/rendicontazione, chi ne amministra le utenze e come si gestisce chi svolge anche attività Operatore?

**Impatto e collegamenti:** #18/#25/#27/#28; nuova domanda proposta, non pubblicata.

## Q-13 Dati quantitativi OPT

**Stato corrente:** Registrazione per anno/trimestre e viste trimestrale/cumulativa scelte dal gruppo, stessa base dati senza doppio inserimento. Non implica scadenza di invio.

**Residuo:** #41: voci, persone/accessi, zero/mancanti, attività degli esterni OPT, correzioni e versione valida. Regole per misure non più offerte in Q-20.

**Impatto e collegamenti:** #43 e nuova consultazione OPT; TM-OPT-01 bozza v0.1.

## Q-05 Storico dopo revoca

**Stato corrente:** Revoca blocca accessi e conserva i dati senza cancellazione implicita.

**Residuo:** #42: tempi, accessi e cancellazione, senza conservazione illimitata. Operatività delle pratiche in Q-18.

**Impatto e collegamenti:** #26/#30 e dati collegati.

## Q-02 Modulo minimo della richiesta

**Stato corrente:** Scelti misura, persona/nucleo e comune; sistema gestisce ID/data/titolare/stato. Nessun testo libero, allegato o identificativo assistito nel modulo minimo.

**Residuo:** Validazioni di dettaglio in progettazione; eventuali preferenze prima dell’accordo nella #38. #41 riguarda ora i tracciati del monitoraggio.

**Impatto e collegamenti:** #48; scelta del gruppo recepita, non domanda generica da riaprire.

## Q-01 Continuità del referente OPT

**Stato corrente:** Account ed email unici per OPT confermati. Creazione manuale distinta da CSV.

**Residuo:** Chi modifica referente/email e come si conserva continuità e storico? Integrare la nuova domanda sui profili con Q-15.

**Impatto e collegamenti:** #24/#44; domanda proposta non pubblicata.

## Q-06 Scheda personale e controlli

**Stato corrente:** Compilatori e campi iniziali noti in TM-OP-01; categorie sovrapponibili, nessun collegamento obbligatorio alle richieste.

**Residuo:** #41: firma, obbligatorietà, controlli, correzioni, invii ripetuti e quali voci aggregare.

**Impatto e collegamenti:** #57/#58; bozza v0.1, non tracciato definitivo.

## Q-07 Qualità e dimensionamento

**Stato corrente:** Sei QS con soglie del team; nessuna prova completa eseguita o SLA approvato.

**Residuo:** Confermare picchi, priorità di ripristino e tempi accettabili; poi eseguire le prove sui moduli disponibili.

**Impatto e collegamenti:** QS-01–QS-06; 50 sessioni, campioni e 4 ore restano proposte.

## Q-14 Conservazione delle schede

**Stato corrente:** Inserimento ministeriale non decide la sorte delle copie PICUS.

**Residuo:** #42 con referente amministrativo/privacy, per tutti i dati e anche dopo revoca.

**Impatto e collegamenti:** #43/#57/#58; trattare insieme a Q-05.

## Q-15 Riattivazione e manutenzione profili

**Stato corrente:** Nessuna riattivazione automatica da CSV; identità e storico non si ricreano implicitamente.

**Residuo:** Ritorno dopo revoca, riuso email, modifica recapiti/misure/territorio e passaggio dell’esterno fra OPT. Definire chi modifica cosa; poi valutare una storia di manutenzione, senza inventarne i permessi.

**Impatto e collegamenti:** #24/#27/#30/#44; nuova domanda proposta insieme a Q-01.

## Q-16 Credenziali e link

**Stato corrente:** #34 non definisce soglie approvate. Regole comuni propongono 12 caratteri, attivazione 72 ore, recupero 15 minuti e limiti ai tentativi.

**Residuo:** Il team propone e un referente competente valida policy e vincoli applicabili; non chiedere al cliente di inventare i parametri.

**Impatto e collegamenti:** #24/#25/#27/#33/#44; #34 aperta.

## Q-17 Indicazione del rifiuto

**Stato corrente:** #36 chiusa: mostrare Nessun erogatore disponibile; messaggio automatico ammesso.

**Residuo:** Nessun motivo libero obbligatorio per il rifiuto. Il motivo dell’annullamento è un requisito distinto.

**Impatto e collegamenti:** #52, già #53; recepita anche in TOBE-01A.

## Q-18 Pratiche e account collegati dopo revoca

**Stato corrente:** La revoca invalida l’utenza; non assegna automaticamente le pratiche e non revoca a cascata gli esterni OPT.

**Residuo:** Chi subentra su richieste aperte o accordi Confermati? Quale sorte hanno gli account personali esterni quando si revoca la OPT?

**Impatto e collegamenti:** #26/#30/#49/#54; nuova domanda proposta, non pubblicata.

## Q-19 Erogatore senza risposta

**Stato corrente:** Un contatto per volta e nessun timeout obbligatorio.

**Residuo:** Il Banco può ritirare il contatto e passare al successivo? Come avvisare il primo e gestire risposte tardive?

**Impatto e collegamenti:** #49/#51; nuova domanda proposta, non pubblicata.

## Q-20 Catalogo ritirato e attività pregresse

**Stato corrente:** Il ritiro dell’offerta o la disattivazione non cancellano riferimenti registrati.

**Residuo:** Definire prosecuzione delle richieste già aperte e registrazione delle attività effettive del trimestre se misura/offerta non è più attiva.

**Impatto e collegamenti:** #46/#47/#43; raffinamento col PO, non una nuova issue obbligatoria.

## Ipotesi e decisioni recepite

| ID | Riferimento corrente | Stato |
| --- | --- | --- |
| IP-01 | Quattro stati; accordo confermato, non prestazione svolta | #29 e baseline del gruppo; Accettata/Completata restano storici |
| IP-02 | Solo il Banco registra/conferma i dettagli concordati; immutabili dopo conferma | Scelta del gruppo; #38 limitata alla negoziazione precedente |
| IP-03 | Modulo misura, persona/nucleo e comune, più metadati del sistema | Scelta del gruppo; nessun identificativo assistito |
| IP-04 | Account, catalogo e offerta sintetici della demo | Prerequisito tecnico, non gestione completa implementata |
| IP-05 | 230.000 utenti non dimensiona account/concorrenza | Significato storico da precisare; QS con profili proposti |
| IP-06 | Ordine territoriale/tipo/carico e contatto sequenziale | Risposte seconda tornata; dati preventivi oggetto di Q-03 |

## Aggiornamento GitHub da eseguire

Bozze per #34/#38/#41/#42 e nuovi quesiti nel pacchetto preparato per la revisione. #52 assorbe #53; conservare quest’ultima come riferimento storico dopo l’aggiornamento dei link. Nuova consultazione OPT sotto #19 senza numero finché non creata. Non riaprire #21/#29/#36/#37/#40/#45 per argomenti già risolti; registrare soltanto i residui pertinenti. Non risultano stime o prove software da questa revisione.
