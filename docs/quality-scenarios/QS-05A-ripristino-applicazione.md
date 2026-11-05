# QS-05A Ripristino dopo il fermo dell’applicazione

**Attributo di qualità** · Disponibilità

**Priorità proposta** · P2

**Motivazione.** Dopo un guasto dell’applicazione, gli utenti devono poter riprendere il lavoro dalle richieste già salvate.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
| --- | --- | --- | --- | --- | --- |
| Il processo che esegue l’applicazione PICUS. | Il processo si interrompe e PICUS smette di rispondere. | Prima del guasto PICUS è in normale operatività. Il database rimane integro e raggiungibile. | L’applicazione PICUS e il suo collegamento al database. | Chi interviene riattiva l’applicazione seguendo la procedura di ripristino. Gli utenti possono accedere, ritrovare le richieste già salvate e inviarne di nuove. | Entro **4 ore dal fermo**, un referente OPT riesce ad accedere, consultare una richiesta già confermata e inviarne una nuova. Il tempo si ferma quando compare la conferma e la nuova richiesta risulta salvata. Tutte le richieste salvate prima del guasto rimangono disponibili, con identificativi, stati e dettagli invariati. |

## Approccio di verifica

In ambiente di prova, preparare due richieste sintetiche, una *In programmazione* e una *Confermata*. Annotare identificativi, stati e dettagli. Interrompere l’applicazione e registrare l’ora del fermo. Dopo il ripristino, eseguire accesso, consultazione e nuovo invio, quindi confrontare le richieste recuperate con i dati annotati.

La soglia di ripristino è proposta.
