-- Il preflight applicativo richiede la riconciliazione esplicita di eventuali
-- avvisi preesistenti incoerenti. Nessuna riga storica viene eliminata o riscritta.
alter table richieste_contatti_disponibilita
    add constraint contatti_riferimenti_avviso_uk unique (id, richiesta_id, erogatore_id);

alter table comunicazioni_avvisi_operativi
    add constraint avvisi_contatto_tipo_uk unique (contatto_id, tipo);

alter table comunicazioni_avvisi_operativi
    add constraint avvisi_destinatario_contatto_fk
    foreign key (contatto_id, richiesta_id, destinatario_id)
    references richieste_contatti_disponibilita (id, richiesta_id, erogatore_id);
