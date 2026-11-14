# Registro dei chiarimenti e delle ipotesi

Versione 0.6 del **14/11/2026**. Il registro conserva gli identificativi Q-01–Q-20, ora in ordine crescente, aggiunge Q-21 per la nuova domanda #84 e recepisce i chiarimenti del contesto del 5 novembre e lo stato della bacheca [Domande al committente](https://github.com/orgs/Pinguini-alla-Fragola/projects/2/views/3).

## Riferimenti e stato

Le risposte della seconda tornata e i chiarimenti GitHub #21/#29/#36/#37/#40/#45 aggiornano le fonti precedenti. La revisione del 4 novembre recepisce come scelte del gruppo conferma del solo Banco, immutabilità, matrice annullamento, modulo minimo, pulsante di ricezione e viste OPT. Non si attribuisce alla professoressa un’approvazione puntuale e non si usa una scelta del gruppo come prova di risposta del cliente.

Q-03 distingue la richiesta del dato temporale minimo dal calendario facoltativo per misura. [Regole funzionali](regole-funzionali.md), [accessi](regole-accessi.md) e [tracciati](tracciati-monitoraggio.md) descrivono le regole comuni.

### Stato della bacheca al 14 novembre

| Bacheca | Issue | Stato | Quesiti collegati |
| --- | --- | --- | --- |
| Domanda #1 | #21 | Done | Q-08 |
| Domanda #2 | #29 | Done | Q-04 |
| Domanda #3 Politica delle password e dei link di accesso | #34 | Backlog | Q-16 |
| Domanda #4 | #36 | Done | Q-17 |
| Domanda #5 | #37 | Done | Q-04, IP-01 |
| Domanda #6 Negoziazione e controproposte prima della conferma | #38 | Backlog | Q-09 |
| Domanda #7 | #40 | Done | Q-11 |
| Domanda #8 Tracciati, conteggi e correzioni del monitoraggio | #41 | Backlog | Q-06, Q-13 |
| Domanda #9 Conservazione, accesso allo storico e cancellazione | #42 | Backlog | Q-05, Q-14 |
| Domanda #10 | #45 | Done | Q-11 |
| Domanda #11 | #64 | Done | Q-12 |
| Domanda #12 | #65 | Backlog | Q-01, Q-15 |
| Domanda #13 | #66 | Backlog | Q-19 |
| Domanda #14 | #67 | Backlog | Q-18 |
| Domanda #15 | #73 | Done | Q-03 |
| Domanda #16 Chi gestisce l’account del banco? | #84 | Backlog | Q-21, collegato a Q-12 |

Q-02, Q-07, Q-10 e Q-20 non hanno una domanda aperta in bacheca: si chiudono con il PO o con prove interne.

## Q-01 Continuità del referente OPT

**Stato corrente:** Un account e una email per OPT. Creazione manuale distinta da CSV.

**Residuo:** Chi aggiorna referente ed email e come si preserva la continuità? La domanda è in #65, ancora in Backlog e senza risposta del cliente.

**Impatto e collegamenti:** #24/#44/#65; Q-15.

## Q-02 Modulo minimo della richiesta

**Stato corrente:** Misura, persona/nucleo e comune sono obbligatori. `description` è facoltativa, in testo semplice e senza identificativi degli assistiti. Il sistema gestisce ID, data, titolare e stato. Il modulo esclude gli allegati.

**Residuo:** Validazioni di dettaglio in progettazione; eventuali preferenze prima dell’accordo nella #38. #41 riguarda ora i tracciati del monitoraggio.

**Impatto e collegamenti:** #48; scelta del gruppo recepita, non domanda generica da riaprire.

## Q-03 Disponibilità preventiva

**Stato corrente:** La disponibilità concreta si verifica sulla richiesta. [#73](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/73) è chiusa: il coordinamento avviene per contatti e il calendario per misura resta una funzionalità aggiuntiva che ogni OPT può scegliere di compilare per automatizzare il processo.

**Residuo:** Validare col PO e il committente il dato temporale minimo richiesto nella seconda tornata.

**Impatto e collegamenti:** #19/#47/#49/#73; TOBE-01B.

## Q-04 Confine e annullamento eccezionale

**Stato corrente:** Solo il Banco conferma. Il ciclo #29 comprende In programmazione, Confermata, Rifiutata e Annullata, con motivo obbligatorio sull’annullamento dopo la conferma. #37 chiude il confine: il Banco non registra l’avvenuta erogazione. Dettagli immutabili dopo conferma e [matrice di annullamento](regole-funzionali.md#matrice-di-annullamento) comune.

**Residuo:** Esempi di evento eccezionale, forma e visibilità del motivo, avvisi alle parti. Da valutare la proposta `Creata` per le richieste in coda e il passaggio a `In programmazione` alla presa in carico esplicita del Banco. Il ciclo di riferimento è quello della #29.

**Impatto e collegamenti:** #20/#28/#37/#52/#54, QS-01B.

## Q-05 Storico dopo revoca

**Stato corrente:** Revoca blocca accessi e conserva i dati senza cancellazione implicita.

**Residuo:** #42, in Backlog e senza risposta: tempi, accessi e cancellazione, senza conservazione illimitata. Operatività delle pratiche in Q-18.

**Impatto e collegamenti:** #26/#30/#42 e dati collegati.

## Q-06 Scheda personale e controlli

**Stato corrente:** Compilatori e campi iniziali noti in TM-OP-01; categorie sovrapponibili, nessun collegamento obbligatorio alle richieste. I professionisti esterni delle OPT non compilano la scheda trimestrale.

**Residuo:** #41, in Backlog e senza risposta: firma, obbligatorietà, controlli, correzioni, invii ripetuti e quali voci aggregare.

**Impatto e collegamenti:** #41/#57/#58; bozza v0.1, non tracciato definitivo.

## Q-07 Qualità e dimensionamento

**Stato corrente:** Catalogo aggiornato degli scenari con soglie del team; nessuna prova completa eseguita o SLA approvato.

**Residuo:** Confermare picchi, priorità di ripristino e tempi accettabili; poi eseguire le prove sui moduli disponibili. Nessuna domanda in bacheca: chiusura col PO.

**Impatto e collegamenti:** [Catalogo degli scenari](../quality-scenarios/README.md).

## Q-08 Ricezione delle comunicazioni

**Stato corrente:** #21 è chiusa: il Banco sceglie di volta in volta tra tutte le OPT, un gruppo di OPT, tutti gli operatori o un gruppo di operatori (multicast). Scelta di progetto: Confermo la ricezione, prima data/ora per account/comunicazione, visibile al Banco; sola apertura insufficiente.

**Residuo:** Limiti allegati e composizione dei gruppi da dettagliare nelle #22/#55. Automazione dei promemoria non introdotta.

**Impatto e collegamenti:** #55/#56; quesito generale sul riscontro superato dalla scelta del gruppo.

## Q-09 Negoziazione dell’appuntamento

**Stato corrente:** Banco ed erogatore concordano tramite modulo di contatto. Il Banco registra e conferma nell’app.

**Residuo:** Definire scambio di proposte e controproposte, dati minimi e vincoli prima dell’accordo. #38 resta in Backlog: la correzione dei dettagli è emersa come proposta di usabilità, non come richiesta del committente, ed è da chiarire se l’erogazione telematica rientri nella stessa domanda.

**Impatto e collegamenti:** #38/#49/#51/#52.

## Q-10 Priorità degli erogatori

**Stato corrente:** Misura, stesso comune e poi limitrofi, operatori del Banco prima delle OPT a pari prossimità, minor carico e remoto PICUS compatibile. Il Banco sceglie e contatta uno alla volta.

**Residuo:** Definire periodo/stati del carico e spareggi col PO. Nessuna domanda dedicata in bacheca.

**Impatto e collegamenti:** #49/#51; regola da seconda tornata.

## Q-11 Account degli operatori

**Stato corrente:** #40 e il [chiarimento aggiornato di #45](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/45#issuecomment-6708235623), entrambe chiuse, prevedono account personali per interni del Banco ed esterni pagati dal Banco. La OPT usa un unico account organizzativo: risponde e riceve l’assegnazione tramite il proprio account anche quando eroga tramite un professionista esterno, e i professionisti esterni non hanno account personali in PICUS.

**Residuo:** Inclusione e attribuzione delle attività dei professionisti nei conteggi organizzativi in Q-13.

**Impatto e collegamenti:** #18/#27/#28/#45/#51/#58.

## Q-12 Utenze del Banco

**Stato corrente:** [#64](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/64), chiusa, attribuisce all’account Banco la gestione degli utenti. Il ruolo Banco è distinto da quello Operatore.

**Residuo:** Credenziali e permessi per chi svolge anche attività da operatore, eventuali altre utenze e modalità di amministrazione. Titolarità e condivisione dell’account del Banco sono trattate a parte in Q-21.

**Impatto e collegamenti:** #18/#25/#27/#28/#64; Q-21.

## Q-13 Dati quantitativi OPT

**Stato corrente:** La OPT registra per anno e trimestre. Le viste trimestrale e cumulativa derivano dagli stessi dati.

**Residuo:** #41, in Backlog, riguarda voci, persone/accessi, zero e mancanti, correzioni e versione valida. Chiarire se includere le attività dei professionisti e come attribuirle se coinvolgono più OPT. Misure non più offerte in Q-20.

**Impatto e collegamenti:** #41/#43/#69; TM-OPT-01.

## Q-14 Conservazione delle schede

**Stato corrente:** Inserimento ministeriale non decide la sorte delle copie PICUS.

**Residuo:** #42, in Backlog, con referente amministrativo/privacy, per tutti i dati e anche dopo revoca.

**Impatto e collegamenti:** #42/#43/#57/#58; trattare insieme a Q-05.

## Q-15 Riattivazione e manutenzione profili

**Stato corrente:** Il CSV preserva lo stato dell’utenza revocata.

**Residuo:** Cambio referente/email, riuso email, ritorno dopo revoca e manutenzione di recapiti, misure e territorio. Tutto in #65, ancora in Backlog.

**Impatto e collegamenti:** #24/#27/#30/#44/#65; Q-01.

## Q-16 Credenziali e link

**Stato corrente:** #34 resta aperta in Backlog: l’unica risposta ricevuta è il richiamo generico alle normative vigenti alla data odierna e non definisce soglie approvate. Regole comuni propongono 12 caratteri, attivazione 72 ore, recupero 15 minuti e limiti ai tentativi.

**Residuo:** Il team propone e un referente competente valida policy e vincoli applicabili; non chiedere al cliente di inventare i parametri.

**Impatto e collegamenti:** #24/#25/#27/#33/#34/#44.

## Q-17 Indicazione del rifiuto

**Stato corrente:** #36 chiusa: mostrare Nessun erogatore disponibile; messaggio automatico ammesso.

**Residuo:** Nessun motivo libero obbligatorio per il rifiuto. Il motivo dell’annullamento è un requisito distinto.

**Impatto e collegamenti:** #52, già #53; recepita anche in TOBE-01A.

## Q-18 Subentro nelle pratiche dopo revoca

**Stato corrente:** La revoca invalida l’utenza e lascia le pratiche e lo storico conservati. Le pratiche restano nella loro assegnazione fino a una decisione di subentro.

**Residuo:** #67, in Backlog: chi subentra su richieste aperte o accordi confermati se è revocato l’account della OPT assegnata o l’account personale del Banco assegnato, chi decide e quali passaggi registrare. Conservazione e accesso allo storico in #42.

**Impatto e collegamenti:** #26/#30/#54/#67.

## Q-19 Erogatore senza risposta

**Stato corrente:** Il Banco interpella un erogatore per volta. Il contesto lascia al Banco la gestione del contatto senza risposta e non fissa un timeout automatico.

**Residuo:** #66, in Backlog: ritiro del contatto, eventuale avviso al destinatario e trattamento delle risposte tardive.

**Impatto e collegamenti:** #49/#51/#66.

## Q-20 Catalogo ritirato e attività pregresse

**Stato corrente:** Il ritiro dell’offerta o la disattivazione non cancellano riferimenti registrati.

**Residuo:** Definire prosecuzione delle richieste già aperte e registrazione delle attività effettive del trimestre se misura/offerta non è più attiva.

**Impatto e collegamenti:** #46/#47/#43; raffinamento col PO, non una nuova issue obbligatoria.

## Q-21 Titolarità dell’account del Banco

**Stato corrente:** #64 attribuisce all’account Banco la gestione delle utenze, ma non dice chi lo usa. [#84](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/84) è aperta in Backlog e senza risposta del cliente.

**Residuo:** L’account del Banco è condiviso da più segretari che gestiscono richieste e appuntamenti in parallelo, oppure appartiene al solo direttore? Nel primo caso serve un account dedicato al direttore per caricare nuove utenze; nel secondo serve un account distinto per chi coordina le richieste. Da definire anche la tracciabilità delle azioni compiute su un account condiviso e il rapporto con gli account personali da Operatore.

**Impatto e collegamenti:** #18/#25/#27/#28/#64/#84; Q-11/Q-12.

## Ipotesi e decisioni recepite

| ID | Riferimento corrente | Stato |
| --- | --- | --- |
| IP-01 | Quattro stati; accordo confermato, non prestazione svolta | #29 e #37 chiuse; baseline del gruppo; Accettata/Completata restano storici |
| IP-02 | Solo il Banco registra/conferma i dettagli concordati; immutabili dopo conferma | Scelta del gruppo; #38 limitata alla negoziazione precedente |
| IP-03 | Modulo con misura, persona/nucleo, comune e `description` facoltativa, più metadati del sistema | Scelta del gruppo; nessun identificativo assistito |
| IP-04 | Account, catalogo e offerta sintetici della demo | Prerequisito tecnico, non gestione completa implementata |
| IP-05 | 230.000 utenti non dimensiona account/concorrenza | Significato storico da precisare; QS con profili proposti |
| IP-06 | Ordine territoriale/tipo/carico e contatto sequenziale | Risposte seconda tornata; dati preventivi oggetto di Q-03 |

## Tracciabilità GitHub

La storia #52 assorbe #53, chiusa. #69 è la storia di consultazione dei conteggi OPT. #64, #65, #66 e #67 sono pubblicate. In bacheca restano aperte #34, #38, #41, #42, #65, #66, #67 e #84: finché non arrivano risposte, i relativi quesiti mantengono il residuo indicato sopra. #84 è l’unica priva di un identificativo precedente e apre il nuovo Q-21. La revisione dei requisiti e degli scenari precede la verifica sull’applicazione.
