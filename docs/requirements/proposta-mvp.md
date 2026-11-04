# Proposta di MVP per il coordinamento

Versione 0.2 del **04/11/2026**. Proposta da selezionare e stimare con PO e Developers; nessuna demo software o approvazione del committente presunta. Il percorso termina con l’accordo Confermato o con Rifiutata e Nessun erogatore disponibile. [Regole funzionali](regole-funzionali.md).

## Sei storie del primo percorso

| Storia | Contributo | Prerequisiti e limiti |
| --- | --- | --- |
| #25 | Login e logout | Recupero distinto #33; account sintetici già attivi |
| #28, porzione del percorso | Permessi per ruolo, titolare e assegnazione | Vincolo trasversale: verificarlo sui moduli integrati, non chiuderlo prima di tutto il prodotto |
| #48 | Invio e consultazione delle proprie richieste | Misura, persona/nucleo e comune; metadati del sistema |
| #49 | Elenco ordinato e contatto di un erogatore per volta | Comuni/adiacenze e carico sintetici; periodo/spareggi da dettagliare |
| #51 | Risposta alla disponibilità | Assenso non conferma l’accordo; rifiuto singolo non rifiuta la pratica |
| #52 | Conferma Banco e dettagli visibili nello stesso esito, oppure rifiuto finale | Presenza/remoto; nessuna seconda approvazione finale nell’app; dati immutabili dopo conferma |

Sono sei riferimenti dopo l’accorpamento proposto: #53 esiste ancora su GitHub finché non aggiornata e chiusa come assorbita. L’incremento usa soltanto la porzione pertinente di #28; non attesta che tutti i permessi del prodotto siano già realizzati.

## Prerequisiti e funzioni successive

Account sintetici per Banco, due OPT e operatori delle tre tipologie; catalogo iniziale e offerta, adiacenze e carico. Il progetto non mostra disponibilità preventive: Q-03 conserva il confronto necessario col PO sul dato temporale chiesto dal cliente.

Gestione utenti, CSV opzionale, recupero, catalogo/offerta tramite interfaccia, conteggi e consultazione OPT, schede personali e comunicazioni sono incrementi successivi. #54 può seguire il primo percorso: la matrice è già scelta, non va inventata al momento dell’implementazione. Annullo solo da Confermata per evento eccezionale con motivo e senza riaprire la ricerca. La scelta di rinviarlo è di pianificazione, non un’eliminazione dal prodotto.

## Dimostrazione prevista

1. OPT A accede, invia e ritrova richiesta con ID, data e In programmazione.
2. Banco vede erogatori ordinati e ne interpella uno; un rifiuto lascia la pratica aperta e consente un’alternativa.
3. Il secondo dà disponibilità: la richiesta resta In programmazione finché manca l’accordo completo.
4. Banco ed erogatore concordano; solo il Banco registra dettagli completi e conferma. In un solo esito si ottengono Confermata e dettagli alla OPT, che informa l’assistito fuori dall’app. Modifiche successive negate.
5. Su altra pratica, esaurite alternative anche remote compatibili, il Banco registra Rifiutata e compare Nessun erogatore disponibile.
6. Accesso estraneo negato; erogatore autorizzato vede soltanto la propria assegnazione. Campi identificativi non ammessi non persistiti né copiati nei log. Logout invalida la sessione.

QS-01/QS-02 e la porzione mobile QS-04 verificano il percorso quando disponibile. QS-03 completo richiede moduli di schede e conteggi; QS-05 runbook reale; QS-06 gestione catalogo. Q-19 riguarda un erogatore che non risponde; non introdurre un timeout implicito. La Review di preparazione presenta artefatti e scelte, non test applicativi mai eseguiti.
