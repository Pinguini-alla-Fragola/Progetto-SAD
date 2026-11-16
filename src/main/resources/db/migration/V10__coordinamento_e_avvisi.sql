alter table richieste add column erogatore_ambito varchar(30)
    check (erogatore_ambito in ('STESSO_COMUNE', 'COMUNE_LIMITROFO', 'CENTRO_PICUS_A_DISTANZA'));

create table comunicazioni_avvisi_operativi (
    id uuid primary key,
    richiesta_id uuid not null,
    contatto_id uuid not null,
    destinatario_id uuid not null references utenze_account (id),
    tipo varchar(40) not null check (tipo in ('RICHIESTA_DISPONIBILITA')),
    registrato_il timestamp with time zone not null,
    unique (contatto_id, destinatario_id, tipo)
);

create index avvisi_destinatario_idx on comunicazioni_avvisi_operativi (destinatario_id, registrato_il, id);
create index richieste_carico_idx on richieste (erogatore_id, stato, accordo_data_ora);
