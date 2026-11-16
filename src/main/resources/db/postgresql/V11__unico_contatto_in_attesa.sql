create unique index richieste_unico_contatto_in_attesa
    on richieste_contatti_disponibilita (richiesta_id)
    where esito = 'IN_ATTESA';
