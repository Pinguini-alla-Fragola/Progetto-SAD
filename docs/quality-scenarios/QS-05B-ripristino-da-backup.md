# QS-05B Ripristino da backup dopo la perdita del database

**Attributo di qualità** · Disponibilità

**Priorità proposta** · P2

**Motivazione.** Se il database diventa inutilizzabile, PICUS deve poter riprendere il servizio a partire da una copia dei dati.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
| --- | --- | --- | --- | --- | --- |
| Il database di PICUS. | Un guasto rende il database inutilizzabile e interrompe il servizio. | Prima del guasto PICUS è in normale operatività. È disponibile una copia di backup, conservata separatamente dal database guasto, e si conosce il momento a cui risalgono i dati della copia. | Il backup, il database su cui viene ripristinato e il collegamento dell’applicazione al database ripristinato. | Chi interviene ripristina la copia su un database funzionante e ricollega PICUS. Gli utenti possono consultare le richieste recuperate e inviarne di nuove. | Entro **8 ore dal guasto**, un referente OPT riesce ad accedere, consultare una richiesta recuperata e inviarne una nuova con conferma di salvataggio. Il punto a cui risalgono i dati recuperati precede il guasto di **al massimo 24 ore**. Tutte le richieste presenti al punto di ripristino vengono recuperate con i relativi identificativi, stati e dettagli. |

## Approccio di verifica

In ambiente di prova, salvare alcune richieste sintetiche prima del backup e aggiungerne una dopo. Simulare il guasto e ripristinare la copia su un database vuoto. Verificare quali dati sono stati recuperati e confrontarli con quelli presenti al momento della copia. Misurare il tempo dal guasto fino alla conferma di un nuovo invio. Le **24 ore** si misurano tra il punto a cui risalgono i dati recuperati e il momento del guasto.

Le soglie di ripristino e di recupero dei dati sono proposte.
