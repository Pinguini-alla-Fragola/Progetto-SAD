create table monitoraggio_categorie_schede (
    codice varchar(64) primary key,
    descrizione varchar(255) not null,
    attiva boolean not null default true,
    constraint chk_categoria_scheda_codice check (length(trim(codice)) between 1 and 64)
);
