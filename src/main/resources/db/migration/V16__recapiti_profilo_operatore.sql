-- #152: telefono e codice fiscale dell'Operatore creato dal Banco.
-- Le colonne restano facoltative per i profili già presenti; il codice fiscale, quando c'è, è unico.
alter table utenze_profili_operatore add column telefono varchar(30);
alter table utenze_profili_operatore add column codice_fiscale varchar(16);
alter table utenze_profili_operatore add constraint utenze_operatori_codice_fiscale_unico unique (codice_fiscale);
