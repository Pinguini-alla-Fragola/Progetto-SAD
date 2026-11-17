-- L'accordo completo accompagna la conferma e si conserva in caso di annullamento.
-- Flyway valida anche lo storico. Riconciliare le righe incoerenti alla fonte prima
-- di ripetere la migrazione, preservando l'esito e i dati concordati.
alter table richieste
    add constraint richieste_stato_accordo_ck check (
        (accordo_modalita is null and stato <> 'CONFERMATA')
        or (accordo_modalita is not null and stato in ('CONFERMATA', 'ANNULLATA') and erogatore_id is not null)
    );

alter table richieste
    add constraint richieste_dettagli_accordo_ck check (
        case accordo_modalita
            when 'IN_PRESENZA' then
                erogatore_ambito is distinct from 'CENTRO_PICUS_A_DISTANZA'
                and accordo_data_ora is not null
                and nullif(trim(accordo_luogo), '') is not null
                and nullif(trim(accordo_nome_referente), '') is not null
                and nullif(trim(accordo_cognome_referente), '') is not null
                and nullif(trim(accordo_telefono_referente), '') is not null
                and accordo_link is null
            when 'A_DISTANZA' then
                erogatore_ambito is not distinct from 'CENTRO_PICUS_A_DISTANZA'
                and accordo_data_ora is not null
                and nullif(trim(accordo_link), '') is not null
                and accordo_luogo is null
                and accordo_nome_referente is null
                and accordo_cognome_referente is null
                and accordo_telefono_referente is null
            else
                accordo_modalita is null
                and accordo_data_ora is null
                and accordo_luogo is null
                and accordo_nome_referente is null
                and accordo_cognome_referente is null
                and accordo_telefono_referente is null
                and accordo_link is null
        end
    );
