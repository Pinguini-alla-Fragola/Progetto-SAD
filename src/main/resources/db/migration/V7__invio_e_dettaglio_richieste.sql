alter table richieste add column riferimento_invio uuid;
alter table richieste add column accordo_modalita varchar(20);
alter table richieste add column accordo_data_ora timestamp with time zone;
alter table richieste add column accordo_luogo varchar(500);
alter table richieste add column accordo_link varchar(2048);
alter table richieste add column accordo_nome_referente varchar(255);
alter table richieste add column accordo_cognome_referente varchar(255);
alter table richieste add column accordo_telefono_referente varchar(50);
alter table richieste add constraint richieste_invio_unico unique (titolare_id, riferimento_invio);

create index richieste_titolare_invio_idx on richieste (titolare_id, inviata_il, id);
create index richieste_coda_idx on richieste (stato, inviata_il, id);
