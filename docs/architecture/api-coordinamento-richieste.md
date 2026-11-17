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
Le regole di transizione sono nel dominio `RichiestaDiMisura`; le porte
`RichiestaRepository` e `ContattiRepository` separano il coordinamento da JPA.
Repository, controller e servizi
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
V13 verifica anche a database che pratica e destinatario dell'avviso coincidano
con il contatto, e ammette un solo avviso per contatto e tipo. Le migrazioni
già pubblicate restano invariate.
Le offerte e le competenze devono essere dichiarate nei dati di catalogo
e nei profili: non si attribuiscono automaticamente tutte le misure agli
operatori o a una OPT senza offerta.

Catalogo legge le proprie offerte e adiacenze e risolve gli account tramite
`UtenzeApi`. Le offerte ritirate e gli account non attivi sono esclusi.
I profili OPT sono letti in blocco, senza una query per ciascuna offerta.
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
aggiornato dopo il login tramite `GET /api/auth/csrf`; i corpi previsti usano JSON.

| Metodo e percorso | Operazione |
| --- | --- |
| `POST /api/richieste/{id}/presa-in-carico` | Solo Banco: Creata → Presa in carico; corpo assente o `{}`. |
| `GET /api/richieste/{id}/candidati` | Solo Banco: opzioni ordinate di una pratica Presa in carico. |
| `POST /api/richieste/{id}/contatti` | Solo Banco: crea il contatto in attesa e l'avviso per l'account scelto. |
| `GET /api/richieste/{id}/contatti` | Storico del Banco, già disponibile. |
| `GET /api/richieste/{id}/contatti/{contattoId}` | Solo Banco: singolo contatto della pratica; destinazione del `Location` di creazione. |
| `GET /api/contatti-disponibilita/{id}` | Dati minimi del solo destinatario, già disponibile. |
| `POST /api/contatti-disponibilita/{id}/risposta` | Risposta del destinatario, già disponibile. |
| `POST /api/contatti-disponibilita/{id}/assegnazione` | Solo Banco: risposta positiva → In programmazione; corpo assente o `{}`. |
| `GET /api/avvisi` | Avvisi del solo account corrente, con riferimenti alla pratica e al contatto. |

Per i comandi senza campi, soltanto un corpo assente o l'oggetto JSON vuoto `{}`
sono validi. Il valore JSON `null` presente nel corpo riceve `400` e lascia
invariato lo stato della richiesta.

La creazione del contatto accetta soltanto:

```json
{
  "erogatoreId": "00000000-0000-0000-0000-000000000001",
  "ambito": "STESSO_COMUNE"
}
```

L'UUID deve identificare un'opzione restituita dalla ricerca; l'esempio è
soltanto illustrativo. Gli ambiti sono `STESSO_COMUNE`, `COMUNE_LIMITROFO`
e `CENTRO_PICUS_A_DISTANZA`. Il server rifiuta proprietà duplicate, campi extra e valori malformati
senza riportarne il contenuto nelle risposte.

La consultazione mantiene lo stato. Il rifiuto di un candidato conserva
**Presa in carico** e lo storico. Una risposta positiva non assegna da sola:
serve il comando esplicito del Banco sull'ultimo contatto di quell'opzione,
con account ancora attivo e compatibile
e nessun altro contatto in attesa. Assegnatario, ambito e **In programmazione**
vengono salvati insieme. Non sono aggiunti comandi di conferma dell'accordo,
rifiuto definitivo o annullamento.

La verifica finale mantiene lock condivisi fino al commit nella stessa
transazione dell'assegnazione. Ordine: pratica (scrittura), misura,
account/profilo/competenza personale, offerta OPT, eventuale adiacenza.
Catalogo e Utenze rileggono sotto lock esclusivamente i dati di propria
competenza, tramite API che richiedono una transazione già aperta. Una revoca,
modifica del profilo o ritiro dell'offerta concorrente si serializza con
l'assegnazione: se arriva prima impedisce il comando, se arriva dopo attende
il commit. Le conseguenze di revoche successive su pratiche già assegnate
restano nella decisione aperta D-08; non sono introdotti annullamenti automatici.

Gli avvisi espongono solo riferimenti: il destinatario apre il contatto per
leggere i dati minimi. La descrizione della pratica rimane riservata a
OPT titolare e Banco anche dopo l'assegnazione. I codici HTTP seguono la
storia #48: `400` per dati non validi, `401` per assenza di sessione, `403` per
accesso vietato, `404` per risorsa inesistente e `409` per stato incompatibile.

Comunicazioni verifica i riferimenti immutabili attraverso `DestinatariAvvisi`,
porta implementata dal modulo Richieste, prima di scrivere la propria casella.
Ripetere lo stesso avviso è innocuo; cambiare pratica o destinatario del
medesimo contatto viene rifiutato. Nessun dato della pratica entra nell'avviso.

## Aggiornamento di un database esistente

Prima dell'upgrade fare un backup e provare il ripristino su una copia. Eseguire
il controllo in sola lettura [preflight-richieste.sql](../../scripts/db/preflight-richieste.sql),
utilizzabile da V6 in poi, prima su quella copia e poi con le scritture applicative
sospese. Lo script contiene metacomandi del client `psql` e va eseguito così:

```powershell
Get-Content -Raw scripts/db/preflight-richieste.sql | docker compose exec -T picus-db psql -U picus -d picus -v ON_ERROR_STOP=1
```

Su V6–V9 viene eseguito il report dei contatti ancora aperti. La tabella degli
avvisi non esiste prima di V10: lo script verifica la sua presenza e segnala
esplicitamente che i due controlli sugli avvisi non sono applicabili, senza
interrompersi con `ON_ERROR_STOP=1`. Da V10 esegue anche quei due report.

Il risultato deve contenere zero righe in ogni report eseguito. Il callback
Flyway ripete il controllo prima di V11 e V13 e ferma l'upgrade con un messaggio
operativo se trova anomalie, prima di creare il relativo vincolo. Non modifica
lo storico né il checksum delle migrazioni precedenti.

Con due contatti `IN_ATTESA` sulla stessa pratica, non scegliere un vincitore
per data o UUID. Il Banco raccoglie i riscontri effettivi dai destinatari e
li fa registrare tramite il normale comando di risposta della versione
precedente (prima di sospendere le scritture). Una risposta negativa conserva
la pratica Presa in carico e il contatto nello storico. Ripetere il preflight
finché rimane al massimo un contatto aperto, poi sospendere le scritture e
aggiornare. Non trasformare una mancata risposta in indisponibilità.
Se i riscontri non sono ottenibili, rinviare l'upgrade e coinvolgere il PO
per Q-19/#66: il contesto non autorizza ritiro o timeout automatici.

Per avvisi orfani, duplicati o indirizzati ad altri account, sospendere
l'upgrade e far verificare al responsabile dei dati ogni riga riportata.
Conservare gli originali in un archivio di audit fuori dalla casella operativa
e concordare una correzione puntuale e tracciata prima di ripetere il controllo.
Il software non riassegna né elimina automaticamente avvisi già consegnati.
Ripristino e aggiornamento devono essere provati sulla copia riconciliata
prima del database condiviso; non usare `flyway repair` per aggirare il controllo.

## Dati sintetici per la demo

Il profilo `demo` crea gli account, ma non dichiara automaticamente competenze
o offerte reali. Sul solo database locale di demo, dopo l'avvio degli account,
eseguire la fixture [offerta-coordinamento.sql](../../scripts/demo/offerta-coordinamento.sql):

```powershell
Get-Content -Raw scripts/demo/offerta-coordinamento.sql | docker compose exec -T picus-db psql -U picus -d picus -v ON_ERROR_STOP=1
```

Lo script è ripetibile e richiede gli account sintetici già creati dal profilo.
Associa M01 e M04 all'operatore demo del Centro PICUS e M01 alla OPT B;
abilita la modalità a distanza di M04 solo in questo dataset. Non registra
risposte o richieste. Per una richiesta M01 a Napoli il Banco vedrà l'operatore
locale e la OPT B limitrofa; per M04 a Portici vedrà l'operatore limitrofo e
l'opzione remota, utilizzabile dopo l'esito negativo locale.
Non eseguire questa fixture su database di produzione o contenenti dati reali.
