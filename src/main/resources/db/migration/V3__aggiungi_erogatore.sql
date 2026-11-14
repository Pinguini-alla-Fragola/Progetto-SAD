alter table richieste
    add column erogatore_id uuid references utenze_account (id);
