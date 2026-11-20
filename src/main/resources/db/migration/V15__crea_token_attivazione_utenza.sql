-- 1. Tabella per i token monouso di attivazione utenza (Task #147)
create table utenze_token_attivazione (
                                          id uuid primary key,
                                          account_id uuid not null references utenze_account(id) on delete cascade,
                                          token_hash varchar(128) not null unique,
                                          scadenza timestamptz not null,
                                          creato_il timestamptz not null,
                                          usato_il timestamptz
);

create index idx_utenze_token_attivazione_account
    on utenze_token_attivazione (account_id);

-- 2. Aggiungi codice_fiscale all'anagrafica OPT (User Story #44)
alter table utenze_profili_opt
    add column codice_fiscale varchar(16);

update utenze_profili_opt
set codice_fiscale = '00000000000'
where codice_fiscale is null;

alter table utenze_profili_opt
    alter column codice_fiscale set not null;
alter table utenze_account
    alter column password_hash drop not null;