create table utenze_inviti_importazione_opt (
    account_id uuid primary key references utenze_account(id) on delete cascade,
    disponibile_dal timestamp with time zone not null default current_timestamp,
    token_hash varchar(128)
);

create index idx_utenze_inviti_importazione_opt_disponibilita
    on utenze_inviti_importazione_opt (disponibile_dal, account_id);
