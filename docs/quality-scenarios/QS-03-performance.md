# QS-03 Consultazione delle richieste durante il picco trimestrale

**Attributo di qualità** · Performance

**Priorità proposta** · P2

**Motivazione.** Durante la rendicontazione trimestrale, il Banco deve poter continuare a gestire le richieste mentre gli operatori inviano le schede personali e le OPT aggiornano i propri conteggi.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
|---|---|---|---|---|---|
| L’utente del Banco addetto al coordinamento delle richieste. | Il Banco apre l’elenco delle richieste *In programmazione* mentre gli operatori inviano le schede trimestrali e le OPT inviano i conteggi o nuove richieste. | PICUS è in normale operatività, con un carico di picco simulato. La prova usa **50 sessioni attive** e dati sintetici per **450 OPT, 2.000 richieste e 100 operatori**. Si avvia il carico di prova. Dopo 2 minuti, si iniziano a registrare i risultati e si continua per 15 minuti. | La pagina che mostra al Banco le richieste *In programmazione*. | PICUS mostra al Banco l’elenco delle richieste in ordine di arrivo e continua a elaborare gli invii in corso. | In almeno il **95% delle aperture**, l’elenco è visibile e consultabile entro **2 secondi** dal clic. Per ciascun tipo di invio, almeno il **95%** riceve la conferma di salvataggio entro **3 secondi** dall’invio nell’interfaccia. Gli errori del servizio restano entro l’**1%** delle operazioni valide e si registrano **zero invii confermati persi o duplicati**. |

## Approccio di verifica

Eseguire la prova sull’interfaccia dell’applicazione completa. Annotare la configurazione, le risorse, la rete e la distribuzione delle sessioni per ruolo. Il carico proposto nei **15 minuti di misurazione** è di **200 operazioni**.

| Operazione | Numero |
| --- | ---: |
| Apertura dell’elenco delle richieste da parte del Banco | 60 |
| Altre consultazioni da parte di OPT e operatori | 60 |
| Invio di una nuova richiesta da parte di una OPT | 40 |
| Invio di una scheda trimestrale da parte di un operatore | 20 |
| Salvataggio dei conteggi trimestrali da parte di una OPT | 20 |

Il Banco apre l’elenco ogni **15 secondi**. Ogni **45 secondi**, una di queste aperture coincide con l’avvio di quattro invii: due nuove richieste, una scheda operatore e un salvataggio dei conteggi OPT. Registrare l’ordine e le pause delle altre consultazioni per poter ripetere la prova.

Registrare separatamente i tempi delle aperture dell’elenco che coincidono con gli invii e i tempi dei tre tipi di invio. Confrontare le conferme con i dati salvati per individuare perdite o duplicati. I valori di carico e le soglie sono proposte da validare.
