# Catalogo territoriale della Campania e adiacenze amministrative

Questo documento descrive le fonti ufficiali ISTAT, le convenzioni sui codici e la regola operativa adottata da PICUS per popolare l'intero territorio della Campania (`V27__catalogo_comuni_campania.sql` e `src/main/resources/static/dati/comuni-campania-istat-2026.json`).

## Fonti ufficiali e licenza

- **Edizione di riferimento**: **1° gennaio 2026** (coerente tra elenco amministrativo e confini geografici).
- **Elenco ufficiale dei comuni, province e città metropolitane**: [Codici statistici delle unità amministrative territoriali](https://www.istat.it/classificazione/codici-dei-comuni-delle-province-e-delle-regioni/) — permalink `Elenco-comuni-italiani.xlsx` (`550` comuni con codice regione `15`).
- **Confini amministrativi comunali**: [Confini delle unità amministrative a fini statistici](https://www.istat.it/notizia/confini-delle-unita-amministrative-a-fini-statistici-al-1-gennaio-2018-2/) — pacchetto generalizzato al 1° gennaio 2026 `Limiti01012026_g.zip` (`Com01012026_g_WGS84`, proiezione `WGS_1984_UTM_Zone_32N`).
- **Licenza**: ISTAT pubblica i dati con licenza [Creative Commons Attribuzione 4.0 Internazionale (CC BY 4.0)](https://www.istat.it/note-legali/).

## Codici ISTAT e raccordo con i codici storici

La colonna `codice_istat` in `catalogo_comuni` è memorizzata come stringa `varchar(6)` con vincolo di unicità e controllo sul formato `^[0-9]{6}$` per preservare gli zeri iniziali di tutti i `550` comuni campani (`061001`–`065158`).

Per mantenere integri tutti i riferimenti già salvati in `richieste`, `utenze_profili_opt`, `utenze_profili_operatore` e `catalogo_adiacenze` senza duplicare i comuni:

| Comune | Codice storico (`codice`) | Codice ISTAT (`codice_istat`) | Territorio sovracomunale |
|---|---|---|---|
| Napoli | `napoli` | `063049` | Città metropolitana di Napoli (`NA`) |
| Portici | `portici` | `063059` | Città metropolitana di Napoli (`NA`) |
| Pozzuoli | `pozzuoli` | `063060` | Città metropolitana di Napoli (`NA`) |
| Altri 547 comuni | `codice_istat` (es. `061005`, `063061`) | `codice_istat` | Provincia / Città metropolitana di appartenenza |

Le ricerche lato catalogo (`cercaComune`) accettano sia il codice storico (`napoli`, `portici`, `pozzuoli`) sia il codice ISTAT a 6 cifre (`063049`, `063059`, `063060`) risolvendolo sull'unico record canonico.

## Regola operativa delle adiacenze (`catalogo_adiacenze`)

Per la prima versione PICUS adotta l'**adiacenza amministrativa basata su tratto di confine condiviso**:

1. **Segmento condiviso**: due comuni campani sono registrati come limitrofi (`comune_a < comune_b`) se e solo se i rispettivi poligoni condividono almeno un lato di confine di lunghezza positiva (`1.508` coppie complessive nella Campania ISTAT 2026).
2. **Contatti in un solo punto**: i comuni che si toccano esclusivamente in un vertice puntiforme senza lato condiviso (`31` coppie in Campania, ad esempio `063058 Pompei` e `063074 Sant'Antonio Abate` oppure `061022 Caserta` e `061067 Recale`) **non** sono considerati limitrofi.
3. **Isole**:
   - I comuni insulari privi di confini terrestri condivisi non hanno righe in `catalogo_adiacenze` (`063061 Procida` ha `0` comuni limitrofi; eventuali opzioni a distanza del Centro PICUS restano disponibili quando la misura lo consente).
   - I comuni appartenenti a isole pluricomunali mantengono esclusivamente i confini terrestri interni all'isola (`Capri` ↔ `Anacapri`; i sei comuni dell'isola d'Ischia secondo i confini terrestri reciproci). Collegamenti marittimi o tempi di percorrenza sono eventuali estensioni future.
4. **Confine regionale**: il catalogo territoriale della versione corrente copre la Regione Campania; per i comuni lungo il confine regionale sono censite le adiacenze con gli altri comuni campani, mentre le estensioni extraregionali potranno essere aggiunte in un rilascio successivo.

## Rigenerazione degli artefatti

Lo script Python (solo libreria standard Python 3) scarica le fonti ufficiali ISTAT, verifica l'allineamento 1-a-1 tra elenco e shapefile, riproietta le geometrie in WGS84 (`EPSG:4326`) preservando l'orientamento sferico di `d3-geo` per poligoni, multipoligoni e buchi interni, e rigenera la migrazione SQL e il GeoJSON statico:

```bash
python scripts/genera_catalogo_campania.py --cache-dir ../work/istat-cache
```
