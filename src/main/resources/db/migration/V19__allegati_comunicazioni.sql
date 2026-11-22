-- #141: allegati delle comunicazioni, salvati nel database insieme ai metadati come prevede l'ADR-001.
create table comunicazioni_allegati (
    id uuid primary key,
    comunicazione_id uuid not null references comunicazioni (id),
    nome_file varchar(200) not null,
    tipo_mime varchar(50) not null
        check (tipo_mime in ('application/pdf', 'image/png', 'image/jpeg', 'text/plain')),
    dimensione_byte bigint not null check (dimensione_byte > 0),
    contenuto bytea not null
);

create index comunicazioni_allegati_comunicazione_idx on comunicazioni_allegati (comunicazione_id);
