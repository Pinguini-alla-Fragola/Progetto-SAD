alter table richieste
    drop constraint richieste_stato_check,
    add constraint richieste_stato_check
        check (stato in ('CREATA', 'IN_PROGRAMMAZIONE', 'CONFERMATA', 'RIFIUTATA', 'ANNULLATA')),
    alter column stato set default 'CREATA';
