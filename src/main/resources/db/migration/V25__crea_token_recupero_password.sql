-- Link monouso per il recupero autonomo della password (US #33, task #160).
-- Come quelli di attivazione (V17) si salva solo l'impronta SHA-256 del token, mai il token in chiaro.
-- Tabella separata: il recupero riguarda solo account attivi, dura 15 minuti e ha un ciclo di vita proprio.
create table utenze_token_recupero_password (
    id uuid primary key,
    account_id uuid not null references utenze_account(id) on delete cascade,
    token_hash varchar(128) not null unique,
    scadenza timestamp with time zone not null,
    creato_il timestamp with time zone not null,
    usato_il timestamp with time zone
);

create index idx_utenze_token_recupero_account
    on utenze_token_recupero_password (account_id);
