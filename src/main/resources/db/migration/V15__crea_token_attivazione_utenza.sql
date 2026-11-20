-- ============================================================================
-- Migrazione per User Story #44 (Creazione manuale OPT e attivazione utenze)
-- ============================================================================

-- 1. Consenti password_hash NULL quando l'account è in stato IN_ATTESA_DI_ATTIVAZIONE
alter table utenze_account
    alter column password_hash drop not null;

-- Aggiungi un controllo di coerenza: se l'account è ATTIVO deve avere una password,
-- se è IN_ATTESA_DI_ATTIVAZIONE non deve ancora averla
alter table utenze_account
    add constraint chk_utenze_account_password_stato
        check (
            (stato = 'IN_ATTESA_DI_ATTIVAZIONE' and password_hash is null)
                or (stato <> 'IN_ATTESA_DI_ATTIVAZIONE' and password_hash is not null)
            );

-- Vincolo di unicità case-insensitive sull'email per prevenire duplicati tipo 'Test@...' e 'test@...'
create unique index uq_utenze_account_email_lower
    on utenze_account (lower(email));


-- 2. Aggiungi codice_fiscale all'anagrafica OPT (richiesto dalla User Story #44)
alter table utenze_profili_opt
    add column codice_fiscale varchar(16) not null;

-- Vincolo di unicità case-insensitive sul codice struttura
create unique index uq_utenze_profili_opt_codice_struttura_lower
    on utenze_profili_opt (lower(codice_struttura));


-- 3. Tabella per i token di attivazione monouso (Task #147)
create table utenze_token_attivazione (
                                          id uuid primary key,
                                          account_id uuid not null references utenze_account (id) on delete cascade,
                                          token_hash varchar(128) not null unique,
                                          scadenza timestamp with time zone not null,
                                          creato_il timestamp with time zone not null,
                                          usato_il timestamp with time zone
);

create index idx_utenze_token_attivazione_account_id
    on utenze_token_attivazione (account_id);