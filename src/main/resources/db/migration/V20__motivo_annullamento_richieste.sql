alter table richieste add column motivo_annullamento text;

alter table richieste add constraint richieste_motivo_annullamento_check
    check (stato = 'ANNULLATA' or motivo_annullamento is null);
