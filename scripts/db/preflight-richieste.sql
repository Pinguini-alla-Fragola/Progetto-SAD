-- Sola lettura. Eseguire su uno snapshot prima dell'upgrade, poi a scritture sospese.
-- Pratiche con più contatti ancora aperti (incompatibili con V11).
select c.richiesta_id, c.id as contatto_id, c.erogatore_id, c.inviato_il, c.esito
from richieste_contatti_disponibilita c
where c.esito = 'IN_ATTESA' and c.richiesta_id in (
    select richiesta_id from richieste_contatti_disponibilita
    where esito = 'IN_ATTESA' group by richiesta_id having count(*) > 1
)
order by c.richiesta_id, c.inviato_il, c.id;

-- Riferimenti incoerenti o avvisi orfani (incompatibili con V13).
select a.id as avviso_id, a.contatto_id, a.richiesta_id, a.destinatario_id,
       c.richiesta_id as richiesta_attesa, c.erogatore_id as destinatario_atteso
from comunicazioni_avvisi_operativi a
left join richieste_contatti_disponibilita c on c.id = a.contatto_id
where c.id is null or c.richiesta_id <> a.richiesta_id or c.erogatore_id <> a.destinatario_id;

-- Duplicati per contatto e tipo (incompatibili con V13).
select contatto_id, tipo, count(*) as avvisi
from comunicazioni_avvisi_operativi group by contatto_id, tipo having count(*) > 1;
