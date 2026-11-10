# Registro dei chiarimenti e delle ipotesi

Versione 0.4 del **07/11/2026**. Il registro conserva gli identificativi Q-01–Q-20 e recepisce i chiarimenti del contesto del 5 novembre e le issue già pubblicate.

## Riferimenti e stato

Le risposte della seconda tornata e i chiarimenti GitHub #21/#29/#36/#40/#45 aggiornano le fonti precedenti. La revisione del 4 novembre recepisce come scelte del gruppo conferma del solo Banco, immutabilità, matrice annullamento, modulo minimo, pulsante di ricezione e viste OPT. Non si attribuisce alla professoressa un’approvazione puntuale e non si usa una scelta del gruppo come prova di risposta del cliente.

Q-03 distingue la richiesta del dato temporale minimo dal calendario facoltativo per misura. [Regole funzionali](regole-funzionali.md), [accessi](regole-accessi.md) e [tracciati](tracciati-monitoraggio.md) descrivono le regole comuni.

## Q-04 Confine e annullamento eccezionale

**Stato corrente:** Solo il Banco conferma. Il ciclo #29 comprende In programmazione, Confermata, Rifiutata e Annullata. Dettagli immutabili dopo conferma e [matrice di annullamento](regole-funzionali.md#matrice-di-annullamento) comune.

**Residuo:** Esempi di evento eccezionale, forma e visibilità del motivo, avvisi alle parti. Da valutare la proposta `Creata` per le richieste in coda e il passaggio a `In programmazione` alla presa in carico esplicita del Banco. Il ciclo di riferimento è quello della #29.

**Impatto e collegamenti:** #20/#28/#52/#54, QS-01B.

## Q-09 Negoziazione dell’appuntamento

**Stato corrente:** Banco ed erogatore concordano tramite modulo di contatto. Il Banco registra e conferma nell’app.

**Residuo:** Definire scambio di proposte e controproposte, dati minimi e vincoli prima dell’accordo.

**Impatto e collegamenti:** #38/#49/#51/#52.

## Q-03 Disponibilità preventiva

**Stato corrente:** La disponibilità concreta si verifica sulla richiesta. Il calendario facoltativo per misura è un’estensione ([#73](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/73)).

**Residuo:** Validare col PO e il committente il dato temporale minimo richiesto nella seconda tornata.

**Impatto e collegamenti:** #19/#47/#49/#73; TOBE-01B.

## Q-10 Priorità degli erogatori

**Stato corrente:** Misura, stesso comune e poi limitrofi, operatori del Banco prima delle OPT a pari prossimità, minor carico e remoto PICUS compatibile. Il Banco sceglie e contatta uno alla volta.

**Residuo:** Definire periodo/stati del carico e spareggi col PO.

**Impatto e collegamenti:** #49/#51; regola da seconda tornata.

## Q-08 Ricezione delle comunicazioni

**Stato corrente:** #21 chiarisce tutti o gruppi OPT/Operatori. Scelta di progetto: Confermo la ricezione, prima data/ora per account/comunicazione, visibile al Banco; sola apertura insufficiente.

**Residuo:** Limiti allegati e composizione dei gruppi da dettagliare nelle #22/#55. Automazione dei promemoria non introdotta.

**Impatto e collegamenti:** #55/#56; quesito generale sul riscontro superato dalla scelta del gruppo.

## Q-11 Account degli operatori

**Stato corrente:** #40 e il [chiarimento aggiornato di #45](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/45#issuecomment-6708235623) prevedono account personali per interni del Banco ed esterni pagati dal Banco. La OPT risponde e riceve l’assegnazione tramite il proprio account anche quando eroga tramite un professionista esterno.

**Residuo:** Inclusione e attribuzione delle attività dei professionisti nei conteggi organizzativi in Q-13.

**Impatto e collegamenti:** #18/#27/#28/#45/#51/#58.

## Q-12 Utenze del Banco

**Stato corrente:** [#64](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/64) attribuisce all’account Banco la gestione degli utenti. Il ruolo Banco è distinto da quello Operatore.

**Residuo:** Credenziali e permessi per chi svolge anche attività da operatore, eventuali altre utenze e modalità di amministrazione.

**Impatto e collegamenti:** #18/#25/#27/#28/#64.

## Q-13 Dati quantitativi OPT

**Stato corrente:** La OPT registra per anno e trimestre. Le viste trimestrale e cumulativa derivano dagli stessi dati.

**Residuo:** #41 riguarda voci, persone/accessi, zero e mancanti, correzioni e versione valida. Chiarire se includere le attività dei professionisti e come attribuirle se coinvolgono più OPT. Misure non più offerte in Q-20.

**Impatto e collegamenti:** #41/#43/#69; TM-OPT-01.

## Q-05 Storico dopo revoca

**Stato corrente:** Revoca blocca accessi e conserva i dati senza cancellazione implicita.

**Residuo:** #42: tempi, accessi e cancellazione, senza conservazione illimitata. Operatività delle pratiche in Q-18.

**Impatto e collegamenti:** #26/#30 e dati collegati.

## Q-02 Modulo minimo della richiesta

**Stato corrente:** Misura, persona/nucleo e comune sono obbligatori. `description` è facoltativa, in testo semplice e senza identificativi degli assistiti. Il sistema gestisce ID, data, titolare e stato. Il modulo esclude gli allegati.

**Residuo:** Validazioni di dettaglio in progettazione; eventuali preferenze prima dell’accordo nella #38. #41 riguarda ora i tracciati del monitoraggio.

**Impatto e collegamenti:** #48; scelta del gruppo recepita, non domanda generica da riaprire.

## Q-01 Continuità del referente OPT

**Stato corrente:** Un account e una email per OPT. Creazione manuale distinta da CSV.

**Residuo:** Chi aggiorna referente ed email e come si preserva la continuità?

**Impatto e collegamenti:** #24/#44/#65; Q-15.

## Q-06 Scheda personale e controlli

**Stato corrente:** Compilatori e campi iniziali noti in TM-OP-01; categorie sovrapponibili, nessun collegamento obbligatorio alle richieste.

**Residuo:** #41: firma, obbligatorietà, controlli, correzioni, invii ripetuti e quali voci aggregare.

**Impatto e collegamenti:** #57/#58; bozza v0.1, non tracciato definitivo.

## Q-07 Qualità e dimensionamento

**Stato corrente:** Catalogo aggiornato degli scenari con soglie del team; nessuna prova completa eseguita o SLA approvato.

**Residuo:** Confermare picchi, priorità di ripristino e tempi accettabili; poi eseguire le prove sui moduli disponibili.

**Impatto e collegamenti:** [Catalogo degli scenari](../quality-scenarios/README.md).

## Q-14 Conservazione delle schede

**Stato corrente:** Inserimento ministeriale non decide la sorte delle copie PICUS.

**Residuo:** #42 con referente amministrativo/privacy, per tutti i dati e anche dopo revoca.

**Impatto e collegamenti:** #43/#57/#58; trattare insieme a Q-05.

## Q-15 Riattivazione e manutenzione profili

**Stato corrente:** Il CSV preserva lo stato dell’utenza revocata.

**Residuo:** Cambio referente/email, riuso email, ritorno dopo revoca e manutenzione di recapiti, misure e territorio.

**Impatto e collegamenti:** #24/#27/#30/#44/#65; Q-01.

## Q-16 Credenziali e link

**Stato corrente:** #34 non definisce soglie approvate. Regole comuni propongono 12 caratteri, attivazione 72 ore, recupero 15 minuti e limiti ai tentativi.

**Residuo:** Il team propone e un referente competente valida policy e vincoli applicabili; non chiedere al cliente di inventare i parametri.

**Impatto e collegamenti:** #24/#25/#27/#33/#44; #34 aperta.

## Q-17 Indicazione del rifiuto

**Stato corrente:** #36 chiusa: mostrare Nessun erogatore disponibile; messaggio automatico ammesso.

**Residuo:** Nessun motivo libero obbligatorio per il rifiuto. Il motivo dell’annullamento è un requisito distinto.

**Impatto e collegamenti:** #52, già #53; recepita anche in TOBE-01A.

## Q-18 Subentro nelle pratiche dopo revoca

**Stato corrente:** La revoca invalida l’utenza e lascia le pratiche e lo storico conservati. Le pratiche restano nella loro assegnazione fino a una decisione di subentro.

**Residuo:** Chi subentra su richieste aperte o accordi confermati se è revocato l’account della OPT assegnata o l’account personale del Banco assegnato? Conservazione e accesso allo storico in #42.

**Impatto e collegamenti:** #26/#30/#54/#67.

## Q-19 Erogatore senza risposta

**Stato corrente:** Il Banco interpella un erogatore per volta. Il contesto lascia al Banco la gestione del contatto senza risposta.

**Residuo:** Definire ritiro del contatto, eventuale avviso e trattamento delle risposte tardive.

**Impatto e collegamenti:** #49/#51/#66.

## Q-20 Catalogo ritirato e attività pregresse

**Stato corrente:** Il ritiro dell’offerta o la disattivazione non cancellano riferimenti registrati.

**Residuo:** Definire prosecuzione delle richieste già aperte e registrazione delle attività effettive del trimestre se misura/offerta non è più attiva.

**Impatto e collegamenti:** #46/#47/#43; raffinamento col PO, non una nuova issue obbligatoria.

## Ipotesi e decisioni recepite

| ID | Riferimento corrente | Stato |
| --- | --- | --- |
| IP-01 | Quattro stati; accordo confermato, non prestazione svolta | #29 e baseline del gruppo; Accettata/Completata restano storici |
| IP-02 | Solo il Banco registra/conferma i dettagli concordati; immutabili dopo conferma | Scelta del gruppo; #38 limitata alla negoziazione precedente |
| IP-03 | Modulo con misura, persona/nucleo, comune e `description` facoltativa, più metadati del sistema | Scelta del gruppo; nessun identificativo assistito |
| IP-04 | Account, catalogo e offerta sintetici della demo | Prerequisito tecnico, non gestione completa implementata |
| IP-05 | 230.000 utenti non dimensiona account/concorrenza | Significato storico da precisare; QS con profili proposti |
| IP-06 | Ordine territoriale/tipo/carico e contatto sequenziale | Risposte seconda tornata; dati preventivi oggetto di Q-03 |

## Tracciabilità GitHub

La storia #52 assorbe #53, chiusa. #69 è la storia di consultazione dei conteggi OPT. #64, #65, #66 e #67 sono pubblicate. La revisione dei requisiti e degli scenari precede la verifica sull’applicazione.
