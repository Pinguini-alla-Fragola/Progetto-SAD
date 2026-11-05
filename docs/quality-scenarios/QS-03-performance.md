# QS-03 Risposta durante i picchi trimestrali

**Attributo di qualità:** Performance.

**Priorità proposta:** P2.

**Motivazione:** Vicino alla scadenza delle schede trimestrali, consultazioni e invii possono concentrarsi mentre il Banco continua a coordinare le richieste. L'applicazione deve mantenere tempi di risposta compatibili con queste attività.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
|---|---|---|---|---|---|
| OPT, Banco e Operatori che usano contemporaneamente l'applicazione. | Aumentano consultazioni e invii vicino alla scadenza trimestrale, mentre continua il coordinamento delle richieste. | Applicazione completa, con 50 sessioni concorrenti, 450 OPT, 2.000 richieste sintetiche, le schede di 100 operatori interni ed esterni pagati dal Banco e dati quantitativi per ciascuna OPT. Dopo l'avvio sono previsti due minuti di preparazione a carico nominale, esclusi dalle misure, e 15 minuti di carico. Configurazione, risorse e rete sono registrate prima della prova. | Consultazione delle richieste, invio delle richieste, schede personali degli operatori del Banco, dati quantitativi delle OPT e salvataggio dei dati. | Le operazioni rispettano i tempi previsti. Ogni invio per cui l'utente riceve una conferma di successo corrisponde a un solo inserimento salvato. I dinieghi di accesso attesi sono conteggiati separatamente dagli errori del servizio. | Soglie proposte: almeno il 95% delle consultazioni entro 2 secondi e il 95% degli invii entro 3 secondi, calcolati separatamente per ciascun tipo di operazione. Il tempo decorre dall'azione nell'interfaccia fino alla visualizzazione del risultato o della conferma di salvataggio. Errori di servizio non oltre l'1%; 0 invii confermati persi o duplicati. Mix di carico: 60% consultazioni, 20% invio richieste, 10% schede personali e 10% dati quantitativi OPT. |

## Approccio di verifica

Eseguire una prova di carico riproducibile attraverso l'interfaccia dell'applicazione completa. Registrare prima dell'esecuzione la sequenza delle operazioni, il ritmo degli invii e le attese simulate tra le azioni. Misurare i tempi fino alla visualizzazione del risultato, distinguendo le consultazioni dai diversi tipi di invio. Conservare tempi, errori e profilo di carico; confrontare le conferme di successo con i dati salvati per rilevare perdite o duplicati.
