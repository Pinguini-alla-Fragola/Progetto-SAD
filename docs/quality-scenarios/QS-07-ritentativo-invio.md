# QS-07 Ripetere un invio dopo un’interruzione della connessione

**Attributo di qualità** · Affidabilità

**Priorità proposta** · P2

**Motivazione.** Una OPT può perdere la connessione dopo aver premuto *Invia*, prima di sapere se PICUS ha registrato la richiesta. Deve poter riprovare senza creare un duplicato che il Banco potrebbe prendere in carico.

## Scenario

| Source | Stimulus | Environment | Artifact | Response | Response measure |
| --- | --- | --- | --- | --- | --- |
| Un referente OPT autenticato con l’account della propria organizzazione. | Il referente OPT invia una nuova richiesta dal browser. Il server la salva, ma la connessione si interrompe prima che il referente riceva la conferma. Quando la connessione torna disponibile, il referente riprova lo stesso invio. | PICUS è in normale operatività. I dati della richiesta sono validi e l’interruzione riguarda la connessione tra il browser e il server. | La funzione di invio e registrazione di una nuova richiesta. | PICUS riconosce che il tentativo riguarda un invio già registrato e mostra la conferma con l’identificativo della richiesta salvata al primo tentativo. | Per quell’invio esiste **una sola richiesta**. L’identificativo mostrato alla OPT dopo il nuovo tentativo coincide con quello della richiesta che il Banco vede nell’elenco. |

## Approccio di verifica

Usare dati sintetici e simulare l’interruzione in due momenti distinti. Dopo il nuovo tentativo, controllare la conferma mostrata alla OPT e le richieste visibili al Banco.

| Caso | Esito atteso |
| --- | --- |
| La connessione cade **dopo** il salvataggio e prima della conferma. Il referente riprova lo stesso invio. | PICUS conferma la richiesta già salvata, con lo stesso identificativo. Il Banco vede una sola richiesta per quell’invio. |
| La connessione cade **prima** che il server riceva l’invio. Il referente riprova. | PICUS salva la richiesta e mostra la conferma. Il Banco vede una sola richiesta per quell’invio. |
| La OPT crea volutamente **due richieste distinte** con la stessa misura, lo stesso comune e la stessa indicazione *persona o nucleo*. | PICUS registra due richieste con identificativi diversi. |
