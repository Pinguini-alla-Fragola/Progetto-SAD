-- Gli avvisi di annullamento appartengono alla richiesta e non a un contatto.
alter table comunicazioni_avvisi_operativi
    alter column contatto_id drop not null,
    drop constraint comunicazioni_avvisi_operativi_tipo_check,
    add constraint avvisi_tipo_contatto_check check (
        (tipo = 'RICHIESTA_DISPONIBILITA' and contatto_id is not null)
        or (tipo = 'RICHIESTA_ANNULLATA' and contatto_id is null)
    ),
    add constraint avvisi_richiesta_fk foreign key (richiesta_id) references richieste (id);

create unique index avvisi_annullamento_destinatario_uk
    on comunicazioni_avvisi_operativi (richiesta_id, destinatario_id)
    where tipo = 'RICHIESTA_ANNULLATA';
