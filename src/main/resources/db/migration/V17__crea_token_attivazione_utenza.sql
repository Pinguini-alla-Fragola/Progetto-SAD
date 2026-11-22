-- 1. Tabella per i token monouso di attivazione utenza (Task #147)
create table utenze_token_attivazione (
                                          id uuid primary key,
                                          account_id uuid not null references utenze_account(id) on delete cascade,
                                          token_hash varchar(128) not null unique,
                                          scadenza timestamp with time zone not null,
                                          creato_il timestamp with time zone not null,
                                          usato_il timestamp with time zone
);

create index idx_utenze_token_attivazione_account
    on utenze_token_attivazione (account_id);

-- 2. Aggiungi codice_fiscale all'anagrafica OPT (User Story #44)
-- Resta nullable su DB per preservare l'assenza del dato nelle OPT pregresse
-- e mantenere compatibili le fixture storiche; l'obbligatorietà per le nuove
-- registrazioni è garantita a livello applicativo in RegistrazioneOptService.
alter table utenze_profili_opt
    add column codice_fiscale varchar(16);

-- 3. Consenti account in attesa di attivazione senza password iniziale
alter table utenze_account
    alter column password_hash drop not null;