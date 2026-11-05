# QS-03 Risposta durante i picchi trimestrali

**Attributo di qualità:** Performance.

**Priorità proposta:** P2.

**Motivazione:** Quando si avvicina la scadenza delle schede trimestrali, più utenti possono consultare e inviare dati nello stesso periodo. Anche durante il picco, l'applicazione deve rispondere in tempi che permettano agli utenti di svolgere queste attività e al Banco di continuare a coordinare le richieste.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
|---|---|---|---|---|---|
| OPT, Banco e operatori che usano l'applicazione nello stesso momento. | In prossimità della scadenza trimestrale aumentano le consultazioni e gli invii, mentre il Banco continua a coordinare le richieste. | La prova usa l'applicazione completa con 50 sessioni simultanee. I dati includono 450 OPT, 2.000 richieste sintetiche, le schede di 100 operatori interni ed esterni pagati dal Banco e dati quantitativi per ogni OPT. Dopo l'avvio si attendono due minuti a carico nominale per preparare la prova. Le misurazioni coprono i successivi 15 minuti di carico. Prima della prova si annotano la configurazione, le risorse e la rete. | La consultazione e l'invio delle richieste, le schede personali degli operatori del Banco, i dati quantitativi delle OPT e il salvataggio dei dati. | L'applicazione rispetta i tempi di risposta previsti e salva una sola volta ogni invio che conferma all'utente come riuscito. I rifiuti di accesso previsti si contano a parte rispetto agli errori del servizio. | L'obiettivo proposto è completare almeno il 95% delle consultazioni entro 2 secondi e almeno il 95% degli invii entro 3 secondi. Le percentuali si calcolano separatamente per tipo di operazione. Il tempo si misura dall'azione nell'interfaccia fino a quando appare il risultato o la conferma di salvataggio. Gli errori del servizio devono restare entro l'1%, con 0 invii confermati persi o duplicati. Il carico è composto per il 60% da consultazioni, per il 20% da invii di richieste, per il 10% da schede personali e per il 10% da dati quantitativi delle OPT. |

## Approccio di verifica

Eseguire una prova di carico ripetibile usando l'interfaccia dell'applicazione completa. Prima di iniziare, annotare l'ordine delle operazioni, la frequenza degli invii e le pause simulate tra le azioni. Misurare quanto passa prima che il risultato appaia, tenendo separate le consultazioni e i diversi tipi di invio. Conservare i tempi, gli errori e il profilo di carico. Confrontare le conferme di salvataggio con i dati salvati per individuare eventuali perdite o duplicati.
