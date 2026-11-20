-- #140: comunicazioni del Banco e account destinatari registrati al momento dell'invio.
-- confermata_il resta vuota finché il destinatario non preme «Confermo la ricezione» (#143).
create table comunicazioni (
    id uuid primary key,
    mittente_id uuid not null references utenze_account (id),
    titolo varchar(150),
    testo text not null,
    tipo_destinatari varchar(30) not null
        check (tipo_destinatari in ('TUTTE_LE_OPT', 'GRUPPO_OPT', 'TUTTI_GLI_OPERATORI', 'GRUPPO_OPERATORI')),
    inviata_il timestamp with time zone not null
);

create table comunicazioni_destinatari (
    comunicazione_id uuid not null references comunicazioni (id),
    destinatario_id uuid not null references utenze_account (id),
    confermata_il timestamp with time zone,
    primary key (comunicazione_id, destinatario_id)
);

create index comunicazioni_destinatari_account_idx on comunicazioni_destinatari (destinatario_id, confermata_il);
