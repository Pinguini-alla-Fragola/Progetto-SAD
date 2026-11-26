-- Dati sintetici per il progetto Compose picus-demo-coordinamento.
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
        raise exception 'Fixture disponibile solo con gli account e i profili demo attivi';
    end if;

    if (select count(*) from catalogo_misure where codice in ('M01', 'M04') and attiva) <> 2 then
        raise exception 'La demo richiede le misure M01 e M04 attive';
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
