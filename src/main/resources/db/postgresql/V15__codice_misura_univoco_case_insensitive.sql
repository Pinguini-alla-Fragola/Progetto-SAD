-- Allinea il vincolo di unicità del database al controllo applicativo case-insensitive
-- sul codice della misura (lower(codice)).
create unique index catalogo_misure_codice_ci_idx
    on catalogo_misure (lower(codice));
