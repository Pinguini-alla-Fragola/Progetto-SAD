create table monitoraggio_versioni_categorie (
    id uuid primary key,
    tracciato varchar(32) not null check (tracciato in ('TM-OP-01', 'TM-OPT-01')),
    codice_versione varchar(64) not null,
    stato varchar(16) not null check (stato in ('BOZZA', 'PUBBLICATA', 'LEGACY')),
    descrizione varchar(255) not null check (length(trim(descrizione)) > 0),
    creata_il timestamp with time zone not null,
    pubblicata_il timestamp with time zone,
    versione_record bigint not null default 0,
    constraint chk_mon_ver_cat_codice_versione check (
        length(codice_versione) between 1 and 64
        and regexp_like(codice_versione, '^[A-Za-z0-9]')
        and not regexp_like(codice_versione, '[^A-Za-z0-9_.-]')
    ),
    constraint uq_mon_ver_cat_id_tracciato unique (id, tracciato),
    constraint uq_mon_ver_cat_tracciato_codice unique (tracciato, codice_versione),
    constraint ck_mon_ver_cat_pubblicata_il check (
        (stato = 'BOZZA' and pubblicata_il is null)
        or (stato in ('PUBBLICATA', 'LEGACY') and pubblicata_il is not null)
    )
);

create table monitoraggio_voci_versione_categorie (
    versione_id uuid not null references monitoraggio_versioni_categorie (id) on delete cascade,
    codice varchar(64) not null,
    descrizione varchar(255) not null check (length(trim(descrizione)) > 0),
    definizione varchar(1000) not null check (length(trim(definizione)) > 0),
    ordine integer not null default 0 check (ordine >= 0),
    primary key (versione_id, codice),
    constraint chk_mon_voce_cat_codice check (
        length(codice) between 1 and 64
        and regexp_like(codice, '^[A-Za-z0-9]')
        and not regexp_like(codice, '[^A-Za-z0-9_.-]')
    )
);

create table monitoraggio_configurazioni_trimestre (
    tracciato varchar(32) not null check (tracciato in ('TM-OP-01', 'TM-OPT-01')),
    anno integer not null check (anno > 0),
    trimestre integer not null check (trimestre between 1 and 4),
    versione_id uuid not null,
    assegnata_il timestamp with time zone not null,
    primary key (tracciato, anno, trimestre),
    constraint fk_mon_conf_trim_versione foreign key (versione_id, tracciato)
        references monitoraggio_versioni_categorie (id, tracciato)
);

create index monitoraggio_conf_trim_versione_idx
    on monitoraggio_configurazioni_trimestre (versione_id);

-- Versione esplicita riservata ai soli trimestri gia' rendicontati con v0.1 prima della migrazione (#181, #41).
insert into monitoraggio_versioni_categorie (
    id, tracciato, codice_versione, stato, descrizione, creata_il, pubblicata_il, versione_record
) values (
    cast('00000000-0000-0000-0000-000000000001' as uuid),
    'TM-OP-01',
    'v0.1',
    'LEGACY',
    'Tracciato provvisorio v0.1',
    current_timestamp,
    current_timestamp,
    0
);

alter table monitoraggio_schede_operatore
    add column versione_categorie_id uuid references monitoraggio_versioni_categorie (id);

insert into monitoraggio_configurazioni_trimestre (tracciato, anno, trimestre, versione_id, assegnata_il)
select 'TM-OP-01', s.anno, s.trimestre,
       cast('00000000-0000-0000-0000-000000000001' as uuid),
       min(s.inviata_il)
from monitoraggio_schede_operatore s
where s.versione_tracciato = '0.1'
group by s.anno, s.trimestre;

update monitoraggio_schede_operatore
set versione_categorie_id = cast('00000000-0000-0000-0000-000000000001' as uuid)
where versione_tracciato = '0.1'
  and versione_categorie_id is null;
