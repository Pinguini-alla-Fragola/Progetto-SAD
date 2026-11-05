# QS-04C Rispondere a una richiesta di disponibilità da smartphone

**Attributo di qualità** · Usabilità

**Priorità proposta** · P1

**Motivazione.** Quando il Banco chiede a un operatore se è disponibile a erogare una misura, l’operatore deve poter rispondere da smartphone e capire se la risposta è stata registrata.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
| --- | --- | --- | --- | --- | --- |
| Un operatore interno del Banco o un operatore esterno pagato dal Banco, autenticato con il proprio account *Operatore*. | Il Banco chiede all’operatore se è disponibile a erogare la misura. L’operatore apre la notifica e comunica da smartphone se è disponibile o indisponibile, secondo la situazione descritta nella prova. | PICUS è in normale operatività. La richiesta è *In programmazione* e la notifica di disponibilità è già presente nell’app. | La notifica e la schermata per rispondere alla richiesta di disponibilità. | PICUS mostra la richiesta, registra la risposta selezionata e conferma l’esito all’operatore. | Almeno **4 operatori su 5** inviano la risposta prevista, **senza aiuto ed entro 1 minuto** dall’apertura della notifica alla conferma. |

## Approccio di verifica

Usare dati sintetici e coinvolgere cinque operatori. Il facilitatore indica se l’operatore è disponibile o indisponibile nella situazione di prova. Registrare esito, tempo ed eventuali difficoltà. Se il facilitatore indica un passaggio o un pulsante, il tentativo conta come svolto con aiuto.

Con un’area visibile di **360 × 800 pixel CSS**, verificare che i campi, le etichette e i pulsanti necessari siano raggiungibili senza scorrimento orizzontale.
