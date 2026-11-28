-- Catalogo completo dei comuni della Campania e relative adiacenze amministrative (#251).
-- Fonte: ISTAT 1° gennaio 2026 (Elenco comuni italiani e Confini amministrativi Limiti01012026_g, licenza CC BY 4.0).
-- Generato con: python scripts/genera_catalogo_campania.py

alter table catalogo_comuni add column codice_istat varchar(6);
alter table catalogo_comuni add column provincia varchar(100);
alter table catalogo_comuni add column sigla_provincia varchar(2);
alter table catalogo_comuni add column citta_metropolitana boolean not null default false;

alter table catalogo_comuni add constraint catalogo_comuni_codice_istat_uk unique (codice_istat);
alter table catalogo_comuni add constraint catalogo_comuni_codice_istat_chk
    check (codice_istat is null or (length(codice_istat) = 6 and regexp_like(codice_istat, '^[0-9]{6}$')));
alter table catalogo_comuni add constraint catalogo_comuni_sigla_provincia_chk
    check (sigla_provincia is null or (length(sigla_provincia) = 2 and regexp_like(sigla_provincia, '^[A-Z]{2}$')));

-- Collega i codici storici esistenti al codice ISTAT ufficiale senza duplicare i comuni.
update catalogo_comuni set nome = 'Napoli', codice_istat = '063049', provincia = 'Napoli', sigla_provincia = 'NA', citta_metropolitana = true where codice = 'napoli';
update catalogo_comuni set nome = 'Portici', codice_istat = '063059', provincia = 'Napoli', sigla_provincia = 'NA', citta_metropolitana = true where codice = 'portici';
update catalogo_comuni set nome = 'Pozzuoli', codice_istat = '063060', provincia = 'Napoli', sigla_provincia = 'NA', citta_metropolitana = true where codice = 'pozzuoli';

insert into catalogo_comuni (codice, nome, codice_istat, provincia, sigla_provincia, citta_metropolitana)
select v.codice, v.nome, v.codice_istat, v.provincia, v.sigla_provincia, v.citta_metropolitana
from (
    select cast('061001' as varchar(50)) as codice, cast('Ailano' as varchar(255)) as nome, cast('061001' as varchar(6)) as codice_istat, cast('Caserta' as varchar(100)) as provincia, cast('CE' as varchar(2)) as sigla_provincia, false as citta_metropolitana
    union all select '061002', 'Alife', '061002', 'Caserta', 'CE', false
    union all select '061003', 'Alvignano', '061003', 'Caserta', 'CE', false
    union all select '061004', 'Arienzo', '061004', 'Caserta', 'CE', false
    union all select '061005', 'Aversa', '061005', 'Caserta', 'CE', false
    union all select '061006', 'Baia e Latina', '061006', 'Caserta', 'CE', false
    union all select '061007', 'Bellona', '061007', 'Caserta', 'CE', false
    union all select '061008', 'Caianello', '061008', 'Caserta', 'CE', false
    union all select '061009', 'Caiazzo', '061009', 'Caserta', 'CE', false
    union all select '061010', 'Calvi Risorta', '061010', 'Caserta', 'CE', false
    union all select '061011', 'Camigliano', '061011', 'Caserta', 'CE', false
    union all select '061012', 'Cancello ed Arnone', '061012', 'Caserta', 'CE', false
    union all select '061013', 'Capodrise', '061013', 'Caserta', 'CE', false
    union all select '061014', 'Capriati a Volturno', '061014', 'Caserta', 'CE', false
    union all select '061015', 'Capua', '061015', 'Caserta', 'CE', false
    union all select '061016', 'Carinaro', '061016', 'Caserta', 'CE', false
    union all select '061017', 'Carinola', '061017', 'Caserta', 'CE', false
    union all select '061018', 'Casagiove', '061018', 'Caserta', 'CE', false
    union all select '061019', 'Casal di Principe', '061019', 'Caserta', 'CE', false
    union all select '061020', 'Casaluce', '061020', 'Caserta', 'CE', false
    union all select '061021', 'Casapulla', '061021', 'Caserta', 'CE', false
    union all select '061022', 'Caserta', '061022', 'Caserta', 'CE', false
    union all select '061023', 'Castel Campagnano', '061023', 'Caserta', 'CE', false
    union all select '061024', 'Castel di Sasso', '061024', 'Caserta', 'CE', false
    union all select '061025', 'Castello del Matese', '061025', 'Caserta', 'CE', false
    union all select '061026', 'Castel Morrone', '061026', 'Caserta', 'CE', false
    union all select '061027', 'Castel Volturno', '061027', 'Caserta', 'CE', false
    union all select '061028', 'Cervino', '061028', 'Caserta', 'CE', false
    union all select '061029', 'Cesa', '061029', 'Caserta', 'CE', false
    union all select '061030', 'Ciorlano', '061030', 'Caserta', 'CE', false
    union all select '061031', 'Conca della Campania', '061031', 'Caserta', 'CE', false
    union all select '061032', 'Curti', '061032', 'Caserta', 'CE', false
    union all select '061033', 'Dragoni', '061033', 'Caserta', 'CE', false
    union all select '061034', 'Fontegreca', '061034', 'Caserta', 'CE', false
    union all select '061035', 'Formicola', '061035', 'Caserta', 'CE', false
    union all select '061036', 'Francolise', '061036', 'Caserta', 'CE', false
    union all select '061037', 'Frignano', '061037', 'Caserta', 'CE', false
    union all select '061038', 'Gallo Matese', '061038', 'Caserta', 'CE', false
    union all select '061039', 'Galluccio', '061039', 'Caserta', 'CE', false
    union all select '061040', 'Giano Vetusto', '061040', 'Caserta', 'CE', false
    union all select '061041', 'Gioia Sannitica', '061041', 'Caserta', 'CE', false
    union all select '061042', 'Grazzanise', '061042', 'Caserta', 'CE', false
    union all select '061043', 'Gricignano di Aversa', '061043', 'Caserta', 'CE', false
    union all select '061044', 'Letino', '061044', 'Caserta', 'CE', false
    union all select '061045', 'Liberi', '061045', 'Caserta', 'CE', false
    union all select '061046', 'Lusciano', '061046', 'Caserta', 'CE', false
    union all select '061047', 'Macerata Campania', '061047', 'Caserta', 'CE', false
    union all select '061048', 'Maddaloni', '061048', 'Caserta', 'CE', false
    union all select '061049', 'Marcianise', '061049', 'Caserta', 'CE', false
    union all select '061050', 'Marzano Appio', '061050', 'Caserta', 'CE', false
) v
where not exists (select 1 from catalogo_comuni c where c.codice = v.codice or c.codice_istat = v.codice_istat);

insert into catalogo_comuni (codice, nome, codice_istat, provincia, sigla_provincia, citta_metropolitana)
select v.codice, v.nome, v.codice_istat, v.provincia, v.sigla_provincia, v.citta_metropolitana
from (
    select cast('061051' as varchar(50)) as codice, cast('Mignano Monte Lungo' as varchar(255)) as nome, cast('061051' as varchar(6)) as codice_istat, cast('Caserta' as varchar(100)) as provincia, cast('CE' as varchar(2)) as sigla_provincia, false as citta_metropolitana
    union all select '061052', 'Mondragone', '061052', 'Caserta', 'CE', false
    union all select '061053', 'Orta di Atella', '061053', 'Caserta', 'CE', false
    union all select '061054', 'Parete', '061054', 'Caserta', 'CE', false
    union all select '061055', 'Pastorano', '061055', 'Caserta', 'CE', false
    union all select '061056', 'Piana di Monte Verna', '061056', 'Caserta', 'CE', false
    union all select '061057', 'Piedimonte Matese', '061057', 'Caserta', 'CE', false
    union all select '061058', 'Pietramelara', '061058', 'Caserta', 'CE', false
    union all select '061059', 'Pietravairano', '061059', 'Caserta', 'CE', false
    union all select '061060', 'Pignataro Maggiore', '061060', 'Caserta', 'CE', false
    union all select '061061', 'Pontelatone', '061061', 'Caserta', 'CE', false
    union all select '061062', 'Portico di Caserta', '061062', 'Caserta', 'CE', false
    union all select '061063', 'Prata Sannita', '061063', 'Caserta', 'CE', false
    union all select '061064', 'Pratella', '061064', 'Caserta', 'CE', false
    union all select '061065', 'Presenzano', '061065', 'Caserta', 'CE', false
    union all select '061066', 'Raviscanina', '061066', 'Caserta', 'CE', false
    union all select '061067', 'Recale', '061067', 'Caserta', 'CE', false
    union all select '061068', 'Riardo', '061068', 'Caserta', 'CE', false
    union all select '061069', 'Rocca d''Evandro', '061069', 'Caserta', 'CE', false
    union all select '061070', 'Roccamonfina', '061070', 'Caserta', 'CE', false
    union all select '061071', 'Roccaromana', '061071', 'Caserta', 'CE', false
    union all select '061072', 'Rocchetta e Croce', '061072', 'Caserta', 'CE', false
    union all select '061073', 'Ruviano', '061073', 'Caserta', 'CE', false
    union all select '061074', 'San Cipriano d''Aversa', '061074', 'Caserta', 'CE', false
    union all select '061075', 'San Felice a Cancello', '061075', 'Caserta', 'CE', false
    union all select '061076', 'San Gregorio Matese', '061076', 'Caserta', 'CE', false
    union all select '061077', 'San Marcellino', '061077', 'Caserta', 'CE', false
    union all select '061078', 'San Nicola la Strada', '061078', 'Caserta', 'CE', false
    union all select '061079', 'San Pietro Infine', '061079', 'Caserta', 'CE', false
    union all select '061080', 'San Potito Sannitico', '061080', 'Caserta', 'CE', false
    union all select '061081', 'San Prisco', '061081', 'Caserta', 'CE', false
    union all select '061082', 'Santa Maria a Vico', '061082', 'Caserta', 'CE', false
    union all select '061083', 'Santa Maria Capua Vetere', '061083', 'Caserta', 'CE', false
    union all select '061084', 'Santa Maria la Fossa', '061084', 'Caserta', 'CE', false
    union all select '061085', 'San Tammaro', '061085', 'Caserta', 'CE', false
    union all select '061086', 'Sant''Angelo d''Alife', '061086', 'Caserta', 'CE', false
    union all select '061087', 'Sant''Arpino', '061087', 'Caserta', 'CE', false
    union all select '061088', 'Sessa Aurunca', '061088', 'Caserta', 'CE', false
    union all select '061089', 'Sparanise', '061089', 'Caserta', 'CE', false
    union all select '061090', 'Succivo', '061090', 'Caserta', 'CE', false
    union all select '061091', 'Teano', '061091', 'Caserta', 'CE', false
    union all select '061092', 'Teverola', '061092', 'Caserta', 'CE', false
    union all select '061093', 'Tora e Piccilli', '061093', 'Caserta', 'CE', false
    union all select '061094', 'Trentola Ducenta', '061094', 'Caserta', 'CE', false
    union all select '061095', 'Vairano Patenora', '061095', 'Caserta', 'CE', false
    union all select '061096', 'Valle Agricola', '061096', 'Caserta', 'CE', false
    union all select '061097', 'Valle di Maddaloni', '061097', 'Caserta', 'CE', false
    union all select '061098', 'Villa di Briano', '061098', 'Caserta', 'CE', false
    union all select '061099', 'Villa Literno', '061099', 'Caserta', 'CE', false
    union all select '061100', 'Vitulazio', '061100', 'Caserta', 'CE', false
) v
where not exists (select 1 from catalogo_comuni c where c.codice = v.codice or c.codice_istat = v.codice_istat);

insert into catalogo_comuni (codice, nome, codice_istat, provincia, sigla_provincia, citta_metropolitana)
select v.codice, v.nome, v.codice_istat, v.provincia, v.sigla_provincia, v.citta_metropolitana
from (
    select cast('061101' as varchar(50)) as codice, cast('Falciano del Massico' as varchar(255)) as nome, cast('061101' as varchar(6)) as codice_istat, cast('Caserta' as varchar(100)) as provincia, cast('CE' as varchar(2)) as sigla_provincia, false as citta_metropolitana
    union all select '061102', 'Cellole', '061102', 'Caserta', 'CE', false
    union all select '061103', 'Casapesenna', '061103', 'Caserta', 'CE', false
    union all select '061104', 'San Marco Evangelista', '061104', 'Caserta', 'CE', false
    union all select '062001', 'Airola', '062001', 'Benevento', 'BN', false
    union all select '062002', 'Amorosi', '062002', 'Benevento', 'BN', false
    union all select '062003', 'Apice', '062003', 'Benevento', 'BN', false
    union all select '062004', 'Apollosa', '062004', 'Benevento', 'BN', false
    union all select '062005', 'Arpaia', '062005', 'Benevento', 'BN', false
    union all select '062006', 'Arpaise', '062006', 'Benevento', 'BN', false
    union all select '062007', 'Baselice', '062007', 'Benevento', 'BN', false
    union all select '062008', 'Benevento', '062008', 'Benevento', 'BN', false
    union all select '062009', 'Bonea', '062009', 'Benevento', 'BN', false
    union all select '062010', 'Bucciano', '062010', 'Benevento', 'BN', false
    union all select '062011', 'Buonalbergo', '062011', 'Benevento', 'BN', false
    union all select '062012', 'Calvi', '062012', 'Benevento', 'BN', false
    union all select '062013', 'Campolattaro', '062013', 'Benevento', 'BN', false
    union all select '062014', 'Campoli del Monte Taburno', '062014', 'Benevento', 'BN', false
    union all select '062015', 'Casalduni', '062015', 'Benevento', 'BN', false
    union all select '062016', 'Castelfranco in Miscano', '062016', 'Benevento', 'BN', false
    union all select '062017', 'Castelpagano', '062017', 'Benevento', 'BN', false
    union all select '062018', 'Castelpoto', '062018', 'Benevento', 'BN', false
    union all select '062019', 'Castelvenere', '062019', 'Benevento', 'BN', false
    union all select '062020', 'Castelvetere in Val Fortore', '062020', 'Benevento', 'BN', false
    union all select '062021', 'Cautano', '062021', 'Benevento', 'BN', false
    union all select '062022', 'Ceppaloni', '062022', 'Benevento', 'BN', false
    union all select '062023', 'Cerreto Sannita', '062023', 'Benevento', 'BN', false
    union all select '062024', 'Circello', '062024', 'Benevento', 'BN', false
    union all select '062025', 'Colle Sannita', '062025', 'Benevento', 'BN', false
    union all select '062026', 'Cusano Mutri', '062026', 'Benevento', 'BN', false
    union all select '062027', 'Dugenta', '062027', 'Benevento', 'BN', false
    union all select '062028', 'Durazzano', '062028', 'Benevento', 'BN', false
    union all select '062029', 'Faicchio', '062029', 'Benevento', 'BN', false
    union all select '062030', 'Foglianise', '062030', 'Benevento', 'BN', false
    union all select '062031', 'Foiano di Val Fortore', '062031', 'Benevento', 'BN', false
    union all select '062032', 'Forchia', '062032', 'Benevento', 'BN', false
    union all select '062033', 'Fragneto l''Abate', '062033', 'Benevento', 'BN', false
    union all select '062034', 'Fragneto Monforte', '062034', 'Benevento', 'BN', false
    union all select '062035', 'Frasso Telesino', '062035', 'Benevento', 'BN', false
    union all select '062036', 'Ginestra degli Schiavoni', '062036', 'Benevento', 'BN', false
    union all select '062037', 'Guardia Sanframondi', '062037', 'Benevento', 'BN', false
    union all select '062038', 'Limatola', '062038', 'Benevento', 'BN', false
    union all select '062039', 'Melizzano', '062039', 'Benevento', 'BN', false
    union all select '062040', 'Moiano', '062040', 'Benevento', 'BN', false
    union all select '062041', 'Molinara', '062041', 'Benevento', 'BN', false
    union all select '062042', 'Montefalcone di Val Fortore', '062042', 'Benevento', 'BN', false
    union all select '062043', 'Montesarchio', '062043', 'Benevento', 'BN', false
    union all select '062044', 'Morcone', '062044', 'Benevento', 'BN', false
    union all select '062045', 'Paduli', '062045', 'Benevento', 'BN', false
    union all select '062046', 'Pago Veiano', '062046', 'Benevento', 'BN', false
) v
where not exists (select 1 from catalogo_comuni c where c.codice = v.codice or c.codice_istat = v.codice_istat);

insert into catalogo_comuni (codice, nome, codice_istat, provincia, sigla_provincia, citta_metropolitana)
select v.codice, v.nome, v.codice_istat, v.provincia, v.sigla_provincia, v.citta_metropolitana
from (
    select cast('062047' as varchar(50)) as codice, cast('Pannarano' as varchar(255)) as nome, cast('062047' as varchar(6)) as codice_istat, cast('Benevento' as varchar(100)) as provincia, cast('BN' as varchar(2)) as sigla_provincia, false as citta_metropolitana
    union all select '062048', 'Paolisi', '062048', 'Benevento', 'BN', false
    union all select '062049', 'Paupisi', '062049', 'Benevento', 'BN', false
    union all select '062050', 'Pesco Sannita', '062050', 'Benevento', 'BN', false
    union all select '062051', 'Pietraroja', '062051', 'Benevento', 'BN', false
    union all select '062052', 'Pietrelcina', '062052', 'Benevento', 'BN', false
    union all select '062053', 'Ponte', '062053', 'Benevento', 'BN', false
    union all select '062054', 'Pontelandolfo', '062054', 'Benevento', 'BN', false
    union all select '062055', 'Puglianello', '062055', 'Benevento', 'BN', false
    union all select '062056', 'Reino', '062056', 'Benevento', 'BN', false
    union all select '062057', 'San Bartolomeo in Galdo', '062057', 'Benevento', 'BN', false
    union all select '062058', 'San Giorgio del Sannio', '062058', 'Benevento', 'BN', false
    union all select '062059', 'San Giorgio La Molara', '062059', 'Benevento', 'BN', false
    union all select '062060', 'San Leucio del Sannio', '062060', 'Benevento', 'BN', false
    union all select '062061', 'San Lorenzello', '062061', 'Benevento', 'BN', false
    union all select '062062', 'San Lorenzo Maggiore', '062062', 'Benevento', 'BN', false
    union all select '062063', 'San Lupo', '062063', 'Benevento', 'BN', false
    union all select '062064', 'San Marco dei Cavoti', '062064', 'Benevento', 'BN', false
    union all select '062065', 'San Martino Sannita', '062065', 'Benevento', 'BN', false
    union all select '062066', 'San Nazzaro', '062066', 'Benevento', 'BN', false
    union all select '062067', 'San Nicola Manfredi', '062067', 'Benevento', 'BN', false
    union all select '062068', 'San Salvatore Telesino', '062068', 'Benevento', 'BN', false
    union all select '062069', 'Santa Croce del Sannio', '062069', 'Benevento', 'BN', false
    union all select '062070', 'Sant''Agata de'' Goti', '062070', 'Benevento', 'BN', false
    union all select '062071', 'Sant''Angelo a Cupolo', '062071', 'Benevento', 'BN', false
    union all select '062072', 'Sassinoro', '062072', 'Benevento', 'BN', false
    union all select '062073', 'Solopaca', '062073', 'Benevento', 'BN', false
    union all select '062074', 'Telese Terme', '062074', 'Benevento', 'BN', false
    union all select '062075', 'Tocco Caudio', '062075', 'Benevento', 'BN', false
    union all select '062076', 'Torrecuso', '062076', 'Benevento', 'BN', false
    union all select '062077', 'Vitulano', '062077', 'Benevento', 'BN', false
    union all select '062078', 'Sant''Arcangelo Trimonte', '062078', 'Benevento', 'BN', false
    union all select '063001', 'Acerra', '063001', 'Napoli', 'NA', true
    union all select '063002', 'Afragola', '063002', 'Napoli', 'NA', true
    union all select '063003', 'Agerola', '063003', 'Napoli', 'NA', true
    union all select '063004', 'Anacapri', '063004', 'Napoli', 'NA', true
    union all select '063005', 'Arzano', '063005', 'Napoli', 'NA', true
    union all select '063006', 'Bacoli', '063006', 'Napoli', 'NA', true
    union all select '063007', 'Barano d''Ischia', '063007', 'Napoli', 'NA', true
    union all select '063008', 'Boscoreale', '063008', 'Napoli', 'NA', true
    union all select '063009', 'Boscotrecase', '063009', 'Napoli', 'NA', true
    union all select '063010', 'Brusciano', '063010', 'Napoli', 'NA', true
    union all select '063011', 'Caivano', '063011', 'Napoli', 'NA', true
    union all select '063012', 'Calvizzano', '063012', 'Napoli', 'NA', true
    union all select '063013', 'Camposano', '063013', 'Napoli', 'NA', true
    union all select '063014', 'Capri', '063014', 'Napoli', 'NA', true
    union all select '063015', 'Carbonara di Nola', '063015', 'Napoli', 'NA', true
    union all select '063016', 'Cardito', '063016', 'Napoli', 'NA', true
    union all select '063017', 'Casalnuovo di Napoli', '063017', 'Napoli', 'NA', true
    union all select '063018', 'Casamarciano', '063018', 'Napoli', 'NA', true
) v
where not exists (select 1 from catalogo_comuni c where c.codice = v.codice or c.codice_istat = v.codice_istat);

insert into catalogo_comuni (codice, nome, codice_istat, provincia, sigla_provincia, citta_metropolitana)
select v.codice, v.nome, v.codice_istat, v.provincia, v.sigla_provincia, v.citta_metropolitana
from (
    select cast('063019' as varchar(50)) as codice, cast('Casamicciola Terme' as varchar(255)) as nome, cast('063019' as varchar(6)) as codice_istat, cast('Napoli' as varchar(100)) as provincia, cast('NA' as varchar(2)) as sigla_provincia, true as citta_metropolitana
    union all select '063020', 'Casandrino', '063020', 'Napoli', 'NA', true
    union all select '063021', 'Casavatore', '063021', 'Napoli', 'NA', true
    union all select '063022', 'Casola di Napoli', '063022', 'Napoli', 'NA', true
    union all select '063023', 'Casoria', '063023', 'Napoli', 'NA', true
    union all select '063024', 'Castellammare di Stabia', '063024', 'Napoli', 'NA', true
    union all select '063025', 'Castello di Cisterna', '063025', 'Napoli', 'NA', true
    union all select '063026', 'Cercola', '063026', 'Napoli', 'NA', true
    union all select '063027', 'Cicciano', '063027', 'Napoli', 'NA', true
    union all select '063028', 'Cimitile', '063028', 'Napoli', 'NA', true
    union all select '063029', 'Comiziano', '063029', 'Napoli', 'NA', true
    union all select '063030', 'Crispano', '063030', 'Napoli', 'NA', true
    union all select '063031', 'Forio', '063031', 'Napoli', 'NA', true
    union all select '063032', 'Frattamaggiore', '063032', 'Napoli', 'NA', true
    union all select '063033', 'Frattaminore', '063033', 'Napoli', 'NA', true
    union all select '063034', 'Giugliano in Campania', '063034', 'Napoli', 'NA', true
    union all select '063035', 'Gragnano', '063035', 'Napoli', 'NA', true
    union all select '063036', 'Grumo Nevano', '063036', 'Napoli', 'NA', true
    union all select '063037', 'Ischia', '063037', 'Napoli', 'NA', true
    union all select '063038', 'Lacco Ameno', '063038', 'Napoli', 'NA', true
    union all select '063039', 'Lettere', '063039', 'Napoli', 'NA', true
    union all select '063040', 'Liveri', '063040', 'Napoli', 'NA', true
    union all select '063041', 'Marano di Napoli', '063041', 'Napoli', 'NA', true
    union all select '063042', 'Mariglianella', '063042', 'Napoli', 'NA', true
    union all select '063043', 'Marigliano', '063043', 'Napoli', 'NA', true
    union all select '063044', 'Massa Lubrense', '063044', 'Napoli', 'NA', true
    union all select '063045', 'Melito di Napoli', '063045', 'Napoli', 'NA', true
    union all select '063046', 'Meta', '063046', 'Napoli', 'NA', true
    union all select '063047', 'Monte di Procida', '063047', 'Napoli', 'NA', true
    union all select '063048', 'Mugnano di Napoli', '063048', 'Napoli', 'NA', true
    union all select 'napoli', 'Napoli', '063049', 'Napoli', 'NA', true
    union all select '063050', 'Nola', '063050', 'Napoli', 'NA', true
    union all select '063051', 'Ottaviano', '063051', 'Napoli', 'NA', true
    union all select '063052', 'Palma Campania', '063052', 'Napoli', 'NA', true
    union all select '063053', 'Piano di Sorrento', '063053', 'Napoli', 'NA', true
    union all select '063054', 'Pimonte', '063054', 'Napoli', 'NA', true
    union all select '063055', 'Poggiomarino', '063055', 'Napoli', 'NA', true
    union all select '063056', 'Pollena Trocchia', '063056', 'Napoli', 'NA', true
    union all select '063057', 'Pomigliano d''Arco', '063057', 'Napoli', 'NA', true
    union all select '063058', 'Pompei', '063058', 'Napoli', 'NA', true
    union all select 'portici', 'Portici', '063059', 'Napoli', 'NA', true
    union all select 'pozzuoli', 'Pozzuoli', '063060', 'Napoli', 'NA', true
    union all select '063061', 'Procida', '063061', 'Napoli', 'NA', true
    union all select '063062', 'Qualiano', '063062', 'Napoli', 'NA', true
    union all select '063063', 'Quarto', '063063', 'Napoli', 'NA', true
    union all select '063064', 'Ercolano', '063064', 'Napoli', 'NA', true
    union all select '063065', 'Roccarainola', '063065', 'Napoli', 'NA', true
    union all select '063066', 'San Gennaro Vesuviano', '063066', 'Napoli', 'NA', true
    union all select '063067', 'San Giorgio a Cremano', '063067', 'Napoli', 'NA', true
    union all select '063068', 'San Giuseppe Vesuviano', '063068', 'Napoli', 'NA', true
) v
where not exists (select 1 from catalogo_comuni c where c.codice = v.codice or c.codice_istat = v.codice_istat);

insert into catalogo_comuni (codice, nome, codice_istat, provincia, sigla_provincia, citta_metropolitana)
select v.codice, v.nome, v.codice_istat, v.provincia, v.sigla_provincia, v.citta_metropolitana
from (
    select cast('063069' as varchar(50)) as codice, cast('San Paolo Bel Sito' as varchar(255)) as nome, cast('063069' as varchar(6)) as codice_istat, cast('Napoli' as varchar(100)) as provincia, cast('NA' as varchar(2)) as sigla_provincia, true as citta_metropolitana
    union all select '063070', 'San Sebastiano al Vesuvio', '063070', 'Napoli', 'NA', true
    union all select '063071', 'Sant''Agnello', '063071', 'Napoli', 'NA', true
    union all select '063072', 'Sant''Anastasia', '063072', 'Napoli', 'NA', true
    union all select '063073', 'Sant''Antimo', '063073', 'Napoli', 'NA', true
    union all select '063074', 'Sant''Antonio Abate', '063074', 'Napoli', 'NA', true
    union all select '063075', 'San Vitaliano', '063075', 'Napoli', 'NA', true
    union all select '063076', 'Saviano', '063076', 'Napoli', 'NA', true
    union all select '063077', 'Scisciano', '063077', 'Napoli', 'NA', true
    union all select '063078', 'Serrara Fontana', '063078', 'Napoli', 'NA', true
    union all select '063079', 'Somma Vesuviana', '063079', 'Napoli', 'NA', true
    union all select '063080', 'Sorrento', '063080', 'Napoli', 'NA', true
    union all select '063081', 'Striano', '063081', 'Napoli', 'NA', true
    union all select '063082', 'Terzigno', '063082', 'Napoli', 'NA', true
    union all select '063083', 'Torre Annunziata', '063083', 'Napoli', 'NA', true
    union all select '063084', 'Torre del Greco', '063084', 'Napoli', 'NA', true
    union all select '063085', 'Tufino', '063085', 'Napoli', 'NA', true
    union all select '063086', 'Vico Equense', '063086', 'Napoli', 'NA', true
    union all select '063087', 'Villaricca', '063087', 'Napoli', 'NA', true
    union all select '063088', 'Visciano', '063088', 'Napoli', 'NA', true
    union all select '063089', 'Volla', '063089', 'Napoli', 'NA', true
    union all select '063090', 'Santa Maria la Carità', '063090', 'Napoli', 'NA', true
    union all select '063091', 'Trecase', '063091', 'Napoli', 'NA', true
    union all select '063092', 'Massa di Somma', '063092', 'Napoli', 'NA', true
    union all select '064001', 'Aiello del Sabato', '064001', 'Avellino', 'AV', false
    union all select '064002', 'Altavilla Irpina', '064002', 'Avellino', 'AV', false
    union all select '064003', 'Andretta', '064003', 'Avellino', 'AV', false
    union all select '064004', 'Aquilonia', '064004', 'Avellino', 'AV', false
    union all select '064005', 'Ariano Irpino', '064005', 'Avellino', 'AV', false
    union all select '064006', 'Atripalda', '064006', 'Avellino', 'AV', false
    union all select '064007', 'Avella', '064007', 'Avellino', 'AV', false
    union all select '064008', 'Avellino', '064008', 'Avellino', 'AV', false
    union all select '064009', 'Bagnoli Irpino', '064009', 'Avellino', 'AV', false
    union all select '064010', 'Baiano', '064010', 'Avellino', 'AV', false
    union all select '064011', 'Bisaccia', '064011', 'Avellino', 'AV', false
    union all select '064012', 'Bonito', '064012', 'Avellino', 'AV', false
    union all select '064013', 'Cairano', '064013', 'Avellino', 'AV', false
    union all select '064014', 'Calabritto', '064014', 'Avellino', 'AV', false
    union all select '064015', 'Calitri', '064015', 'Avellino', 'AV', false
    union all select '064016', 'Candida', '064016', 'Avellino', 'AV', false
    union all select '064017', 'Caposele', '064017', 'Avellino', 'AV', false
    union all select '064018', 'Capriglia Irpina', '064018', 'Avellino', 'AV', false
    union all select '064019', 'Carife', '064019', 'Avellino', 'AV', false
    union all select '064020', 'Casalbore', '064020', 'Avellino', 'AV', false
    union all select '064021', 'Cassano Irpino', '064021', 'Avellino', 'AV', false
    union all select '064022', 'Castel Baronia', '064022', 'Avellino', 'AV', false
    union all select '064023', 'Castelfranci', '064023', 'Avellino', 'AV', false
    union all select '064024', 'Castelvetere sul Calore', '064024', 'Avellino', 'AV', false
    union all select '064025', 'Cervinara', '064025', 'Avellino', 'AV', false
    union all select '064026', 'Cesinali', '064026', 'Avellino', 'AV', false
) v
where not exists (select 1 from catalogo_comuni c where c.codice = v.codice or c.codice_istat = v.codice_istat);

insert into catalogo_comuni (codice, nome, codice_istat, provincia, sigla_provincia, citta_metropolitana)
select v.codice, v.nome, v.codice_istat, v.provincia, v.sigla_provincia, v.citta_metropolitana
from (
    select cast('064027' as varchar(50)) as codice, cast('Chianche' as varchar(255)) as nome, cast('064027' as varchar(6)) as codice_istat, cast('Avellino' as varchar(100)) as provincia, cast('AV' as varchar(2)) as sigla_provincia, false as citta_metropolitana
    union all select '064028', 'Chiusano di San Domenico', '064028', 'Avellino', 'AV', false
    union all select '064029', 'Contrada', '064029', 'Avellino', 'AV', false
    union all select '064030', 'Conza della Campania', '064030', 'Avellino', 'AV', false
    union all select '064031', 'Domicella', '064031', 'Avellino', 'AV', false
    union all select '064032', 'Flumeri', '064032', 'Avellino', 'AV', false
    union all select '064033', 'Fontanarosa', '064033', 'Avellino', 'AV', false
    union all select '064034', 'Forino', '064034', 'Avellino', 'AV', false
    union all select '064035', 'Frigento', '064035', 'Avellino', 'AV', false
    union all select '064036', 'Gesualdo', '064036', 'Avellino', 'AV', false
    union all select '064037', 'Greci', '064037', 'Avellino', 'AV', false
    union all select '064038', 'Grottaminarda', '064038', 'Avellino', 'AV', false
    union all select '064039', 'Grottolella', '064039', 'Avellino', 'AV', false
    union all select '064040', 'Guardia Lombardi', '064040', 'Avellino', 'AV', false
    union all select '064041', 'Lacedonia', '064041', 'Avellino', 'AV', false
    union all select '064042', 'Lapio', '064042', 'Avellino', 'AV', false
    union all select '064043', 'Lauro', '064043', 'Avellino', 'AV', false
    union all select '064044', 'Lioni', '064044', 'Avellino', 'AV', false
    union all select '064045', 'Luogosano', '064045', 'Avellino', 'AV', false
    union all select '064046', 'Manocalzati', '064046', 'Avellino', 'AV', false
    union all select '064047', 'Marzano di Nola', '064047', 'Avellino', 'AV', false
    union all select '064048', 'Melito Irpino', '064048', 'Avellino', 'AV', false
    union all select '064049', 'Mercogliano', '064049', 'Avellino', 'AV', false
    union all select '064050', 'Mirabella Eclano', '064050', 'Avellino', 'AV', false
    union all select '064051', 'Montaguto', '064051', 'Avellino', 'AV', false
    union all select '064052', 'Montecalvo Irpino', '064052', 'Avellino', 'AV', false
    union all select '064053', 'Montefalcione', '064053', 'Avellino', 'AV', false
    union all select '064054', 'Monteforte Irpino', '064054', 'Avellino', 'AV', false
    union all select '064055', 'Montefredane', '064055', 'Avellino', 'AV', false
    union all select '064056', 'Montefusco', '064056', 'Avellino', 'AV', false
    union all select '064057', 'Montella', '064057', 'Avellino', 'AV', false
    union all select '064058', 'Montemarano', '064058', 'Avellino', 'AV', false
    union all select '064059', 'Montemiletto', '064059', 'Avellino', 'AV', false
    union all select '064060', 'Monteverde', '064060', 'Avellino', 'AV', false
    union all select '064063', 'Morra De Sanctis', '064063', 'Avellino', 'AV', false
    union all select '064064', 'Moschiano', '064064', 'Avellino', 'AV', false
    union all select '064065', 'Mugnano del Cardinale', '064065', 'Avellino', 'AV', false
    union all select '064066', 'Nusco', '064066', 'Avellino', 'AV', false
    union all select '064067', 'Ospedaletto d''Alpinolo', '064067', 'Avellino', 'AV', false
    union all select '064068', 'Pago del Vallo di Lauro', '064068', 'Avellino', 'AV', false
    union all select '064069', 'Parolise', '064069', 'Avellino', 'AV', false
    union all select '064070', 'Paternopoli', '064070', 'Avellino', 'AV', false
    union all select '064071', 'Petruro Irpino', '064071', 'Avellino', 'AV', false
    union all select '064072', 'Pietradefusi', '064072', 'Avellino', 'AV', false
    union all select '064073', 'Pietrastornina', '064073', 'Avellino', 'AV', false
    union all select '064074', 'Prata di Principato Ultra', '064074', 'Avellino', 'AV', false
    union all select '064075', 'Pratola Serra', '064075', 'Avellino', 'AV', false
    union all select '064076', 'Quadrelle', '064076', 'Avellino', 'AV', false
    union all select '064077', 'Quindici', '064077', 'Avellino', 'AV', false
    union all select '064078', 'Roccabascerana', '064078', 'Avellino', 'AV', false
) v
where not exists (select 1 from catalogo_comuni c where c.codice = v.codice or c.codice_istat = v.codice_istat);

insert into catalogo_comuni (codice, nome, codice_istat, provincia, sigla_provincia, citta_metropolitana)
select v.codice, v.nome, v.codice_istat, v.provincia, v.sigla_provincia, v.citta_metropolitana
from (
    select cast('064079' as varchar(50)) as codice, cast('Rocca San Felice' as varchar(255)) as nome, cast('064079' as varchar(6)) as codice_istat, cast('Avellino' as varchar(100)) as provincia, cast('AV' as varchar(2)) as sigla_provincia, false as citta_metropolitana
    union all select '064080', 'Rotondi', '064080', 'Avellino', 'AV', false
    union all select '064081', 'Salza Irpina', '064081', 'Avellino', 'AV', false
    union all select '064082', 'San Mango sul Calore', '064082', 'Avellino', 'AV', false
    union all select '064083', 'San Martino Valle Caudina', '064083', 'Avellino', 'AV', false
    union all select '064084', 'San Michele di Serino', '064084', 'Avellino', 'AV', false
    union all select '064085', 'San Nicola Baronia', '064085', 'Avellino', 'AV', false
    union all select '064086', 'San Potito Ultra', '064086', 'Avellino', 'AV', false
    union all select '064087', 'San Sossio Baronia', '064087', 'Avellino', 'AV', false
    union all select '064088', 'Santa Lucia di Serino', '064088', 'Avellino', 'AV', false
    union all select '064089', 'Sant''Andrea di Conza', '064089', 'Avellino', 'AV', false
    union all select '064090', 'Sant''Angelo all''Esca', '064090', 'Avellino', 'AV', false
    union all select '064091', 'Sant''Angelo a Scala', '064091', 'Avellino', 'AV', false
    union all select '064092', 'Sant''Angelo dei Lombardi', '064092', 'Avellino', 'AV', false
    union all select '064093', 'Santa Paolina', '064093', 'Avellino', 'AV', false
    union all select '064095', 'Santo Stefano del Sole', '064095', 'Avellino', 'AV', false
    union all select '064096', 'Savignano Irpino', '064096', 'Avellino', 'AV', false
    union all select '064097', 'Scampitella', '064097', 'Avellino', 'AV', false
    union all select '064098', 'Senerchia', '064098', 'Avellino', 'AV', false
    union all select '064099', 'Serino', '064099', 'Avellino', 'AV', false
    union all select '064100', 'Sirignano', '064100', 'Avellino', 'AV', false
    union all select '064101', 'Solofra', '064101', 'Avellino', 'AV', false
    union all select '064102', 'Sorbo Serpico', '064102', 'Avellino', 'AV', false
    union all select '064103', 'Sperone', '064103', 'Avellino', 'AV', false
    union all select '064104', 'Sturno', '064104', 'Avellino', 'AV', false
    union all select '064105', 'Summonte', '064105', 'Avellino', 'AV', false
    union all select '064106', 'Taurano', '064106', 'Avellino', 'AV', false
    union all select '064107', 'Taurasi', '064107', 'Avellino', 'AV', false
    union all select '064108', 'Teora', '064108', 'Avellino', 'AV', false
    union all select '064109', 'Torella dei Lombardi', '064109', 'Avellino', 'AV', false
    union all select '064110', 'Torre Le Nocelle', '064110', 'Avellino', 'AV', false
    union all select '064111', 'Torrioni', '064111', 'Avellino', 'AV', false
    union all select '064112', 'Trevico', '064112', 'Avellino', 'AV', false
    union all select '064113', 'Tufo', '064113', 'Avellino', 'AV', false
    union all select '064114', 'Vallata', '064114', 'Avellino', 'AV', false
    union all select '064115', 'Vallesaccarda', '064115', 'Avellino', 'AV', false
    union all select '064116', 'Venticano', '064116', 'Avellino', 'AV', false
    union all select '064117', 'Villamaina', '064117', 'Avellino', 'AV', false
    union all select '064118', 'Villanova del Battista', '064118', 'Avellino', 'AV', false
    union all select '064119', 'Volturara Irpina', '064119', 'Avellino', 'AV', false
    union all select '064120', 'Zungoli', '064120', 'Avellino', 'AV', false
    union all select '064121', 'Montoro', '064121', 'Avellino', 'AV', false
    union all select '065001', 'Acerno', '065001', 'Salerno', 'SA', false
    union all select '065002', 'Agropoli', '065002', 'Salerno', 'SA', false
    union all select '065003', 'Albanella', '065003', 'Salerno', 'SA', false
    union all select '065004', 'Alfano', '065004', 'Salerno', 'SA', false
    union all select '065005', 'Altavilla Silentina', '065005', 'Salerno', 'SA', false
    union all select '065006', 'Amalfi', '065006', 'Salerno', 'SA', false
    union all select '065007', 'Angri', '065007', 'Salerno', 'SA', false
    union all select '065008', 'Aquara', '065008', 'Salerno', 'SA', false
) v
where not exists (select 1 from catalogo_comuni c where c.codice = v.codice or c.codice_istat = v.codice_istat);

insert into catalogo_comuni (codice, nome, codice_istat, provincia, sigla_provincia, citta_metropolitana)
select v.codice, v.nome, v.codice_istat, v.provincia, v.sigla_provincia, v.citta_metropolitana
from (
    select cast('065009' as varchar(50)) as codice, cast('Ascea' as varchar(255)) as nome, cast('065009' as varchar(6)) as codice_istat, cast('Salerno' as varchar(100)) as provincia, cast('SA' as varchar(2)) as sigla_provincia, false as citta_metropolitana
    union all select '065010', 'Atena Lucana', '065010', 'Salerno', 'SA', false
    union all select '065011', 'Atrani', '065011', 'Salerno', 'SA', false
    union all select '065012', 'Auletta', '065012', 'Salerno', 'SA', false
    union all select '065013', 'Baronissi', '065013', 'Salerno', 'SA', false
    union all select '065014', 'Battipaglia', '065014', 'Salerno', 'SA', false
    union all select '065015', 'Bellosguardo', '065015', 'Salerno', 'SA', false
    union all select '065016', 'Bracigliano', '065016', 'Salerno', 'SA', false
    union all select '065017', 'Buccino', '065017', 'Salerno', 'SA', false
    union all select '065018', 'Buonabitacolo', '065018', 'Salerno', 'SA', false
    union all select '065019', 'Caggiano', '065019', 'Salerno', 'SA', false
    union all select '065020', 'Calvanico', '065020', 'Salerno', 'SA', false
    union all select '065021', 'Camerota', '065021', 'Salerno', 'SA', false
    union all select '065022', 'Campagna', '065022', 'Salerno', 'SA', false
    union all select '065023', 'Campora', '065023', 'Salerno', 'SA', false
    union all select '065024', 'Cannalonga', '065024', 'Salerno', 'SA', false
    union all select '065025', 'Capaccio Paestum', '065025', 'Salerno', 'SA', false
    union all select '065026', 'Casalbuono', '065026', 'Salerno', 'SA', false
    union all select '065027', 'Casaletto Spartano', '065027', 'Salerno', 'SA', false
    union all select '065028', 'Casal Velino', '065028', 'Salerno', 'SA', false
    union all select '065029', 'Caselle in Pittari', '065029', 'Salerno', 'SA', false
    union all select '065030', 'Castelcivita', '065030', 'Salerno', 'SA', false
    union all select '065031', 'Castellabate', '065031', 'Salerno', 'SA', false
    union all select '065032', 'Castelnuovo Cilento', '065032', 'Salerno', 'SA', false
    union all select '065033', 'Castelnuovo di Conza', '065033', 'Salerno', 'SA', false
    union all select '065034', 'Castel San Giorgio', '065034', 'Salerno', 'SA', false
    union all select '065035', 'Castel San Lorenzo', '065035', 'Salerno', 'SA', false
    union all select '065036', 'Castiglione del Genovesi', '065036', 'Salerno', 'SA', false
    union all select '065037', 'Cava de'' Tirreni', '065037', 'Salerno', 'SA', false
    union all select '065038', 'Celle di Bulgheria', '065038', 'Salerno', 'SA', false
    union all select '065039', 'Centola', '065039', 'Salerno', 'SA', false
    union all select '065040', 'Ceraso', '065040', 'Salerno', 'SA', false
    union all select '065041', 'Cetara', '065041', 'Salerno', 'SA', false
    union all select '065042', 'Cicerale', '065042', 'Salerno', 'SA', false
    union all select '065043', 'Colliano', '065043', 'Salerno', 'SA', false
    union all select '065044', 'Conca dei Marini', '065044', 'Salerno', 'SA', false
    union all select '065045', 'Controne', '065045', 'Salerno', 'SA', false
    union all select '065046', 'Contursi Terme', '065046', 'Salerno', 'SA', false
    union all select '065047', 'Corbara', '065047', 'Salerno', 'SA', false
    union all select '065048', 'Corleto Monforte', '065048', 'Salerno', 'SA', false
    union all select '065049', 'Cuccaro Vetere', '065049', 'Salerno', 'SA', false
    union all select '065050', 'Eboli', '065050', 'Salerno', 'SA', false
    union all select '065051', 'Felitto', '065051', 'Salerno', 'SA', false
    union all select '065052', 'Fisciano', '065052', 'Salerno', 'SA', false
    union all select '065053', 'Furore', '065053', 'Salerno', 'SA', false
    union all select '065054', 'Futani', '065054', 'Salerno', 'SA', false
    union all select '065055', 'Giffoni Sei Casali', '065055', 'Salerno', 'SA', false
    union all select '065056', 'Giffoni Valle Piana', '065056', 'Salerno', 'SA', false
    union all select '065057', 'Gioi', '065057', 'Salerno', 'SA', false
    union all select '065058', 'Giungano', '065058', 'Salerno', 'SA', false
) v
where not exists (select 1 from catalogo_comuni c where c.codice = v.codice or c.codice_istat = v.codice_istat);

insert into catalogo_comuni (codice, nome, codice_istat, provincia, sigla_provincia, citta_metropolitana)
select v.codice, v.nome, v.codice_istat, v.provincia, v.sigla_provincia, v.citta_metropolitana
from (
    select cast('065059' as varchar(50)) as codice, cast('Ispani' as varchar(255)) as nome, cast('065059' as varchar(6)) as codice_istat, cast('Salerno' as varchar(100)) as provincia, cast('SA' as varchar(2)) as sigla_provincia, false as citta_metropolitana
    union all select '065060', 'Laureana Cilento', '065060', 'Salerno', 'SA', false
    union all select '065061', 'Laurino', '065061', 'Salerno', 'SA', false
    union all select '065062', 'Laurito', '065062', 'Salerno', 'SA', false
    union all select '065063', 'Laviano', '065063', 'Salerno', 'SA', false
    union all select '065064', 'Lustra', '065064', 'Salerno', 'SA', false
    union all select '065065', 'Magliano Vetere', '065065', 'Salerno', 'SA', false
    union all select '065066', 'Maiori', '065066', 'Salerno', 'SA', false
    union all select '065067', 'Mercato San Severino', '065067', 'Salerno', 'SA', false
    union all select '065068', 'Minori', '065068', 'Salerno', 'SA', false
    union all select '065069', 'Moio della Civitella', '065069', 'Salerno', 'SA', false
    union all select '065070', 'Montano Antilia', '065070', 'Salerno', 'SA', false
    union all select '065071', 'Montecorice', '065071', 'Salerno', 'SA', false
    union all select '065072', 'Montecorvino Pugliano', '065072', 'Salerno', 'SA', false
    union all select '065073', 'Montecorvino Rovella', '065073', 'Salerno', 'SA', false
    union all select '065074', 'Monteforte Cilento', '065074', 'Salerno', 'SA', false
    union all select '065075', 'Monte San Giacomo', '065075', 'Salerno', 'SA', false
    union all select '065076', 'Montesano sulla Marcellana', '065076', 'Salerno', 'SA', false
    union all select '065077', 'Morigerati', '065077', 'Salerno', 'SA', false
    union all select '065078', 'Nocera Inferiore', '065078', 'Salerno', 'SA', false
    union all select '065079', 'Nocera Superiore', '065079', 'Salerno', 'SA', false
    union all select '065080', 'Novi Velia', '065080', 'Salerno', 'SA', false
    union all select '065081', 'Ogliastro Cilento', '065081', 'Salerno', 'SA', false
    union all select '065082', 'Olevano sul Tusciano', '065082', 'Salerno', 'SA', false
    union all select '065083', 'Oliveto Citra', '065083', 'Salerno', 'SA', false
    union all select '065084', 'Omignano', '065084', 'Salerno', 'SA', false
    union all select '065085', 'Orria', '065085', 'Salerno', 'SA', false
    union all select '065086', 'Ottati', '065086', 'Salerno', 'SA', false
    union all select '065087', 'Padula', '065087', 'Salerno', 'SA', false
    union all select '065088', 'Pagani', '065088', 'Salerno', 'SA', false
    union all select '065089', 'Palomonte', '065089', 'Salerno', 'SA', false
    union all select '065090', 'Pellezzano', '065090', 'Salerno', 'SA', false
    union all select '065091', 'Perdifumo', '065091', 'Salerno', 'SA', false
    union all select '065092', 'Perito', '065092', 'Salerno', 'SA', false
    union all select '065093', 'Pertosa', '065093', 'Salerno', 'SA', false
    union all select '065094', 'Petina', '065094', 'Salerno', 'SA', false
    union all select '065095', 'Piaggine', '065095', 'Salerno', 'SA', false
    union all select '065096', 'Pisciotta', '065096', 'Salerno', 'SA', false
    union all select '065097', 'Polla', '065097', 'Salerno', 'SA', false
    union all select '065098', 'Pollica', '065098', 'Salerno', 'SA', false
    union all select '065099', 'Pontecagnano Faiano', '065099', 'Salerno', 'SA', false
    union all select '065100', 'Positano', '065100', 'Salerno', 'SA', false
    union all select '065101', 'Postiglione', '065101', 'Salerno', 'SA', false
    union all select '065102', 'Praiano', '065102', 'Salerno', 'SA', false
    union all select '065103', 'Prignano Cilento', '065103', 'Salerno', 'SA', false
    union all select '065104', 'Ravello', '065104', 'Salerno', 'SA', false
    union all select '065105', 'Ricigliano', '065105', 'Salerno', 'SA', false
    union all select '065106', 'Roccadaspide', '065106', 'Salerno', 'SA', false
    union all select '065107', 'Roccagloriosa', '065107', 'Salerno', 'SA', false
    union all select '065108', 'Roccapiemonte', '065108', 'Salerno', 'SA', false
) v
where not exists (select 1 from catalogo_comuni c where c.codice = v.codice or c.codice_istat = v.codice_istat);

insert into catalogo_comuni (codice, nome, codice_istat, provincia, sigla_provincia, citta_metropolitana)
select v.codice, v.nome, v.codice_istat, v.provincia, v.sigla_provincia, v.citta_metropolitana
from (
    select cast('065109' as varchar(50)) as codice, cast('Rofrano' as varchar(255)) as nome, cast('065109' as varchar(6)) as codice_istat, cast('Salerno' as varchar(100)) as provincia, cast('SA' as varchar(2)) as sigla_provincia, false as citta_metropolitana
    union all select '065110', 'Romagnano al Monte', '065110', 'Salerno', 'SA', false
    union all select '065111', 'Roscigno', '065111', 'Salerno', 'SA', false
    union all select '065112', 'Rutino', '065112', 'Salerno', 'SA', false
    union all select '065113', 'Sacco', '065113', 'Salerno', 'SA', false
    union all select '065114', 'Sala Consilina', '065114', 'Salerno', 'SA', false
    union all select '065115', 'Salento', '065115', 'Salerno', 'SA', false
    union all select '065116', 'Salerno', '065116', 'Salerno', 'SA', false
    union all select '065117', 'Salvitelle', '065117', 'Salerno', 'SA', false
    union all select '065118', 'San Cipriano Picentino', '065118', 'Salerno', 'SA', false
    union all select '065119', 'San Giovanni a Piro', '065119', 'Salerno', 'SA', false
    union all select '065120', 'San Gregorio Magno', '065120', 'Salerno', 'SA', false
    union all select '065121', 'San Mango Piemonte', '065121', 'Salerno', 'SA', false
    union all select '065122', 'San Marzano sul Sarno', '065122', 'Salerno', 'SA', false
    union all select '065123', 'San Mauro Cilento', '065123', 'Salerno', 'SA', false
    union all select '065124', 'San Mauro la Bruca', '065124', 'Salerno', 'SA', false
    union all select '065125', 'San Pietro al Tanagro', '065125', 'Salerno', 'SA', false
    union all select '065126', 'San Rufo', '065126', 'Salerno', 'SA', false
    union all select '065127', 'Santa Marina', '065127', 'Salerno', 'SA', false
    union all select '065128', 'Sant''Angelo a Fasanella', '065128', 'Salerno', 'SA', false
    union all select '065129', 'Sant''Arsenio', '065129', 'Salerno', 'SA', false
    union all select '065130', 'Sant''Egidio del Monte Albino', '065130', 'Salerno', 'SA', false
    union all select '065131', 'Santomenna', '065131', 'Salerno', 'SA', false
    union all select '065132', 'San Valentino Torio', '065132', 'Salerno', 'SA', false
    union all select '065133', 'Sanza', '065133', 'Salerno', 'SA', false
    union all select '065134', 'Sapri', '065134', 'Salerno', 'SA', false
    union all select '065135', 'Sarno', '065135', 'Salerno', 'SA', false
    union all select '065136', 'Sassano', '065136', 'Salerno', 'SA', false
    union all select '065137', 'Scafati', '065137', 'Salerno', 'SA', false
    union all select '065138', 'Scala', '065138', 'Salerno', 'SA', false
    union all select '065139', 'Serramezzana', '065139', 'Salerno', 'SA', false
    union all select '065140', 'Serre', '065140', 'Salerno', 'SA', false
    union all select '065141', 'Sessa Cilento', '065141', 'Salerno', 'SA', false
    union all select '065142', 'Siano', '065142', 'Salerno', 'SA', false
    union all select '065143', 'Sicignano degli Alburni', '065143', 'Salerno', 'SA', false
    union all select '065144', 'Stella Cilento', '065144', 'Salerno', 'SA', false
    union all select '065145', 'Stio', '065145', 'Salerno', 'SA', false
    union all select '065146', 'Teggiano', '065146', 'Salerno', 'SA', false
    union all select '065147', 'Torchiara', '065147', 'Salerno', 'SA', false
    union all select '065148', 'Torraca', '065148', 'Salerno', 'SA', false
    union all select '065149', 'Torre Orsaia', '065149', 'Salerno', 'SA', false
    union all select '065150', 'Tortorella', '065150', 'Salerno', 'SA', false
    union all select '065151', 'Tramonti', '065151', 'Salerno', 'SA', false
    union all select '065152', 'Trentinara', '065152', 'Salerno', 'SA', false
    union all select '065153', 'Valle dell''Angelo', '065153', 'Salerno', 'SA', false
    union all select '065154', 'Vallo della Lucania', '065154', 'Salerno', 'SA', false
    union all select '065155', 'Valva', '065155', 'Salerno', 'SA', false
    union all select '065156', 'Vibonati', '065156', 'Salerno', 'SA', false
    union all select '065157', 'Vietri sul Mare', '065157', 'Salerno', 'SA', false
    union all select '065158', 'Bellizzi', '065158', 'Salerno', 'SA', false
) v
where not exists (select 1 from catalogo_comuni c where c.codice = v.codice or c.codice_istat = v.codice_istat);

-- Adiacenze amministrative basate su tratto di confine condiviso (comune_a < comune_b).
insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('061001' as varchar(50)) as comune_a, cast('061063' as varchar(50)) as comune_b
    union all select '061001', '061064'
    union all select '061001', '061066'
    union all select '061001', '061095'
    union all select '061002', '061003'
    union all select '061002', '061006'
    union all select '061002', '061033'
    union all select '061002', '061041'
    union all select '061002', '061057'
    union all select '061002', '061080'
    union all select '061002', '061086'
    union all select '061003', '061009'
    union all select '061003', '061033'
    union all select '061003', '061041'
    union all select '061003', '061045'
    union all select '061003', '061073'
    union all select '061004', '061075'
    union all select '061004', '061082'
    union all select '061004', '062032'
    union all select '061004', '062040'
    union all select '061004', '062070'
    union all select '061004', '063065'
    union all select '061005', '061016'
    union all select '061005', '061020'
    union all select '061005', '061029'
    union all select '061005', '061037'
    union all select '061005', '061043'
    union all select '061005', '061046'
    union all select '061005', '061077'
    union all select '061005', '061092'
    union all select '061005', '061094'
    union all select '061005', '063034'
    union all select '061005', '063073'
    union all select '061006', '061033'
    union all select '061006', '061059'
    union all select '061006', '061071'
    union all select '061006', '061086'
    union all select '061007', '061011'
    union all select '061007', '061015'
    union all select '061007', '061061'
    union all select '061007', '061100'
    union all select '061008', '061050'
    union all select '061008', '061070'
    union all select '061008', '061091'
    union all select '061008', '061095'
    union all select '061009', '061023'
    union all select '061009', '061024'
    union all select '061009', '061045'
    union all select '061009', '061056'
    union all select '061009', '061073'
    union all select '061009', '062038'
    union all select '061010', '061036'
    union all select '061010', '061040'
    union all select '061010', '061060'
    union all select '061010', '061072'
    union all select '061010', '061089'
    union all select '061010', '061091'
    union all select '061011', '061035'
    union all select '061011', '061040'
    union all select '061011', '061055'
    union all select '061011', '061061'
    union all select '061011', '061100'
    union all select '061012', '061019'
    union all select '061012', '061027'
    union all select '061012', '061042'
    union all select '061012', '061052'
    union all select '061012', '061099'
    union all select '061012', '061101'
    union all select '061013', '061049'
    union all select '061013', '061062'
    union all select '061013', '061067'
    union all select '061013', '061078'
    union all select '061014', '061030'
    union all select '061014', '061034'
    union all select '061014', '061038'
    union all select '061015', '061022'
    union all select '061015', '061024'
    union all select '061015', '061026'
    union all select '061015', '061042'
    union all select '061015', '061056'
    union all select '061015', '061061'
    union all select '061015', '061081'
    union all select '061015', '061083'
    union all select '061015', '061084'
    union all select '061015', '061085'
    union all select '061015', '061100'
    union all select '061016', '061043'
    union all select '061016', '061049'
    union all select '061016', '061083'
    union all select '061016', '061092'
    union all select '061017', '061036'
    union all select '061017', '061088'
    union all select '061017', '061091'
    union all select '061017', '061101'
    union all select '061018', '061021'
    union all select '061018', '061022'
    union all select '061018', '061067'
    union all select '061018', '061081'
    union all select '061019', '061042'
    union all select '061019', '061074'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);

insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('061019' as varchar(50)) as comune_a, cast('061084' as varchar(50)) as comune_b
    union all select '061019', '061085'
    union all select '061019', '061098'
    union all select '061019', '061099'
    union all select '061020', '061037'
    union all select '061020', '061083'
    union all select '061020', '061085'
    union all select '061020', '061092'
    union all select '061021', '061032'
    union all select '061021', '061047'
    union all select '061021', '061081'
    union all select '061022', '061026'
    union all select '061022', '061048'
    union all select '061022', '061078'
    union all select '061022', '061081'
    union all select '061022', '061097'
    union all select '061022', '061104'
    union all select '061022', '062038'
    union all select '061022', '062070'
    union all select '061023', '061073'
    union all select '061023', '062002'
    union all select '061023', '062027'
    union all select '061023', '062038'
    union all select '061023', '062039'
    union all select '061024', '061045'
    union all select '061024', '061056'
    union all select '061024', '061061'
    union all select '061025', '061057'
    union all select '061025', '061076'
    union all select '061025', '061080'
    union all select '061026', '061056'
    union all select '061026', '062038'
    union all select '061027', '061052'
    union all select '061027', '061099'
    union all select '061027', '063034'
    union all select '061028', '061048'
    union all select '061028', '061082'
    union all select '061028', '061097'
    union all select '061028', '062028'
    union all select '061029', '061043'
    union all select '061029', '061087'
    union all select '061029', '061090'
    union all select '061029', '063073'
    union all select '061030', '061034'
    union all select '061030', '061063'
    union all select '061030', '061064'
    union all select '061031', '061039'
    union all select '061031', '061050'
    union all select '061031', '061051'
    union all select '061031', '061065'
    union all select '061031', '061070'
    union all select '061031', '061093'
    union all select '061032', '061047'
    union all select '061032', '061081'
    union all select '061032', '061083'
    union all select '061033', '061045'
    union all select '061033', '061071'
    union all select '061034', '061038'
    union all select '061034', '061063'
    union all select '061035', '061040'
    union all select '061035', '061058'
    union all select '061035', '061061'
    union all select '061035', '061071'
    union all select '061035', '061072'
    union all select '061036', '061042'
    union all select '061036', '061060'
    union all select '061036', '061089'
    union all select '061036', '061091'
    union all select '061036', '061101'
    union all select '061037', '061077'
    union all select '061037', '061085'
    union all select '061037', '061098'
    union all select '061038', '061044'
    union all select '061038', '061063'
    union all select '061039', '061051'
    union all select '061039', '061069'
    union all select '061039', '061070'
    union all select '061039', '061088'
    union all select '061040', '061055'
    union all select '061040', '061060'
    union all select '061040', '061072'
    union all select '061041', '061073'
    union all select '061041', '061080'
    union all select '061041', '062026'
    union all select '061041', '062029'
    union all select '061042', '061060'
    union all select '061042', '061084'
    union all select '061042', '061100'
    union all select '061042', '061101'
    union all select '061043', '061049'
    union all select '061043', '061090'
    union all select '061044', '061063'
    union all select '061044', '061076'
    union all select '061044', '061096'
    union all select '061045', '061061'
    union all select '061045', '061071'
    union all select '061046', '061054'
    union all select '061046', '061094'
    union all select '061046', '063034'
    union all select '061047', '061049'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);

insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('061047' as varchar(50)) as comune_a, cast('061062' as varchar(50)) as comune_b
    union all select '061047', '061067'
    union all select '061047', '061083'
    union all select '061048', '061049'
    union all select '061048', '061075'
    union all select '061048', '061082'
    union all select '061048', '061097'
    union all select '061048', '061104'
    union all select '061048', '063001'
    union all select '061049', '061053'
    union all select '061049', '061062'
    union all select '061049', '061083'
    union all select '061049', '061090'
    union all select '061049', '061104'
    union all select '061049', '063001'
    union all select '061049', '063011'
    union all select '061050', '061065'
    union all select '061050', '061070'
    union all select '061050', '061093'
    union all select '061050', '061095'
    union all select '061051', '061065'
    union all select '061051', '061069'
    union all select '061051', '061079'
    union all select '061052', '061088'
    union all select '061052', '061101'
    union all select '061053', '061087'
    union all select '061053', '061090'
    union all select '061053', '063011'
    union all select '061053', '063030'
    union all select '061053', '063033'
    union all select '061054', '061094'
    union all select '061054', '063034'
    union all select '061055', '061060'
    union all select '061055', '061100'
    union all select '061057', '061076'
    union all select '061057', '061080'
    union all select '061057', '061086'
    union all select '061057', '062026'
    union all select '061058', '061059'
    union all select '061058', '061068'
    union all select '061058', '061071'
    union all select '061058', '061072'
    union all select '061059', '061066'
    union all select '061059', '061068'
    union all select '061059', '061071'
    union all select '061059', '061086'
    union all select '061059', '061095'
    union all select '061060', '061089'
    union all select '061060', '061100'
    union all select '061061', '061071'
    union all select '061062', '061067'
    union all select '061063', '061064'
    union all select '061063', '061066'
    union all select '061063', '061096'
    union all select '061064', '061065'
    union all select '061064', '061095'
    union all select '061065', '061093'
    union all select '061065', '061095'
    union all select '061066', '061086'
    union all select '061066', '061095'
    union all select '061066', '061096'
    union all select '061067', '061078'
    union all select '061068', '061072'
    union all select '061068', '061091'
    union all select '061068', '061095'
    union all select '061069', '061088'
    union all select '061070', '061088'
    union all select '061070', '061091'
    union all select '061072', '061091'
    union all select '061073', '062002'
    union all select '061073', '062029'
    union all select '061073', '062055'
    union all select '061074', '061098'
    union all select '061074', '061099'
    union all select '061074', '061103'
    union all select '061074', '063034'
    union all select '061075', '061082'
    union all select '061075', '063001'
    union all select '061075', '063050'
    union all select '061075', '063065'
    union all select '061076', '061086'
    union all select '061076', '061096'
    union all select '061077', '061094'
    union all select '061077', '061098'
    union all select '061077', '061103'
    union all select '061078', '061104'
    union all select '061080', '062026'
    union all select '061081', '061083'
    union all select '061082', '062028'
    union all select '061082', '062070'
    union all select '061083', '061085'
    union all select '061083', '061092'
    union all select '061084', '061085'
    union all select '061085', '061098'
    union all select '061087', '061090'
    union all select '061087', '063032'
    union all select '061087', '063033'
    union all select '061087', '063036'
    union all select '061087', '063073'
    union all select '061088', '061091'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);

insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('061088' as varchar(50)) as comune_a, cast('061101' as varchar(50)) as comune_b
    union all select '061088', '061102'
    union all select '061091', '061095'
    union all select '061094', '061103'
    union all select '061094', '063034'
    union all select '061097', '062028'
    union all select '061097', '062070'
    union all select '061098', '061103'
    union all select '061099', '063034'
    union all select '061103', '063034'
    union all select '062001', '062005'
    union all select '062001', '062009'
    union all select '062001', '062010'
    union all select '062001', '062032'
    union all select '062001', '062040'
    union all select '062001', '062048'
    union all select '062001', '064080'
    union all select '062002', '062039'
    union all select '062002', '062055'
    union all select '062002', '062068'
    union all select '062002', '062074'
    union all select '062003', '062011'
    union all select '062003', '062012'
    union all select '062003', '062045'
    union all select '062003', '062058'
    union all select '062003', '062078'
    union all select '062003', '064005'
    union all select '062003', '064012'
    union all select '062003', '064048'
    union all select '062003', '064050'
    union all select '062003', '064052'
    union all select '062003', '064116'
    union all select '062004', '062008'
    union all select '062004', '062014'
    union all select '062004', '062018'
    union all select '062004', '062022'
    union all select '062004', '062043'
    union all select '062004', '062060'
    union all select '062004', '064078'
    union all select '062005', '062032'
    union all select '062005', '062048'
    union all select '062005', '063065'
    union all select '062006', '062022'
    union all select '062006', '064002'
    union all select '062006', '064073'
    union all select '062006', '064078'
    union all select '062007', '062020'
    union all select '062007', '062025'
    union all select '062007', '062031'
    union all select '062007', '062057'
    union all select '062007', '062064'
    union all select '062008', '062018'
    union all select '062008', '062030'
    union all select '062008', '062034'
    union all select '062008', '062045'
    union all select '062008', '062050'
    union all select '062008', '062052'
    union all select '062008', '062060'
    union all select '062008', '062067'
    union all select '062008', '062071'
    union all select '062008', '062076'
    union all select '062009', '062010'
    union all select '062009', '062043'
    union all select '062009', '062075'
    union all select '062009', '064080'
    union all select '062010', '062040'
    union all select '062010', '062075'
    union all select '062011', '062045'
    union all select '062011', '062059'
    union all select '062011', '062078'
    union all select '062011', '064020'
    union all select '062011', '064052'
    union all select '062012', '062058'
    union all select '062012', '062066'
    union all select '062012', '064072'
    union all select '062012', '064116'
    union all select '062013', '062015'
    union all select '062013', '062024'
    union all select '062013', '062033'
    union all select '062013', '062034'
    union all select '062013', '062044'
    union all select '062013', '062054'
    union all select '062014', '062018'
    union all select '062014', '062021'
    union all select '062014', '062043'
    union all select '062014', '062075'
    union all select '062014', '062077'
    union all select '062015', '062034'
    union all select '062015', '062053'
    union all select '062015', '062054'
    union all select '062015', '062063'
    union all select '062016', '062036'
    union all select '062016', '062042'
    union all select '062016', '064005'
    union all select '062016', '064037'
    union all select '062016', '064052'
    union all select '062017', '062024'
    union all select '062017', '062025'
    union all select '062017', '062069'
    union all select '062018', '062030'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);

insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('062018' as varchar(50)) as comune_a, cast('062077' as varchar(50)) as comune_b
    union all select '062019', '062037'
    union all select '062019', '062061'
    union all select '062019', '062068'
    union all select '062019', '062073'
    union all select '062019', '062074'
    union all select '062020', '062025'
    union all select '062020', '062057'
    union all select '062021', '062035'
    union all select '062021', '062075'
    union all select '062021', '062077'
    union all select '062022', '062060'
    union all select '062022', '062067'
    union all select '062022', '062071'
    union all select '062022', '064002'
    union all select '062022', '064027'
    union all select '062022', '064078'
    union all select '062023', '062026'
    union all select '062023', '062037'
    union all select '062023', '062044'
    union all select '062023', '062051'
    union all select '062023', '062054'
    union all select '062023', '062061'
    union all select '062023', '062063'
    union all select '062024', '062025'
    union all select '062024', '062033'
    union all select '062024', '062044'
    union all select '062024', '062056'
    union all select '062024', '062069'
    union all select '062025', '062056'
    union all select '062025', '062064'
    union all select '062026', '062029'
    union all select '062026', '062051'
    union all select '062026', '062061'
    union all select '062027', '062035'
    union all select '062027', '062038'
    union all select '062027', '062039'
    union all select '062027', '062070'
    union all select '062028', '062070'
    union all select '062029', '062055'
    union all select '062029', '062061'
    union all select '062029', '062068'
    union all select '062030', '062076'
    union all select '062030', '062077'
    union all select '062031', '062041'
    union all select '062031', '062042'
    union all select '062031', '062057'
    union all select '062031', '062059'
    union all select '062031', '062064'
    union all select '062032', '062040'
    union all select '062032', '063065'
    union all select '062033', '062034'
    union all select '062033', '062050'
    union all select '062033', '062056'
    union all select '062034', '062050'
    union all select '062034', '062053'
    union all select '062034', '062054'
    union all select '062034', '062076'
    union all select '062035', '062039'
    union all select '062035', '062070'
    union all select '062035', '062073'
    union all select '062035', '062075'
    union all select '062035', '062077'
    union all select '062036', '062042'
    union all select '062036', '062059'
    union all select '062036', '064020'
    union all select '062036', '064052'
    union all select '062037', '062061'
    union all select '062037', '062062'
    union all select '062037', '062063'
    union all select '062037', '062073'
    union all select '062037', '062077'
    union all select '062038', '062070'
    union all select '062039', '062073'
    union all select '062039', '062074'
    union all select '062040', '062070'
    union all select '062040', '062075'
    union all select '062041', '062059'
    union all select '062041', '062064'
    union all select '062042', '062059'
    union all select '062043', '062075'
    union all select '062043', '064025'
    union all select '062043', '064078'
    union all select '062043', '064080'
    union all select '062043', '064083'
    union all select '062044', '062051'
    union all select '062044', '062054'
    union all select '062044', '062069'
    union all select '062044', '062072'
    union all select '062045', '062046'
    union all select '062045', '062052'
    union all select '062045', '062058'
    union all select '062045', '062059'
    union all select '062045', '062067'
    union all select '062045', '062078'
    union all select '062046', '062050'
    union all select '062046', '062052'
    union all select '062046', '062059'
    union all select '062046', '062064'
    union all select '062047', '064073'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);

insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('062047' as varchar(50)) as comune_a, cast('064078' as varchar(50)) as comune_b
    union all select '062047', '064083'
    union all select '062047', '064103'
    union all select '062047', '064105'
    union all select '062048', '063065'
    union all select '062048', '064080'
    union all select '062049', '062053'
    union all select '062049', '062062'
    union all select '062049', '062076'
    union all select '062049', '062077'
    union all select '062050', '062052'
    union all select '062050', '062056'
    union all select '062050', '062064'
    union all select '062053', '062062'
    union all select '062053', '062063'
    union all select '062053', '062076'
    union all select '062054', '062063'
    union all select '062055', '062068'
    union all select '062056', '062064'
    union all select '062058', '062065'
    union all select '062058', '062066'
    union all select '062058', '062067'
    union all select '062059', '062064'
    union all select '062059', '064020'
    union all select '062060', '062071'
    union all select '062061', '062068'
    union all select '062062', '062063'
    union all select '062062', '062077'
    union all select '062065', '062066'
    union all select '062065', '062067'
    union all select '062065', '062071'
    union all select '062065', '064056'
    union all select '062066', '064056'
    union all select '062066', '064072'
    union all select '062067', '062071'
    union all select '062067', '064027'
    union all select '062067', '064071'
    union all select '062067', '064111'
    union all select '062068', '062074'
    union all select '062070', '062075'
    union all select '062071', '064027'
    union all select '062073', '062074'
    union all select '062073', '062077'
    union all select '062076', '062077'
    union all select '063001', '063002'
    union all select '063001', '063010'
    union all select '063001', '063011'
    union all select '063001', '063017'
    union all select '063001', '063025'
    union all select '063001', '063043'
    union all select '063001', '063050'
    union all select '063001', '063057'
    union all select '063002', '063011'
    union all select '063002', '063016'
    union all select '063002', '063017'
    union all select '063002', '063023'
    union all select '063003', '063035'
    union all select '063003', '063054'
    union all select '063003', '065006'
    union all select '063003', '065053'
    union all select '063003', '065100'
    union all select '063003', '065102'
    union all select '063003', '065138'
    union all select '063004', '063014'
    union all select '063005', '063020'
    union all select '063005', '063021'
    union all select '063005', '063023'
    union all select '063005', '063032'
    union all select '063005', '063036'
    union all select '063005', 'napoli'
    union all select '063006', '063047'
    union all select '063006', 'pozzuoli'
    union all select '063007', '063019'
    union all select '063007', '063037'
    union all select '063007', '063078'
    union all select '063008', '063009'
    union all select '063008', '063055'
    union all select '063008', '063058'
    union all select '063008', '063082'
    union all select '063008', '063083'
    union all select '063008', '065137'
    union all select '063009', '063051'
    union all select '063009', '063082'
    union all select '063009', '063083'
    union all select '063009', '063091'
    union all select '063010', '063025'
    union all select '063010', '063042'
    union all select '063010', '063043'
    union all select '063010', '063079'
    union all select '063011', '063016'
    union all select '063011', '063030'
    union all select '063012', '063041'
    union all select '063012', '063048'
    union all select '063012', '063062'
    union all select '063012', '063087'
    union all select '063013', '063027'
    union all select '063013', '063028'
    union all select '063013', '063029'
    union all select '063013', '063050'
    union all select '063015', '063040'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);

insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('063015' as varchar(50)) as comune_a, cast('063052' as varchar(50)) as comune_b
    union all select '063015', '064031'
    union all select '063016', '063023'
    union all select '063016', '063030'
    union all select '063016', '063032'
    union all select '063017', '063023'
    union all select '063017', '063056'
    union all select '063017', '063057'
    union all select '063017', '063072'
    union all select '063017', '063089'
    union all select '063018', '063028'
    union all select '063018', '063029'
    union all select '063018', '063050'
    union all select '063018', '063085'
    union all select '063018', '063088'
    union all select '063018', '064007'
    union all select '063019', '063031'
    union all select '063019', '063037'
    union all select '063019', '063038'
    union all select '063019', '063078'
    union all select '063020', '063036'
    union all select '063020', '063045'
    union all select '063020', '063073'
    union all select '063020', 'napoli'
    union all select '063021', '063023'
    union all select '063021', 'napoli'
    union all select '063022', '063035'
    union all select '063022', '063039'
    union all select '063023', '063032'
    union all select '063023', '063089'
    union all select '063023', 'napoli'
    union all select '063024', '063035'
    union all select '063024', '063054'
    union all select '063024', '063058'
    union all select '063024', '063083'
    union all select '063024', '063086'
    union all select '063024', '063090'
    union all select '063025', '063057'
    union all select '063025', '063079'
    union all select '063026', '063056'
    union all select '063026', '063070'
    union all select '063026', '063089'
    union all select '063026', '063092'
    union all select '063026', 'napoli'
    union all select '063027', '063029'
    union all select '063027', '063050'
    union all select '063027', '063065'
    union all select '063027', '063085'
    union all select '063028', '063029'
    union all select '063028', '063050'
    union all select '063029', '063085'
    union all select '063030', '063032'
    union all select '063030', '063033'
    union all select '063031', '063038'
    union all select '063031', '063078'
    union all select '063032', '063033'
    union all select '063032', '063036'
    union all select '063034', '063045'
    union all select '063034', '063048'
    union all select '063034', '063062'
    union all select '063034', '063063'
    union all select '063034', '063073'
    union all select '063034', '063087'
    union all select '063034', 'pozzuoli'
    union all select '063035', '063039'
    union all select '063035', '063054'
    union all select '063035', '063074'
    union all select '063035', '063090'
    union all select '063035', '065104'
    union all select '063035', '065138'
    union all select '063036', '063073'
    union all select '063039', '063074'
    union all select '063039', '065007'
    union all select '063039', '065047'
    union all select '063039', '065104'
    union all select '063039', '065151'
    union all select '063040', '063050'
    union all select '063040', '063052'
    union all select '063040', '063069'
    union all select '063040', '063088'
    union all select '063040', '064031'
    union all select '063040', '064047'
    union all select '063041', '063048'
    union all select '063041', '063063'
    union all select '063041', '063087'
    union all select '063041', 'napoli'
    union all select '063042', '063043'
    union all select '063042', '063079'
    union all select '063043', '063050'
    union all select '063043', '063075'
    union all select '063043', '063077'
    union all select '063043', '063079'
    union all select '063044', '063080'
    union all select '063045', '063048'
    union all select '063045', '063073'
    union all select '063045', 'napoli'
    union all select '063046', '063053'
    union all select '063046', '063086'
    union all select '063048', '063087'
    union all select '063048', 'napoli'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);

insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('063050' as varchar(50)) as comune_a, cast('063051' as varchar(50)) as comune_b
    union all select '063050', '063052'
    union all select '063050', '063065'
    union all select '063050', '063066'
    union all select '063050', '063069'
    union all select '063050', '063075'
    union all select '063050', '063076'
    union all select '063050', '063077'
    union all select '063050', '063079'
    union all select '063050', '063088'
    union all select '063051', '063064'
    union all select '063051', '063066'
    union all select '063051', '063068'
    union all select '063051', '063079'
    union all select '063051', '063082'
    union all select '063051', '063084'
    union all select '063052', '063055'
    union all select '063052', '063066'
    union all select '063052', '063068'
    union all select '063052', '063081'
    union all select '063052', '064043'
    union all select '063052', '065135'
    union all select '063053', '063071'
    union all select '063053', '063086'
    union all select '063054', '063086'
    union all select '063054', '065100'
    union all select '063055', '063068'
    union all select '063055', '063081'
    union all select '063055', '063082'
    union all select '063055', '065132'
    union all select '063055', '065137'
    union all select '063056', '063064'
    union all select '063056', '063072'
    union all select '063056', '063089'
    union all select '063056', '063092'
    union all select '063057', '063072'
    union all select '063057', '063079'
    union all select '063058', '063083'
    union all select '063058', '063090'
    union all select '063058', '065137'
    union all select '063062', '063087'
    union all select '063063', '063087'
    union all select '063063', 'napoli'
    union all select '063063', 'pozzuoli'
    union all select '063064', '063067'
    union all select '063064', '063070'
    union all select '063064', '063072'
    union all select '063064', '063079'
    union all select '063064', '063084'
    union all select '063064', '063092'
    union all select '063064', 'portici'
    union all select '063065', '063085'
    union all select '063065', '064007'
    union all select '063065', '064080'
    union all select '063066', '063068'
    union all select '063067', '063070'
    union all select '063067', 'napoli'
    union all select '063067', 'portici'
    union all select '063068', '063082'
    union all select '063070', '063092'
    union all select '063070', 'napoli'
    union all select '063071', '063080'
    union all select '063072', '063079'
    union all select '063074', '063090'
    union all select '063074', '065007'
    union all select '063074', '065137'
    union all select '063075', '063076'
    union all select '063075', '063077'
    union all select '063076', '063077'
    union all select '063076', '063079'
    union all select '063077', '063079'
    union all select '063081', '065132'
    union all select '063081', '065135'
    union all select '063083', '063084'
    union all select '063083', '063091'
    union all select '063084', '063091'
    union all select '063085', '064007'
    union all select '063086', '065100'
    union all select '063088', '064007'
    union all select '063088', '064010'
    union all select '063088', '064047'
    union all select '063088', '064054'
    union all select '063088', '064065'
    union all select '063088', '064068'
    union all select '063088', '064103'
    union all select '063088', '064106'
    union all select '063089', 'napoli'
    union all select '064001', '064006'
    union all select '064001', '064008'
    union all select '064001', '064026'
    union all select '064001', '064029'
    union all select '064001', '064084'
    union all select '064001', '064099'
    union all select '064001', '064101'
    union all select '064002', '064027'
    union all select '064002', '064039'
    union all select '064002', '064071'
    union all select '064002', '064073'
    union all select '064002', '064074'
    union all select '064002', '064091'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);

insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('064002' as varchar(50)) as comune_a, cast('064113' as varchar(50)) as comune_b
    union all select '064003', '064011'
    union all select '064003', '064013'
    union all select '064003', '064015'
    union all select '064003', '064030'
    union all select '064003', '064040'
    union all select '064003', '064063'
    union all select '064004', '064011'
    union all select '064004', '064015'
    union all select '064004', '064041'
    union all select '064004', '064060'
    union all select '064005', '064032'
    union all select '064005', '064037'
    union all select '064005', '064038'
    union all select '064005', '064048'
    union all select '064005', '064052'
    union all select '064005', '064096'
    union all select '064005', '064118'
    union all select '064005', '064120'
    union all select '064006', '064008'
    union all select '064006', '064026'
    union all select '064006', '064046'
    union all select '064006', '064086'
    union all select '064006', '064095'
    union all select '064006', '064102'
    union all select '064007', '064010'
    union all select '064007', '064025'
    union all select '064007', '064083'
    union all select '064007', '064100'
    union all select '064007', '064103'
    union all select '064008', '064018'
    union all select '064008', '064029'
    union all select '064008', '064039'
    union all select '064008', '064046'
    union all select '064008', '064049'
    union all select '064008', '064054'
    union all select '064008', '064055'
    union all select '064008', '064067'
    union all select '064008', '064105'
    union all select '064009', '064014'
    union all select '064009', '064044'
    union all select '064009', '064057'
    union all select '064009', '064066'
    union all select '064009', '065001'
    union all select '064010', '064065'
    union all select '064010', '064100'
    union all select '064010', '064103'
    union all select '064011', '064015'
    union all select '064011', '064040'
    union all select '064011', '064041'
    union all select '064011', '064097'
    union all select '064011', '064114'
    union all select '064012', '064038'
    union all select '064012', '064048'
    union all select '064012', '064050'
    union all select '064013', '064015'
    union all select '064013', '064030'
    union all select '064014', '064017'
    union all select '064014', '064098'
    union all select '064014', '065001'
    union all select '064014', '065155'
    union all select '064016', '064046'
    union all select '064016', '064053'
    union all select '064016', '064069'
    union all select '064016', '064075'
    union all select '064016', '064086'
    union all select '064017', '064030'
    union all select '064017', '064044'
    union all select '064017', '064108'
    union all select '064017', '065033'
    union all select '064017', '065063'
    union all select '064017', '065155'
    union all select '064018', '064039'
    union all select '064018', '064091'
    union all select '064018', '064105'
    union all select '064019', '064022'
    union all select '064019', '064035'
    union all select '064019', '064040'
    union all select '064019', '064104'
    union all select '064019', '064112'
    union all select '064019', '064114'
    union all select '064020', '064052'
    union all select '064021', '064057'
    union all select '064021', '064058'
    union all select '064021', '064066'
    union all select '064022', '064032'
    union all select '064022', '064085'
    union all select '064022', '064104'
    union all select '064023', '064058'
    union all select '064023', '064066'
    union all select '064023', '064070'
    union all select '064023', '064109'
    union all select '064024', '064028'
    union all select '064024', '064058'
    union all select '064024', '064070'
    union all select '064024', '064082'
    union all select '064024', '064119'
    union all select '064025', '064080'
    union all select '064025', '064083'
    union all select '064026', '064084'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);

insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('064026' as varchar(50)) as comune_a, cast('064095' as varchar(50)) as comune_b
    union all select '064027', '064071'
    union all select '064028', '064042'
    union all select '064028', '064069'
    union all select '064028', '064081'
    union all select '064028', '064082'
    union all select '064028', '064119'
    union all select '064029', '064034'
    union all select '064029', '064054'
    union all select '064029', '064101'
    union all select '064029', '064121'
    union all select '064030', '064063'
    union all select '064030', '064089'
    union all select '064030', '064108'
    union all select '064030', '065033'
    union all select '064031', '064043'
    union all select '064031', '064047'
    union all select '064031', '064068'
    union all select '064032', '064035'
    union all select '064032', '064038'
    union all select '064032', '064085'
    union all select '064032', '064087'
    union all select '064032', '064104'
    union all select '064032', '064118'
    union all select '064032', '064120'
    union all select '064033', '064036'
    union all select '064033', '064038'
    union all select '064033', '064045'
    union all select '064033', '064050'
    union all select '064033', '064070'
    union all select '064033', '064090'
    union all select '064034', '064054'
    union all select '064034', '064064'
    union all select '064034', '064077'
    union all select '064034', '064121'
    union all select '064034', '065016'
    union all select '064035', '064036'
    union all select '064035', '064038'
    union all select '064035', '064040'
    union all select '064035', '064079'
    union all select '064035', '064104'
    union all select '064035', '064117'
    union all select '064036', '064038'
    union all select '064036', '064070'
    union all select '064036', '064117'
    union all select '064037', '064051'
    union all select '064037', '064096'
    union all select '064038', '064048'
    union all select '064038', '064050'
    union all select '064039', '064055'
    union all select '064039', '064074'
    union all select '064039', '064091'
    union all select '064040', '064063'
    union all select '064040', '064079'
    union all select '064040', '064092'
    union all select '064040', '064114'
    union all select '064041', '064060'
    union all select '064041', '064097'
    union all select '064042', '064045'
    union all select '064042', '064053'
    union all select '064042', '064059'
    union all select '064042', '064069'
    union all select '064042', '064082'
    union all select '064042', '064107'
    union all select '064043', '064064'
    union all select '064043', '064068'
    union all select '064043', '064077'
    union all select '064043', '064106'
    union all select '064043', '065135'
    union all select '064044', '064063'
    union all select '064044', '064066'
    union all select '064044', '064092'
    union all select '064044', '064108'
    union all select '064045', '064070'
    union all select '064045', '064082'
    union all select '064045', '064090'
    union all select '064045', '064107'
    union all select '064046', '064055'
    union all select '064046', '064075'
    union all select '064046', '064086'
    union all select '064047', '064068'
    union all select '064049', '064054'
    union all select '064049', '064065'
    union all select '064049', '064067'
    union all select '064049', '064076'
    union all select '064049', '064105'
    union all select '064050', '064090'
    union all select '064050', '064107'
    union all select '064050', '064110'
    union all select '064050', '064116'
    union all select '064051', '064096'
    union all select '064053', '064059'
    union all select '064053', '064075'
    union all select '064054', '064064'
    union all select '064054', '064065'
    union all select '064054', '064106'
    union all select '064055', '064074'
    union all select '064055', '064075'
    union all select '064056', '064059'
    union all select '064056', '064072'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);

insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('064056' as varchar(50)) as comune_a, cast('064093' as varchar(50)) as comune_b
    union all select '064056', '064111'
    union all select '064057', '064058'
    union all select '064057', '064066'
    union all select '064057', '064099'
    union all select '064057', '064119'
    union all select '064057', '065001'
    union all select '064057', '065056'
    union all select '064058', '064066'
    union all select '064058', '064070'
    union all select '064058', '064119'
    union all select '064059', '064072'
    union all select '064059', '064074'
    union all select '064059', '064075'
    union all select '064059', '064093'
    union all select '064059', '064107'
    union all select '064059', '064110'
    union all select '064063', '064092'
    union all select '064063', '064108'
    union all select '064064', '064077'
    union all select '064064', '064106'
    union all select '064065', '064076'
    union all select '064065', '064100'
    union all select '064066', '064092'
    union all select '064066', '064109'
    union all select '064067', '064105'
    union all select '064068', '064106'
    union all select '064069', '064081'
    union all select '064069', '064086'
    union all select '064070', '064082'
    union all select '064070', '064109'
    union all select '064070', '064117'
    union all select '064071', '064111'
    union all select '064071', '064113'
    union all select '064072', '064110'
    union all select '064072', '064116'
    union all select '064073', '064078'
    union all select '064073', '064091'
    union all select '064073', '064105'
    union all select '064074', '064075'
    union all select '064074', '064093'
    union all select '064074', '064113'
    union all select '064076', '064100'
    union all select '064076', '064105'
    union all select '064077', '065016'
    union all select '064077', '065135'
    union all select '064077', '065142'
    union all select '064078', '064083'
    union all select '064079', '064092'
    union all select '064079', '064104'
    union all select '064079', '064117'
    union all select '064081', '064086'
    union all select '064081', '064102'
    union all select '064081', '064119'
    union all select '064084', '064088'
    union all select '064084', '064095'
    union all select '064084', '064099'
    union all select '064085', '064087'
    union all select '064085', '064112'
    union all select '064086', '064102'
    union all select '064087', '064112'
    union all select '064087', '064115'
    union all select '064087', '064120'
    union all select '064088', '064095'
    union all select '064088', '064099'
    union all select '064090', '064107'
    union all select '064091', '064105'
    union all select '064092', '064109'
    union all select '064092', '064117'
    union all select '064093', '064111'
    union all select '064093', '064113'
    union all select '064095', '064099'
    union all select '064095', '064102'
    union all select '064095', '064119'
    union all select '064097', '064112'
    union all select '064097', '064114'
    union all select '064097', '064115'
    union all select '064098', '065022'
    union all select '064098', '065083'
    union all select '064098', '065155'
    union all select '064099', '064101'
    union all select '064099', '064119'
    union all select '064099', '065020'
    union all select '064099', '065056'
    union all select '064100', '064103'
    union all select '064100', '064105'
    union all select '064101', '064121'
    union all select '064101', '065020'
    union all select '064102', '064119'
    union all select '064103', '064105'
    union all select '064107', '064110'
    union all select '064109', '064117'
    union all select '064110', '064116'
    union all select '064111', '064113'
    union all select '064112', '064114'
    union all select '064112', '064115'
    union all select '064118', '064120'
    union all select '064121', '065016'
    union all select '064121', '065020'
    union all select '064121', '065052'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);

insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('064121' as varchar(50)) as comune_a, cast('065067' as varchar(50)) as comune_b
    union all select '065001', '065022'
    union all select '065001', '065056'
    union all select '065001', '065073'
    union all select '065001', '065082'
    union all select '065002', '065025'
    union all select '065002', '065031'
    union all select '065002', '065042'
    union all select '065002', '065060'
    union all select '065002', '065081'
    union all select '065002', '065103'
    union all select '065002', '065147'
    union all select '065003', '065005'
    union all select '065003', '065025'
    union all select '065003', '065030'
    union all select '065003', '065050'
    union all select '065003', '065106'
    union all select '065003', '065140'
    union all select '065004', '065062'
    union all select '065004', '065107'
    union all select '065004', '065109'
    union all select '065005', '065030'
    union all select '065005', '065045'
    union all select '065005', '065101'
    union all select '065005', '065140'
    union all select '065006', '065011'
    union all select '065006', '065044'
    union all select '065006', '065053'
    union all select '065006', '065138'
    union all select '065007', '065047'
    union all select '065007', '065122'
    union all select '065007', '065130'
    union all select '065007', '065137'
    union all select '065008', '065015'
    union all select '065008', '065030'
    union all select '065008', '065035'
    union all select '065008', '065051'
    union all select '065008', '065086'
    union all select '065008', '065106'
    union all select '065009', '065028'
    union all select '065009', '065032'
    union all select '065009', '065040'
    union all select '065009', '065096'
    union all select '065009', '065124'
    union all select '065010', '065097'
    union all select '065010', '065114'
    union all select '065010', '065125'
    union all select '065010', '065129'
    union all select '065010', '065146'
    union all select '065011', '065104'
    union all select '065011', '065138'
    union all select '065012', '065017'
    union all select '065012', '065019'
    union all select '065012', '065048'
    union all select '065012', '065093'
    union all select '065012', '065094'
    union all select '065012', '065097'
    union all select '065012', '065117'
    union all select '065012', '065143'
    union all select '065013', '065036'
    union all select '065013', '065037'
    union all select '065013', '065052'
    union all select '065013', '065067'
    union all select '065013', '065090'
    union all select '065013', '065116'
    union all select '065014', '065050'
    union all select '065014', '065073'
    union all select '065014', '065082'
    union all select '065014', '065099'
    union all select '065014', '065158'
    union all select '065015', '065051'
    union all select '065015', '065061'
    union all select '065015', '065086'
    union all select '065015', '065111'
    union all select '065015', '065128'
    union all select '065016', '065067'
    union all select '065016', '065142'
    union all select '065017', '065043'
    union all select '065017', '065089'
    union all select '065017', '065110'
    union all select '065017', '065117'
    union all select '065017', '065120'
    union all select '065017', '065143'
    union all select '065018', '065076'
    union all select '065018', '065087'
    union all select '065018', '065133'
    union all select '065018', '065136'
    union all select '065019', '065093'
    union all select '065019', '065097'
    union all select '065019', '065117'
    union all select '065020', '065052'
    union all select '065020', '065055'
    union all select '065020', '065056'
    union all select '065021', '065038'
    union all select '065021', '065039'
    union all select '065021', '065107'
    union all select '065021', '065119'
    union all select '065022', '065046'
    union all select '065022', '065050'
    union all select '065022', '065082'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);

insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('065022' as varchar(50)) as comune_a, cast('065083' as varchar(50)) as comune_b
    union all select '065022', '065101'
    union all select '065022', '065140'
    union all select '065023', '065024'
    union all select '065023', '065057'
    union all select '065023', '065061'
    union all select '065023', '065069'
    union all select '065023', '065080'
    union all select '065023', '065145'
    union all select '065024', '065069'
    union all select '065024', '065080'
    union all select '065024', '065154'
    union all select '065025', '065042'
    union all select '065025', '065050'
    union all select '065025', '065058'
    union all select '065025', '065106'
    union all select '065025', '065152'
    union all select '065026', '065027'
    union all select '065026', '065076'
    union all select '065026', '065133'
    union all select '065027', '065029'
    union all select '065027', '065077'
    union all select '065027', '065133'
    union all select '065027', '065148'
    union all select '065027', '065150'
    union all select '065027', '065156'
    union all select '065028', '065032'
    union all select '065028', '065084'
    union all select '065028', '065098'
    union all select '065028', '065115'
    union all select '065028', '065144'
    union all select '065029', '065077'
    union all select '065029', '065109'
    union all select '065029', '065133'
    union all select '065029', '065149'
    union all select '065030', '065045'
    union all select '065030', '065086'
    union all select '065030', '065101'
    union all select '065030', '065106'
    union all select '065030', '065143'
    union all select '065031', '065060'
    union all select '065031', '065071'
    union all select '065031', '065091'
    union all select '065032', '065040'
    union all select '065032', '065115'
    union all select '065032', '065154'
    union all select '065033', '065063'
    union all select '065033', '065131'
    union all select '065034', '065067'
    union all select '065034', '065078'
    union all select '065034', '065108'
    union all select '065034', '065135'
    union all select '065034', '065142'
    union all select '065035', '065051'
    union all select '065035', '065106'
    union all select '065036', '065052'
    union all select '065036', '065055'
    union all select '065036', '065116'
    union all select '065036', '065118'
    union all select '065036', '065121'
    union all select '065037', '065066'
    union all select '065037', '065067'
    union all select '065037', '065079'
    union all select '065037', '065090'
    union all select '065037', '065108'
    union all select '065037', '065116'
    union all select '065037', '065151'
    union all select '065037', '065157'
    union all select '065038', '065039'
    union all select '065038', '065062'
    union all select '065038', '065070'
    union all select '065038', '065107'
    union all select '065039', '065070'
    union all select '065039', '065096'
    union all select '065039', '065124'
    union all select '065040', '065049'
    union all select '065040', '065054'
    union all select '065040', '065080'
    union all select '065040', '065124'
    union all select '065040', '065154'
    union all select '065041', '065066'
    union all select '065041', '065157'
    union all select '065042', '065058'
    union all select '065042', '065074'
    union all select '065042', '065081'
    union all select '065042', '065092'
    union all select '065042', '065103'
    union all select '065042', '065152'
    union all select '065043', '065046'
    union all select '065043', '065063'
    union all select '065043', '065083'
    union all select '065043', '065089'
    union all select '065043', '065120'
    union all select '065043', '065155'
    union all select '065044', '065053'
    union all select '065045', '065101'
    union all select '065046', '065083'
    union all select '065046', '065089'
    union all select '065046', '065101'
    union all select '065046', '065143'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);

insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('065047' as varchar(50)) as comune_a, cast('065130' as varchar(50)) as comune_b
    union all select '065047', '065151'
    union all select '065048', '065094'
    union all select '065048', '065097'
    union all select '065048', '065111'
    union all select '065048', '065113'
    union all select '065048', '065125'
    union all select '065048', '065126'
    union all select '065048', '065128'
    union all select '065048', '065129'
    union all select '065048', '065146'
    union all select '065049', '065054'
    union all select '065049', '065080'
    union all select '065050', '065082'
    union all select '065050', '065140'
    union all select '065051', '065061'
    union all select '065051', '065065'
    union all select '065051', '065074'
    union all select '065051', '065106'
    union all select '065052', '065055'
    union all select '065052', '065067'
    union all select '065053', '065102'
    union all select '065054', '065070'
    union all select '065054', '065080'
    union all select '065054', '065124'
    union all select '065055', '065056'
    union all select '065055', '065118'
    union all select '065056', '065072'
    union all select '065056', '065073'
    union all select '065056', '065099'
    union all select '065056', '065116'
    union all select '065056', '065118'
    union all select '065057', '065069'
    union all select '065057', '065085'
    union all select '065057', '065115'
    union all select '065057', '065145'
    union all select '065057', '065154'
    union all select '065058', '065152'
    union all select '065059', '065127'
    union all select '065059', '065156'
    union all select '065060', '065064'
    union all select '065060', '065091'
    union all select '065060', '065147'
    union all select '065061', '065065'
    union all select '065061', '065080'
    union all select '065061', '065095'
    union all select '065061', '065109'
    union all select '065061', '065111'
    union all select '065061', '065113'
    union all select '065061', '065145'
    union all select '065061', '065153'
    union all select '065062', '065070'
    union all select '065062', '065107'
    union all select '065062', '065109'
    union all select '065063', '065131'
    union all select '065063', '065155'
    union all select '065064', '065084'
    union all select '065064', '065091'
    union all select '065064', '065092'
    union all select '065064', '065112'
    union all select '065064', '065115'
    union all select '065064', '065141'
    union all select '065064', '065147'
    union all select '065065', '065074'
    union all select '065065', '065085'
    union all select '065065', '065145'
    union all select '065066', '065068'
    union all select '065066', '065104'
    union all select '065066', '065151'
    union all select '065066', '065157'
    union all select '065067', '065108'
    union all select '065067', '065142'
    union all select '065068', '065104'
    union all select '065069', '065154'
    union all select '065070', '065080'
    union all select '065070', '065109'
    union all select '065070', '065124'
    union all select '065071', '065091'
    union all select '065071', '065123'
    union all select '065071', '065139'
    union all select '065072', '065073'
    union all select '065072', '065099'
    union all select '065072', '065158'
    union all select '065073', '065082'
    union all select '065073', '065158'
    union all select '065074', '065085'
    union all select '065074', '065092'
    union all select '065074', '065106'
    union all select '065074', '065152'
    union all select '065075', '065095'
    union all select '065075', '065133'
    union all select '065075', '065136'
    union all select '065075', '065146'
    union all select '065076', '065087'
    union all select '065076', '065133'
    union all select '065077', '065127'
    union all select '065077', '065149'
    union all select '065077', '065150'
    union all select '065078', '065079'
    union all select '065078', '065088'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);

insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('065078' as varchar(50)) as comune_a, cast('065108' as varchar(50)) as comune_b
    union all select '065078', '065132'
    union all select '065078', '065135'
    union all select '065078', '065151'
    union all select '065079', '065108'
    union all select '065079', '065151'
    union all select '065080', '065109'
    union all select '065080', '065154'
    union all select '065081', '065103'
    union all select '065083', '065155'
    union all select '065084', '065115'
    union all select '065084', '065141'
    union all select '065084', '065144'
    union all select '065085', '065092'
    union all select '065085', '065115'
    union all select '065085', '065145'
    union all select '065086', '065094'
    union all select '065086', '065128'
    union all select '065086', '065143'
    union all select '065087', '065114'
    union all select '065087', '065136'
    union all select '065088', '065122'
    union all select '065088', '065130'
    union all select '065088', '065132'
    union all select '065088', '065151'
    union all select '065089', '065143'
    union all select '065090', '065116'
    union all select '065091', '065139'
    union all select '065091', '065141'
    union all select '065092', '065103'
    union all select '065092', '065112'
    union all select '065092', '065115'
    union all select '065093', '065097'
    union all select '065094', '065128'
    union all select '065094', '065143'
    union all select '065095', '065113'
    union all select '065095', '065133'
    union all select '065095', '065146'
    union all select '065095', '065153'
    union all select '065096', '065124'
    union all select '065097', '065129'
    union all select '065098', '065123'
    union all select '065098', '065141'
    union all select '065098', '065144'
    union all select '065099', '065116'
    union all select '065099', '065158'
    union all select '065100', '065102'
    union all select '065101', '065140'
    union all select '065101', '065143'
    union all select '065103', '065112'
    union all select '065103', '065147'
    union all select '065104', '065138'
    union all select '065104', '065151'
    union all select '065105', '065110'
    union all select '065105', '065120'
    union all select '065106', '065152'
    union all select '065107', '065109'
    union all select '065107', '065119'
    union all select '065107', '065149'
    union all select '065109', '065133'
    union all select '065109', '065149'
    union all select '065109', '065153'
    union all select '065110', '065117'
    union all select '065110', '065120'
    union all select '065111', '065113'
    union all select '065111', '065128'
    union all select '065112', '065147'
    union all select '065113', '065146'
    union all select '065114', '065136'
    union all select '065114', '065146'
    union all select '065115', '065154'
    union all select '065116', '065118'
    union all select '065116', '065121'
    union all select '065116', '065157'
    union all select '065118', '065121'
    union all select '065119', '065127'
    union all select '065119', '065149'
    union all select '065122', '065130'
    union all select '065122', '065132'
    union all select '065122', '065137'
    union all select '065123', '065139'
    union all select '065123', '065141'
    union all select '065125', '065126'
    union all select '065125', '065129'
    union all select '065125', '065146'
    union all select '065126', '065129'
    union all select '065126', '065146'
    union all select '065127', '065149'
    union all select '065127', '065150'
    union all select '065127', '065156'
    union all select '065129', '065146'
    union all select '065130', '065151'
    union all select '065132', '065135'
    union all select '065132', '065137'
    union all select '065133', '065136'
    union all select '065133', '065153'
    union all select '065134', '065148'
    union all select '065134', '065150'
    union all select '065134', '065156'
    union all select '065135', '065142'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);

insert into catalogo_adiacenze (comune_a, comune_b)
select v.comune_a, v.comune_b
from (
    select cast('065136' as varchar(50)) as comune_a, cast('065146' as varchar(50)) as comune_b
    union all select '065139', '065141'
    union all select '065141', '065144'
    union all select '065148', '065150'
    union all select '065148', '065156'
    union all select '065150', '065156'
    union all select 'napoli', 'portici'
    union all select 'napoli', 'pozzuoli'
) v
where not exists (select 1 from catalogo_adiacenze a where a.comune_a = v.comune_a and a.comune_b = v.comune_b);
