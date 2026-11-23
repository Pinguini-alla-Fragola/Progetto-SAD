alter table utenze_token_attivazione add column ordine_emissione bigint;

-- I timestamp storici possono coincidere. L'ID rende deterministico l'ordine dei soli token pregressi.
merge into utenze_token_attivazione as token
using (
    select id, row_number() over (partition by account_id order by creato_il, id) as ordine
    from utenze_token_attivazione
) as pregressi
on token.id = pregressi.id
when matched then update set ordine_emissione = pregressi.ordine;

alter table utenze_token_attivazione alter column ordine_emissione set not null;
alter table utenze_token_attivazione
    add constraint utenze_token_attivazione_ordine_uk unique (account_id, ordine_emissione);
