create table richieste_contatti_disponibilita (
    id uuid primary key,
    richiesta_id uuid not null references richieste (id),
    erogatore_id uuid not null references utenze_account (id),
    ambito varchar(30) not null
        check (ambito in ('STESSO_COMUNE', 'COMUNE_LIMITROFO', 'CENTRO_PICUS_A_DISTANZA')),
    nome_misura varchar(255) not null,
    inviato_il timestamp with time zone not null,
    esito varchar(20) not null default 'IN_ATTESA'
        check (esito in ('IN_ATTESA', 'DISPONIBILE', 'NON_DISPONIBILE'))
);

create index ix_contatti_richiesta_invio
    on richieste_contatti_disponibilita (richiesta_id, inviato_il, id);
