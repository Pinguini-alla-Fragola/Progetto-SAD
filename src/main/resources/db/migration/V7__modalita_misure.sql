-- Il coordinamento dei contatti usa la migrazione V6.
alter table catalogo_misure
    add column erogabile_a_distanza boolean not null default false;
