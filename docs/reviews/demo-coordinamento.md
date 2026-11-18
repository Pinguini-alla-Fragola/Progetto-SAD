# Demo del coordinamento delle richieste

Usa questi passaggi per mostrare il percorso dall’invio della richiesta alla
consultazione dell’accordo. Esegui la preparazione soltanto su un database
locale con dati sintetici.

## Avviare l’ambiente di demo

1. Dalla radice del repository, configura `.env` seguendo
   [Avvio locale](../../README.md#avvio-locale).
2. Avvia Docker Compose con un progetto dedicato, che usa un volume separato.
   Se un’altra istanza di PICUS occupa la porta 8080, arrestala prima.

   ```console
   docker compose -p picus-demo-coordinamento up --build -d
   ```

3. Quando la pagina di accesso è disponibile su <http://localhost:8080>, apri
   il client PostgreSQL del progetto demo.

   ```console
   docker compose -p picus-demo-coordinamento exec picus-db psql -X -U picus -d picus -v ON_ERROR_STOP=1
   ```

## Preparare offerte e competenze

Incolla l’intero blocco nel client `psql`. La transazione verifica la presenza
degli account e dei profili demo prima di aggiornare i dati. Puoi rieseguirla
sullo stesso database demo. Attendi `COMMIT`, quindi esci con `\q`.

```sql
begin;

do $$
begin
    if (select count(*) from utenze_account where stato = 'ATTIVO' and
        ((email = 'banco@picus.test' and ruolo = 'BANCO') or
         (email in ('opt-a@picus.test', 'opt-b@picus.test') and ruolo = 'OPT') or
         (email = 'operatore@picus.test' and ruolo = 'OPERATORE'))) <> 4
        or not exists (select 1 from utenze_profili_opt p join utenze_account a on a.id = p.account_id
                       where a.email = 'opt-b@picus.test')
        or not exists (select 1 from utenze_profili_operatore p join utenze_account a on a.id = p.account_id
                       where a.email = 'operatore@picus.test') then
        raise exception 'Fixture disponibile solo dopo l avvio locale del profilo demo';
    end if;
end $$;

insert into catalogo_offerte_opt (opt_id, misura_id, tramite_professionista, attiva)
select a.id, m.id, false, true from utenze_account a cross join catalogo_misure m
where a.email = 'opt-b@picus.test' and a.ruolo = 'OPT' and m.codice = 'M01'
on conflict (opt_id, misura_id) do update set attiva = true, tramite_professionista = false;

insert into utenze_operatori_misure (account_id, misura_id)
select a.id, m.id from utenze_account a cross join catalogo_misure m
where a.email = 'operatore@picus.test' and a.ruolo = 'OPERATORE' and m.codice in ('M01', 'M04')
on conflict (account_id, misura_id) do nothing;

update utenze_profili_operatore set centro_picus = true
where account_id in (select id from utenze_account where email = 'operatore@picus.test' and ruolo = 'OPERATORE');
update catalogo_misure set erogabile_a_distanza = true where codice = 'M04';

commit;
```

Il blocco abilita la OPT B per la misura `M01` e l’operatore del Centro PICUS
per `M01` e `M04`. Abilita anche l’erogazione a distanza per `M04`.

## Riprodurre il percorso

Apri tre profili del browser o sessioni separate. Usa la password impostata
in `PICUS_DEMO_PASSWORD` per accedere con gli account sintetici.

1. **OPT A** (`opt-a@picus.test`). Invia una richiesta per `M01` a Napoli
   usando dati sintetici. Annota l’ID e verifica lo stato **Creata**.
2. **Banco** (`banco@picus.test`). Prendi in carico la richiesta. Tra i
   candidati compaiono l’operatore di Napoli e la OPT B di Pozzuoli. Invia
   il contatto di disponibilità all’operatore.
3. **Operatore** (`operatore@picus.test`). Apri il contatto ricevuto e rispondi
   positivamente. La richiesta resta **Presa in carico** fino all’assegnazione.
4. **Banco**. Assegna la richiesta all’operatore. Quando passa **In programmazione**,
   registra un accordo in presenza con dati di esempio.
5. **OPT A**. Riapri lo stesso ID e verifica lo stato **Confermata** e i dettagli
   dell’accordo.

Per provare la variante a distanza, crea una richiesta per `M04` a Portici.
Il Banco vede due opzioni per l’operatore, una nel comune limitrofo e una a
distanza. Dopo il diniego dell’opzione locale, può contattare l’operatore per
quella a distanza e registrare l’accordo con data, ora e link.
