create table monitoraggio_schede_operatore (
    id uuid primary key,
    operatore_id uuid not null references utenze_profili_operatore (account_id),
    anno integer not null check (anno > 0),
    trimestre integer not null check (trimestre between 1 and 4),
    inviata_il timestamp with time zone not null,
    versione_tracciato varchar(16) not null,
    nome varchar(255),
    cognome varchar(255),
    luogo_nascita varchar(255),
    data_nascita date,
    codice_fiscale varchar(255),
    qualifica varchar(255),
    tipologia_contratto varchar(255),
    area_livello varchar(255),
    ore numeric(19, 6) not null check (ore >= 0),
    scala_ore integer not null check (scala_ore between -12 and 6),
    costo_orario_lordo numeric(19, 6) not null check (costo_orario_lordo >= 0),
    scala_costo_orario_lordo integer not null check (scala_costo_orario_lordo between -12 and 6),
    constraint uq_scheda_operatore_periodo unique (operatore_id, anno, trimestre)
);

create index monitoraggio_schede_periodo_idx
    on monitoraggio_schede_operatore (anno, trimestre, inviata_il, id);

create table monitoraggio_conteggi_scheda (
    scheda_id uuid not null references monitoraggio_schede_operatore (id),
    categoria varchar(64) not null,
    valore bigint not null check (valore >= 0),
    primary key (scheda_id, categoria)
);
