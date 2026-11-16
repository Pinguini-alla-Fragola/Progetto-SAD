# Backend di coordinamento della richiesta

La [#49](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/49)
aggiunge presa in carico, ricerca, contatto e assegnazione al flusso di invio
della #48. Riutilizza login, sessioni, CSRF e risposta alla disponibilità
già presenti. Non introduce viste o nuovi test.

## Confini e transazioni

`RichiesteApi` estende anche `CoordinamentoRichiesteApi`, `CatalogoApi` estende
`RicercaErogatoriApi` e `ComunicazioniApi` estende `AvvisiOperativiApi`.
Nomi, firme e DTO dei contratti esistenti sono conservati; ogni servizio
implementa le capacità del proprio incremento.

`ServizioCoordinamentoRichieste` applica il Service Layer e usa il lock
pessimistico della richiesta, condiviso con `DisponibilitaService`.
Le regole di transizione sono nell'entità. Repository, controller e servizi
mantengono responsabilità separate e dipendenze ricevute per costruttore.

Un contatto e il relativo avviso in-app vengono registrati nella stessa
transazione. Comunicazioni scrive soltanto la propria casella: non invia email
o notifiche a gruppi. L'unicità del contatto in attesa è protetta anche da
un indice PostgreSQL. Fallimenti e comandi incompatibili non lasciano
assegnazioni, contatti o avvisi parziali.

## Ricerca e ordinamento

Le migrazioni V9/V10 aggiungono offerte OPT, legami operatore-misura,
appartenenza al Centro PICUS, ambito assegnato e avvisi persistenti. V11
aggiunge l'indice parziale PostgreSQL sul contatto in attesa. V12 assegna
un ordine persistente ai contatti, anche a parità di istante di invio.
Le offerte e le competenze devono essere dichiarate nei dati di catalogo
e nei profili: non si attribuiscono automaticamente tutte le misure agli
operatori o a una OPT senza offerta.

Catalogo legge le proprie offerte e adiacenze e risolve gli account tramite
`UtenzeApi`. Le offerte ritirate e gli account non attivi sono esclusi.
L'erogazione tramite professionista rimane associata all'account OPT.
Gli operatori partecipano solo per le misure del proprio profilo.
Una misura abilitata e un operatore del Centro PICUS consentono l'opzione
remota, anche quando lo stesso account compare come opzione locale.

L'ordine è: stesso comune, limitrofi, remoto; a pari prossimità, operatori
del Banco prima delle OPT e poi minor carico. `OrdinamentoErogatori` isola
la politica dal servizio e dalla persistenza.

Il carico adottato in questo incremento conta gli appuntamenti **Confermata**
con data/ora non ancora trascorsa. Esclude appuntamenti passati, Annullata e
pratiche senza appuntamento fissato. Nome e UUID risolvono le parità solo
per presentare un ordine stabile: non introducono una nuova priorità
funzionale. Sono scelte tecniche per il residuo Q-10, modificabili quando
il gruppo definirà periodo e criterio di carico.

L'elenco comprende tutte le opzioni compatibili. Il Banco può interpellare
il remoto quando tutte le opzioni locali correnti risultano declinate,
considerando l'ultimo contatto di ciascuna opzione. Senza candidati locali
il remoto è subito utilizzabile. Non sono introdotti timeout automatici,
ritiro dei contatti o trattamento delle risposte tardive: restano in Q-19/#66.

## Endpoint

Tutti richiedono sessione autenticata. I POST richiedono il token CSRF
aggiornato dopo il login; i corpi previsti usano JSON.

| Metodo e percorso | Operazione |
| --- | --- |
| `POST /api/richieste/{id}/presa-in-carico` | Solo Banco: Creata → Presa in carico; corpo assente o `{}`. |
| `GET /api/richieste/{id}/candidati` | Solo Banco: opzioni ordinate di una pratica Presa in carico. |
| `POST /api/richieste/{id}/contatti` | Solo Banco: crea il contatto in attesa e l'avviso per l'account scelto. |
| `GET /api/richieste/{id}/contatti` | Storico del Banco, già disponibile. |
| `GET /api/contatti-disponibilita/{id}` | Dati minimi del solo destinatario, già disponibile. |
| `POST /api/contatti-disponibilita/{id}/risposta` | Risposta del destinatario, già disponibile. |
| `POST /api/contatti-disponibilita/{id}/assegnazione` | Solo Banco: risposta positiva → In programmazione; corpo assente o `{}`. |
| `GET /api/avvisi` | Avvisi del solo account corrente, con riferimenti alla pratica e al contatto. |

La creazione del contatto accetta soltanto:

```json
{
  "erogatoreId": "00000000-0000-0000-0000-000000000001",
  "ambito": "STESSO_COMUNE"
}
```

L'UUID deve identificare un'opzione restituita dalla ricerca; l'esempio è
soltanto illustrativo. Gli ambiti sono `STESSO_COMUNE`, `COMUNE_LIMITROFO`
e `CENTRO_PICUS_A_DISTANZA`. Il server rifiuta campi extra e valori malformati
senza riportarne il contenuto nelle risposte.

La consultazione mantiene lo stato. Il rifiuto di un candidato conserva
**Presa in carico** e lo storico. Una risposta positiva non assegna da sola:
serve il comando esplicito del Banco sull'ultimo contatto di quell'opzione,
con account ancora attivo e compatibile
e nessun altro contatto in attesa. Assegnatario, ambito e **In programmazione**
vengono salvati insieme. Non sono aggiunti comandi di conferma dell'accordo,
rifiuto definitivo o annullamento.

Gli avvisi espongono solo riferimenti: il destinatario apre il contatto per
leggere i dati minimi. La descrizione della pratica rimane riservata a
OPT titolare e Banco anche dopo l'assegnazione. I codici HTTP seguono la
#48: `400` per dati non validi, `401` per assenza di sessione, `403` per
accesso vietato, `404` per risorsa inesistente e `409` per stato incompatibile.
