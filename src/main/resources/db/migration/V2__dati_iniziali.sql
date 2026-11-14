insert into catalogo_misure (codice, nome, descrizione) values
    ('M01', 'Accoglienza e ascolto', 'Primo contatto e valutazione del bisogno.'),
    ('M02', 'Informazione consulenza e orientamento', 'Indicazioni sui servizi del territorio e sulle procedure.'),
    ('M03', 'Accompagnamento ai servizi', 'Supporto per accedere ai servizi locali.'),
    ('M04', 'Sostegno psicologico', 'Supporto psicologico, anche a distanza se appropriato.'),
    ('M05', 'Educativa alimentare', 'Educazione a comportamenti alimentari consapevoli.'),
    ('M06', 'Gestione del bilancio familiare', 'Aiuto nel pianificare e gestire le spese familiari.'),
    ('M07', 'Sostegno scolastico', 'Aiuto nello studio per bambini e ragazzi.'),
    ('M08', 'Sostegno educativo agli adulti', 'Attività educative per adulti.'),
    ('M09', 'Orientamento alla ricerca di lavoro', 'Aiuto con curriculum, candidature e colloqui.'),
    ('M10', 'Prima assistenza medica', 'Assistenza qualificata, farmaci e servizi ambulatoriali.'),
    ('M11', 'Tutela legale', 'Consulenza sui diritti di cittadinanza sociale.'),
    ('M12', 'Altro', 'Ulteriori misure da specificare.');

insert into catalogo_comuni (codice, nome) values
    ('napoli', 'Napoli'),
    ('portici', 'Portici'),
    ('pozzuoli', 'Pozzuoli');

insert into catalogo_adiacenze (comune_a, comune_b) values
    ('napoli', 'portici'),
    ('napoli', 'pozzuoli');
