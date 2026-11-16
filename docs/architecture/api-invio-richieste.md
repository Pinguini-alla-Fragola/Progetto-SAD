# Backend di invio e consultazione delle richieste

L'incremento della [#48](https://github.com/Pinguini-alla-Fragola/Progetto-SAD/issues/48)
registra le richieste in **Creata**. Le letture non cambiano lo stato.
Presa in carico, ricerca, contatti, assegnazione e conferma dell'accordo
appartengono agli incrementi successivi.

## Contratti e responsabilità

`RichiesteApi`, `CatalogoApi` e `UtenzeApi` conservano nomi, firme e DTO.
Estendono rispettivamente `InvioRichiesteApi`, `ConsultazioneCatalogoApi` e
`SessioniApi`: queste capacità permettono di utilizzare l'incremento senza
dipendere dai comandi di coordinamento non ancora implementati.

`ServizioInvioRichieste` implementa i casi d'uso; `RichiestaDiMisura` rappresenta
la pratica e il repository di dominio separa la persistenza. L'adattatore JPA
di Richieste accede solo alle proprie tabelle. I riferimenti di catalogo e le
identità passano dalle API dei rispettivi moduli. Le dipendenze sono ricevute
per costruttore e l'orologio è un `Clock` iniettato.

La verifica dell'account passa da `SessioniApi.verificaAccountSessione`.
`AccountUserDetailsService` collega gli account persistenti all'autenticazione
Spring Security già presente, anche per richieste HTTP Basic durante
l'integrazione locale. Il principal ha come nome l'UUID dell'account; è
supportato anche un principal `IdentitaAutenticata`. Ruolo e stato attivo
vengono riletti sul server prima di accedere alle risorse. Non sono introdotti
nuovi endpoint di login o pagine. L'interfaccia di accesso e i controlli
specifici della #25 rimangono nel relativo incremento.

## Endpoint JSON

Tutti gli endpoint richiedono un account PICUS attivo. Per i client HTTP usare
`Accept: application/json`. I comandi conservano la protezione CSRF di Spring
Security; il client deve mantenere il cookie di sessione e inviare il token
nell'header restituito da `GET /api/richieste/csrf`.

| Metodo e percorso | Risultato e autorizzazione |
| --- | --- |
| `GET /api/catalogo/misure` | Misure attive selezionabili. |
| `GET /api/catalogo/comuni` | Codici e nomi dei comuni censiti. |
| `GET /api/richieste/csrf` | `headerName`, `parameterName` e `token` per la sessione corrente. |
| `POST /api/richieste` | Invio della OPT autenticata; `201`, sintesi e header `Location`. |
| `GET /api/richieste/mie` | Sintesi delle richieste della sola OPT autenticata. |
| `GET /api/richieste/coda` | Coda del solo Banco: Creata, Presa in carico e In programmazione. |
| `GET /api/richieste` | Elenco OPT o coda Banco in base al ruolo. |
| `GET /api/richieste/{id}` | Dettaglio per Banco, OPT titolare o account erogatore assegnato. |

Il corpo di invio usa `Content-Type: application/json`:

```json
{
  "misuraId": 1,
  "tipoDestinatario": "PERSONA",
  "comuneCodice": "napoli",
  "description": "Accompagnamento ai servizi del territorio."
}
```

`misuraId` è un intero positivo riferito a una misura attiva;
`tipoDestinatario` ammette `PERSONA` o `NUCLEO`; `comuneCodice` deve esistere.
`description` è facoltativa, fino a 1000 caratteri, e deve essere priva di
identificativi degli assistiti. Spazi esterni e descrizioni vuote vengono
normalizzati. Il testo viene restituito letteralmente come stringa JSON:
la vista deve renderlo come testo, con escaping, senza interpretarlo come HTML.
Il controllo dei campi non identifica automaticamente dati personali scritti
nella descrizione; tale valutazione resta aperta nell'ADR-003.

Non sono ammessi altri campi, inclusi titolare, stato e identificativi
dell'assistito. Il server rifiuta l'intero invio prima di scrivere. Gli errori
contengono solo messaggi controllati e indicazioni sui campi ammessi; non
riportano i valori ricevuti né il contenuto della descrizione. Il controller
non registra il corpo delle richieste nei log.

Il server assegna UUID, istante UTC, OPT titolare e stato Creata. Gli elenchi
sono ordinati per istante di invio e, in caso di parità, UUID. I nomi delle
misure rimangono risolvibili anche dopo la loro disattivazione.

## Ritenti e persistenza

L'header facoltativo `Idempotency-Key` contiene un UUID generato dal client
per il singolo invio logico. Ripetere lo stesso riferimento con gli stessi
dati normalizzati restituisce lo stesso identificativo; cambiare i dati con
quel riferimento produce `409`. Riferimenti diversi, oppure invii senza
riferimento, possono creare richieste distinte con contenuto uguale.

Il vincolo univoco su `(titolare_id, riferimento_invio)` e l'inserimento
PostgreSQL `ON CONFLICT` coordinano anche invii concorrenti da processi
diversi. La transazione usa `READ COMMITTED`, rilegge l'invio vincente e ne
confronta i dati senza sovrascriverli. Un ritento già registrato resta
riconoscibile anche se nel frattempo la misura è stata disattivata.

Le migrazioni additive V6 e V7 introducono il flag di erogabilità a distanza
del catalogo, il riferimento di invio, gli indici e i campi di lettura
dell'accordo. Il flag delle misure esistenti parte da `false`: la loro
abilitazione a distanza resta una scelta del catalogo. Non sono eseguiti
comandi di assegnazione o conferma in questo incremento.

## Visibilità e codici di errore

La OPT titolare e il Banco vedono la descrizione. Un erogatore assegnato vede
il dettaglio senza descrizione o identificativo della OPT titolare. Un'altra
OPT senza assegnazione riceve `403 FORBIDDEN` e nessun dato della pratica.
I dettagli di un accordo registrato sono consultabili anche dopo annullamento.

| Codice | Significato |
| --- | --- |
| `400` | Campi mancanti, extra, malformati o riferimenti di catalogo non validi. |
| `401` | Autenticazione mancante o account non più attivo. |
| `403` | Ruolo o relazione con la pratica non autorizzati; token CSRF non valido. |
| `404` | Identificativo della pratica inesistente. |
| `409` | Riferimento di invio già usato con dati diversi. |

I test della funzionalità sono affidati al gruppo di test; questo incremento
non aggiunge né modifica classi di test. Build e test già presenti restano
parte delle verifiche del progetto.
