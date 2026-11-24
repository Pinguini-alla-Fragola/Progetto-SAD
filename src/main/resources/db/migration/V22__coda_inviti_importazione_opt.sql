-- I link dei singoli tentativi restano validi anche se l'esito SMTP non viene registrato.
alter table utenze_token_attivazione
    add column da_importazione_opt boolean not null default false;

create table utenze_inviti_importazione_opt (
    account_id uuid primary key references utenze_account(id) on delete cascade,
    disponibile_dal timestamp with time zone default current_timestamp,
    tentativi integer not null default 0 check (tentativi >= 0)
);

create index idx_utenze_inviti_importazione_opt_disponibilita
    on utenze_inviti_importazione_opt (disponibile_dal, account_id);
