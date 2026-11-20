create table comunicazioni_messaggi (
    id uuid primary key,
    titolo text not null,
    testo text not null,
    inviata_il timestamp with time zone not null
);

create table comunicazioni_destinatari (
    comunicazione_id uuid not null references comunicazioni_messaggi (id),
    destinatario_id uuid not null references utenze_account (id),
    confermata_il timestamp with time zone,
    primary key (comunicazione_id, destinatario_id)
);
