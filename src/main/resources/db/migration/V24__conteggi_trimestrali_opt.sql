create table monitoraggio_categorie (
                                        codice varchar(64) primary key,
                                        descrizione varchar(255) not null,
                                        attiva boolean not null default true
);

create table monitoraggio_configurazione (
                                             id smallint primary key check (id = 1),
                                             correzioni_abilitate boolean not null default false,
                                             scadenza_correzioni timestamp with time zone
);

insert into monitoraggio_configurazione (id, correzioni_abilitate, scadenza_correzioni)
values (1, false, null);

-- Voci iniziali del tracciato TM-OPT-01 (estendibili dal Banco)
insert into monitoraggio_categorie (codice, descrizione, attiva) values
                                                                     ('PERSONE_RAGGIUNTE', 'Persone raggiunte nel trimestre', true),
                                                                     ('MINORI', 'Minori raggiunti', true),
                                                                     ('ANZIANI', 'Anziani raggiunti', true),
                                                                     ('NUCLEI_FAMILIARI', 'Nuclei familiari raggiunti', true);

create table monitoraggio_conteggi_opt (
                                           id uuid primary key,
                                           opt_id uuid not null references utenze_profili_opt (account_id),
                                           anno integer not null check (anno >= 1),
                                           trimestre integer not null check (trimestre between 1 and 4),
                                           misura_id bigint not null references catalogo_misure (id),
                                           voce varchar(64) not null references monitoraggio_categorie (codice),
                                           valore bigint not null check (valore >= 0),
                                           aggiornato_il timestamp with time zone not null,
                                           unique (opt_id, anno, trimestre, misura_id, voce)
);

create index idx_monitoraggio_conteggi_opt_periodo
    on monitoraggio_conteggi_opt (opt_id, anno, trimestre);