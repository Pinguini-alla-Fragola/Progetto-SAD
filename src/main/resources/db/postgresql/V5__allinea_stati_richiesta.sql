alter table richieste
    drop constraint richieste_stato_check,
    add constraint richieste_stato_check
        check (stato in (
            'CREATA',
            'PRESA_IN_CARICO',
            'IN_PROGRAMMAZIONE',
            'CONFERMATA',
            'RIFIUTATA',
            'ANNULLATA'
        ));
