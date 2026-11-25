create table monitoraggio_categorie_schede (
    codice varchar(64) primary key,
    descrizione varchar(255) not null,
    attiva boolean not null default true,
    constraint chk_categoria_scheda_codice check (
        regexp_like(codice, '^[A-Za-z0-9]')
        and not regexp_like(codice, '[^A-Za-z0-9_.-]')
    )
);
