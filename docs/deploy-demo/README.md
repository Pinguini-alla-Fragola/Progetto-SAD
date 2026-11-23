# Demo PICUS su VPS

La demo è raggiungibile su `https://demo.picus.strawberrypenguin.it`. Caddy gestisce HTTPS e verifica gli inviti prima di inoltrare le richieste a Spring Boot su `127.0.0.1:8080`. PostgreSQL e Postfix usano reti Docker private. I dati della demo sono sintetici.

## Configurare il DNS in Cloudflare

1. Accedi a Cloudflare e apri la zona `strawberrypenguin.it`. In **DNS → Records**, premi **Add record**. Se esiste già un record per `demo.picus.strawberrypenguin.it`, controlla dove punta prima di sostituirlo.
2. Imposta il tipo **A** e usa i seguenti valori.

   | Campo | Valore |
   | --- | --- |
   | **Name** | `demo.picus` |
   | **IPv4 address** | L'IPv4 pubblico del VPS indicato nel pannello del provider |
   | **Proxy status** | **DNS only** (nuvola grigia) |
   | **TTL** | **Auto** |

3. Salva e verifica che il nome risultante sia `demo.picus.strawberrypenguin.it`. Se trovi un record `AAAA` per quel nome, mantienilo solo se l'IPv6 pubblico del VPS è effettivamente raggiungibile su `80` e `443`.

Caddy ottiene e rinnova il certificato quando il nome risolve verso il VPS e le porte TCP `80` e `443` sono raggiungibili anche attraverso il firewall del provider. [Gestione dei record Cloudflare](https://developers.cloudflare.com/dns/manage-dns-records/how-to/create-dns-records/) · [HTTPS automatico di Caddy](https://caddyserver.com/docs/automatic-https)

## Verificare la pubblicazione

Da PowerShell, esegui questi controlli dopo la propagazione DNS.

```powershell
Resolve-DnsName -Name demo.picus.strawberrypenguin.it -Type A -Server 1.1.1.1
curl.exe -I http://demo.picus.strawberrypenguin.it/login
curl.exe -I -H "Accept: text/html" https://demo.picus.strawberrypenguin.it/login
curl.exe -I https://demo.picus.strawberrypenguin.it/invito
```

- La query DNS restituisce l'IPv4 del VPS. HTTP risponde `308` verso lo stesso percorso in HTTPS.
- HTTPS presenta un certificato valido e `/login` risponde `303` verso `/invito` per una navigazione senza sessione. `/invito` risponde `200`.
- Apri il link d'invito oppure inserisci il codice nella pagina `/invito`. Premi **Entra nella demo**, conserva il codice di rientro mostrato al primo accesso e accedi a PICUS con uno degli account sintetici.

Se DNS è corretto ma il certificato è ancora in attesa, controlla sul VPS `rc-service caddy status` e `tail -n 50 /var/log/caddy/caddy.log`. Dopo aver corretto la causa puoi riavviare il servizio con `rc-service caddy restart`.

### Proxy Cloudflare facoltativo

Prima di attivare la nuvola arancione, verifica che Cloudflare abbia un certificato edge attivo per il nome esatto. Se la zona completa è `strawberrypenguin.it`, Universal SSL copre il primo livello e il nome della demo richiede copertura aggiuntiva. Con il proxy attivo, usa **Full (strict)** per verificare il certificato di Caddy all'origine. Verifica gli effetti sugli altri sottodomini prima di cambiare un'impostazione a livello di zona. Ripeti i controlli HTTPS e di login dopo il cambio. [Copertura Universal SSL](https://developers.cloudflare.com/ssl/edge-certificates/universal-ssl/limitations/) · [Modalità Full (strict)](https://developers.cloudflare.com/ssl/origin-configuration/ssl-modes/full-strict/)

Prima di usare il proxy Cloudflare, configura in Caddy gli indirizzi Cloudflare come proxy fidati e la sorgente dell'IP del visitatore. Il limite per IP usa l'indirizzo calcolato da Caddy. Con **DNS only** è l'indirizzo della connessione al VPS. [Proxy fidati in Caddy](https://caddyserver.com/docs/caddyfile/options#trusted-proxies)

## Gestire la demo sul VPS

Il progetto Compose è `picus-demo`. Il wrapper sul server seleziona i file di configurazione e il file ambiente corretti.

```sh
rc-service picus-demo-firewall status
rc-service docker status
rc-service caddy status
rc-service picus-demo-invites status
/opt/picus-demo/compose ps
/opt/picus-demo/compose logs --tail=50 picus-webapp
curl -fsS -o /dev/null http://127.0.0.1:8080/login
```

### Generare e revocare gli inviti

Esegui i comandi via SSH sul VPS. Sostituisci `ID_INVITO` con l'identificativo restituito da `crea` o `elenca`.

```sh
picus-inviti crea --etichetta "revisore-01" --ore 36
picus-inviti elenca
picus-inviti revoca ID_INVITO
```

Per generare gli inviti tramite uno script, aggiungi `--json` a `crea` e leggi i campi `id`, `durata_ore`, `attiva_entro`, `codice` e `url`. Alla creazione `scadenza` è `null`. `--ore` accetta un numero intero positivo e il valore predefinito è 24. Condividi privatamente il link o il codice. `elenca` mostra identificativo, etichetta, stato e il termine di attivazione o la scadenza dell'accesso, in UTC.

L'invito va confermato per la prima volta entro 48 ore dalla creazione. Da quella conferma decorrono le ore impostate con `--ore` e appare il codice di rientro con il relativo link. I due codici funzionano anche in altri browser, con la stessa scadenza. `revoca` blocca entrambi i codici e tutte le relative sessioni. Chi riceve uno dei codici inoltrati può usarlo finché è valido. Gli inviti riutilizzabili già in attesa all'aggiornamento hanno 48 ore dall'aggiornamento per essere attivati. Gli inviti già attivati e gli inviti monouso precedenti conservano le loro scadenze.

Il riscatto accetta fino a 30 richieste per IP e 300 complessive in una finestra di 60 secondi. Quando il limite è raggiunto, la pagina indica quanto attendere. Il limite riguarda il riscatto e lascia accessibili le sessioni già attive.

Dopo il riscatto serve la password di PICUS. Leggila sul VPS con `sed -n 's/^PICUS_DEMO_PASSWORD=//p' /opt/picus-demo/runtime.env`. Gli account sintetici sono `banco@picus.test`, `opt-a@picus.test`, `opt-b@picus.test` e `operatore@picus.test`.

Il servizio di invito ascolta solo su `127.0.0.1:8787`. Il database degli inviti è separato da PICUS e contiene gli hash dei token e delle sessioni. I sorgenti del servizio e le regole Caddy si trovano in [scripts/demo/invites](../../scripts/demo/invites/).

### Collaudare le e-mail di attivazione

Accedi come Banco e registra una OPT con un indirizzo fittizio `@picus.test`. Postfix consegna il messaggio alla casella locale sul VPS. Elenca i messaggi via SSH e sostituisci `PERCORSO_MESSAGGIO` con il percorso restituito.

```sh
/opt/picus-demo/compose exec -T picus-smtp find /var/lib/picus-capture/Maildir/new -type f
/opt/picus-demo/compose exec -T picus-smtp cat PERCORSO_MESSAGGIO | python3 -c 'from email import policy; from email.parser import BytesParser; import sys; print(BytesParser(policy=policy.default).parse(sys.stdin.buffer).get_body(preferencelist=("plain",)).get_content())'
/opt/picus-demo/compose logs --tail=50 picus-smtp
/opt/picus-demo/compose exec -T picus-smtp postqueue -p
```

Apri il link di attivazione in un browser con un invito alla demo già riscattato. Il link determina l'attivazione dell'account PICUS e il relativo accesso alla demo continua a essere gestito dalla pagina di invito.

### Attivare il recapito esterno

Postfix usa `noreply@mail.picus.strawberrypenguin.it`. Nella zona Cloudflare `strawberrypenguin.it` aggiungi questi record. I nomi nella tabella sono relativi alla zona.

| Tipo | Nome | Valore |
| --- | --- | --- |
| A | `mail.picus` | IPv4 pubblico del VPS, **DNS only** |
| TXT | `mail.picus` | `v=spf1 ip4:IPV4_DEL_VPS -all` |
| TXT | `demo2026._domainkey.mail.picus` | Riga pubblica restituita dal comando DKIM qui sotto |
| TXT | `_dmarc.mail.picus` | `v=DMARC1; p=none` |

```sh
cat /opt/picus-demo/mail/dkim-public.txt
```

Nel pannello del provider VPS imposta anche il PTR dell'IPv4 a `mail.picus.strawberrypenguin.it`, dopo aver pubblicato il record A. Per questo servizio di solo invio bastano i record elencati. Dopo la propagazione, inserisci in `/opt/picus-demo/mail/destinatari.txt` un indirizzo esterno di collaudo autorizzato per riga e riavvia il solo SMTP. [Record e-mail Cloudflare](https://developers.cloudflare.com/dns/manage-dns-records/how-to/email-records/) · [Reverse DNS su Hostinger](https://www.hostinger.com/support/4805528-how-to-setup-reverse-dns-on-vps/)

```sh
vi /opt/picus-demo/mail/destinatari.txt
/opt/picus-demo/compose restart picus-smtp
```

Dopo il riavvio, registra l'account di prova o reinvia il suo invito dal Banco. Controlla la ricezione e gli esiti di SPF, DKIM e DMARC nelle intestazioni del messaggio. La chiave privata, l'elenco reale e le credenziali restano nei file del VPS. Postfix ascolta sulla rete Docker dedicata e accetta solo i destinatari di collaudo configurati.

### File di deploy versionati

La directory [scripts/demo/vps](../../scripts/demo/vps/) contiene Caddy, l'overlay Compose, il firewall OpenRC e la build SMTP. `runtime.env`, i dump, i log, l'archivio inviti e la chiave DKIM privata sono stato locale del VPS. Conserva le copie operative con permessi riservati.

L'overlay `compose.vps.yml` e il wrapper `compose` si installano in `/opt/picus-demo/`. I file di `scripts/demo/invites/` usati dal servizio si installano in `/usr/local/lib/picus-demo-invites/`. `Caddyfile` va in `/etc/caddy/`. Le unità OpenRC si installano in `/etc/init.d/` con i nomi `picus-demo-firewall` e `picus-demo-invites`. Caddy importa le regole del gateway d'invito. Per inizializzare Postfix su un VPS nuovo, esegui dalla radice del checkout sul server:

```sh
umask 077
install -d -m 0700 /opt/picus-demo/mail
test -s /opt/picus-demo/mail/dkim-private.pem || openssl genpkey \
  -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /opt/picus-demo/mail/dkim-private.pem
test -f /opt/picus-demo/mail/destinatari.txt || touch /opt/picus-demo/mail/destinatari.txt
chiave=$(openssl pkey -in /opt/picus-demo/mail/dkim-private.pem -pubout -outform DER | openssl base64 -A)
printf 'v=DKIM1; k=rsa; p=%s\n' "$chiave" > /opt/picus-demo/mail/dkim-public.txt
docker build -t picus-demo/smtp:v1 scripts/demo/vps/mail
/opt/picus-demo/compose up -d --no-deps --no-build --pull never picus-smtp
```

### Backup PostgreSQL

I dump si trovano in `/opt/picus-demo/backups/`. Per una nuova copia del database prima di un aggiornamento, esegui sul VPS:

```sh
umask 077
database=$(sed -n 's/^PICUS_VPS_DB_NAME=//p' /opt/picus-demo/runtime.env)
database=${database:-picus}
/opt/picus-demo/compose exec -T picus-db pg_dump -U picus -Fc "$database" \
  > "/opt/picus-demo/backups/picus-$(date -u +%Y%m%dT%H%M%SZ).dump"
```

Conserva il riferimento dell'immagine e la configurazione del rilascio precedente insieme al dump. Verifica il dump ripristinandolo in un database separato prima di usarlo per recuperare la demo. Se una migrazione rende lo schema incompatibile, il rollback richiede anche il ripristino del database.
