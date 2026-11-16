create table catalogo_offerte_opt (
    opt_id uuid not null references utenze_profili_opt (account_id),
    misura_id bigint not null references catalogo_misure (id),
    tramite_professionista boolean not null default false,
    attiva boolean not null default true,
    primary key (opt_id, misura_id)
);

alter table utenze_profili_operatore add column centro_picus boolean not null default false;

create table utenze_operatori_misure (
    account_id uuid not null references utenze_profili_operatore (account_id),
    misura_id bigint not null references catalogo_misure (id),
    primary key (account_id, misura_id)
);

create index catalogo_offerte_misura_idx on catalogo_offerte_opt (misura_id, attiva);
create index utenze_operatori_misura_idx on utenze_operatori_misure (misura_id);
