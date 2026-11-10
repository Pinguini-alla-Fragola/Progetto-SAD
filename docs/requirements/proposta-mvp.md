# Proposta di MVP per il coordinamento

Versione 0.2 del **04/11/2026**. Proposta da selezionare e stimare con PO e Developers; nessuna demo software o approvazione del committente presunta. Il percorso termina con l’accordo Confermato o con Rifiutata e Nessun erogatore disponibile. [Regole funzionali](regole-funzionali.md).

## Sei storie del primo percorso

| Storia | Contributo | Prerequisiti e limiti |
| --- | --- | --- |
| #25 | Login e logout | Recupero distinto #33; account sintetici già attivi |
| #28, porzione del percorso | Permessi per ruolo, titolare e assegnazione | Vincolo trasversale: verificarlo sui moduli integrati, non chiuderlo prima di tutto il prodotto |
| #48 | Invio e consultazione delle proprie richieste | Misura, persona/nucleo, comune e `description` facoltativa. Metadati del sistema. |
| #49 | Elenco ordinato e contatto di un erogatore per volta | Comuni/adiacenze e carico sintetici; periodo/spareggi da dettagliare |
| #51 | Risposta alla disponibilità | Assenso non conferma l’accordo; rifiuto singolo non rifiuta la pratica |
| #52 | Conferma Banco e dettagli visibili nello stesso esito, oppure rifiuto finale | Presenza/remoto; nessuna seconda approvazione finale nell’app; dati immutabili dopo conferma |

I sei riferimenti includono #52, che ha assorbito la #53, e la porzione pertinente del vincolo #28.

## Prerequisiti e funzioni successive

Preparare l’account Banco, due account organizzativi OPT e due account personali, uno interno e uno esterno pagato dal Banco. Predisporre catalogo, offerta, adiacenze e carico. Una OPT eroga tramite un professionista e risponde con il proprio account. Il calendario facoltativo per misura (#73) è un’estensione e il dato temporale minimo resta in Q-03.

Gestione utenti, CSV opzionale, recupero, catalogo/offerta tramite interfaccia, conteggi e consultazione OPT (#69), schede e comunicazioni sono incrementi successivi. #54 può seguire il primo percorso secondo la pianificazione. Il prodotto usa la [matrice di annullamento](regole-funzionali.md#matrice-di-annullamento).

## Dimostrazione prevista

1. OPT A accede, invia e ritrova richiesta con ID, data e In programmazione.
2. Banco vede erogatori ordinati e ne interpella uno; un rifiuto lascia la pratica aperta e consente un’alternativa.
3. Una OPT che eroga tramite un professionista risponde dal proprio account. La richiesta resta In programmazione fino all’accordo completo.
4. Banco ed erogatore concordano; solo il Banco registra dettagli completi e conferma. In un solo esito si ottengono Confermata e dettagli alla OPT, che informa l’assistito fuori dall’app. Modifiche successive negate.
5. Su altra pratica, esaurite alternative anche remote compatibili, il Banco registra Rifiutata e compare Nessun erogatore disponibile.
6. Accesso estraneo negato; erogatore autorizzato vede soltanto la propria assegnazione. Campi identificativi non ammessi non persistiti né copiati nei log. Logout invalida la sessione.

QS-01A, QS-02A, QS-02B e QS-04A/B/C coprono il percorso quando disponibile. QS-01B riguarda l’incremento #54 e QS-01C richiede la revoca. QS-03 completo richiede schede e conteggi, QS-05A/B procedure di ripristino, QS-06 la gestione del catalogo. #66/Q-19 riguarda il contatto senza risposta. Le prove sono pianificate.
