#!/usr/bin/env python3
"""Genera migrazione Flyway V27 e GeoJSON della Campania da fonti ufficiali ISTAT 2026."""

from __future__ import annotations

import argparse
from collections import defaultdict
import io
import json
import math
from pathlib import Path
import struct
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

URL_ELENCO_ISTAT = (
    "https://www.istat.it/storage/codici-unita-amministrative/Elenco-comuni-italiani.xlsx"
)
URL_CONFINI_ISTAT = (
    "https://www.istat.it/storage/cartografia/confini_amministrativi/generalizzati/2026/Limiti01012026_g.zip"
)
CODICE_REGIONE_CAMPANIA = "15"
CODICI_STORICI = {
    "063049": "napoli",
    "063059": "portici",
    "063060": "pozzuoli",
}
DECIMALI_WGS84 = 5


def scarica_o_leggi(url: str, cache_dir: Path | None, nome_file: str) -> bytes:
    if cache_dir is not None:
        cache_dir.mkdir(parents=True, exist_ok=True)
        locale = cache_dir / nome_file
        if locale.exists() and locale.stat().st_size > 0:
            return locale.read_bytes()
    richiesta = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (PICUS)"})
    with urllib.request.urlopen(richiesta, timeout=60) as risposta:
        dati = risposta.read()
    if cache_dir is not None:
        (cache_dir / nome_file).write_bytes(dati)
    return dati


def indice_colonna(riferimento: str) -> int:
    indice = 0
    for ch in riferimento:
        if ch.isalpha():
            indice = indice * 26 + (ord(ch.upper()) - 64)
        else:
            break
    return indice - 1


def leggi_elenco_campania_xlsx(dati_xlsx: bytes) -> dict[str, dict]:
    ns_uri = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    ns = {"m": ns_uri}
    zf = zipfile.ZipFile(io.BytesIO(dati_xlsx))
    shared_strings: list[str] = []
    if "xl/sharedStrings.xml" in zf.namelist():
        radice_sst = ET.fromstring(zf.read("xl/sharedStrings.xml"))
        for si in radice_sst.findall("m:si", ns):
            shared_strings.append(
                "".join(t.text or "" for t in si.iter(f"{{{ns_uri}}}t"))
            )

    foglio = ET.fromstring(zf.read("xl/worksheets/sheet1.xml"))
    righe: list[list[str]] = []
    for riga in foglio.findall(".//m:sheetData/m:row", ns):
        celle: dict[int, str] = {}
        for cella in riga.findall("m:c", ns):
            ci = indice_colonna(cella.attrib["r"])
            tipo = cella.attrib.get("t")
            valore = cella.find("m:v", ns)
            if valore is not None and valore.text is not None:
                celle[ci] = (
                    shared_strings[int(valore.text)] if tipo == "s" else valore.text
                )
            else:
                inline = cella.find("m:is", ns)
                if inline is not None:
                    celle[ci] = "".join(t.text or "" for t in inline.iter(f"{{{ns_uri}}}t"))
                else:
                    celle[ci] = ""
        if celle:
            massimo = max(celle)
            righe.append([celle.get(i, "") for i in range(massimo + 1)])

    intestazione = righe[0]
    col_reg = intestazione.index("Codice Regione")
    col_cod = intestazione.index("Codice Comune formato alfanumerico")
    col_nome = intestazione.index("Denominazione in italiano")
    col_sigla = intestazione.index("Sigla automobilistica")
    col_uts = next(
        i for i, c in enumerate(intestazione) if c.startswith("Denominazione dell'Unità territoriale")
    )
    col_tipo_uts = next(
        i for i, c in enumerate(intestazione) if c.startswith("Tipologia di Unità territoriale")
    )

    comuni: dict[str, dict] = {}
    for riga in righe[1:]:
        if len(riga) <= max(col_reg, col_cod, col_nome, col_sigla, col_uts, col_tipo_uts):
            continue
        if riga[col_reg].strip().lstrip("0") != CODICE_REGIONE_CAMPANIA:
            continue
        codice_istat = riga[col_cod].strip().zfill(6)
        nome = riga[col_nome].strip()
        provincia = riga[col_uts].strip()
        sigla = riga[col_sigla].strip().upper()
        # Codice ISTAT tipologia UTS = 3 per le città metropolitane; 1 per le province.
        citta_metropolitana = riga[col_tipo_uts].strip() == "3" or sigla == "NA"
        comuni[codice_istat] = {
            "codiceCatalogo": CODICI_STORICI.get(codice_istat, codice_istat),
            "codiceIstat": codice_istat,
            "nome": nome,
            "provincia": provincia,
            "siglaProvincia": sigla,
            "cittaMetropolitana": citta_metropolitana,
        }
    return comuni


def utm32n_to_wgs84(easting: float, northing: float) -> tuple[float, float]:
    """Converte coordinate WGS 84 / UTM zona 32N (EPSG:32632) in gradi WGS 84 (lon, lat)."""
    a = 6378137.0
    f = 1.0 / 298.257223563
    e2 = f * (2.0 - f)
    e1 = (1.0 - math.sqrt(1.0 - e2)) / (1.0 + math.sqrt(1.0 - e2))
    ep2 = e2 / (1.0 - e2)
    k0 = 0.9996
    lon0 = math.radians(9.0)

    x = easting - 500000.0
    y = northing
    m = y / k0
    mu = m / (a * (1.0 - e2 / 4.0 - 3.0 * e2 * e2 / 64.0 - 5.0 * e2**3 / 256.0))

    phi1 = (
        mu
        + (3.0 * e1 / 2.0 - 27.0 * e1**3 / 32.0) * math.sin(2.0 * mu)
        + (21.0 * e1**2 / 16.0 - 55.0 * e1**4 / 32.0) * math.sin(4.0 * mu)
        + (151.0 * e1**3 / 96.0) * math.sin(6.0 * mu)
        + (1097.0 * e1**4 / 512.0) * math.sin(8.0 * mu)
    )

    sin_p = math.sin(phi1)
    cos_p = math.cos(phi1)
    tan_p = math.tan(phi1)
    c1 = ep2 * cos_p * cos_p
    t1 = tan_p * tan_p
    n1 = a / math.sqrt(1.0 - e2 * sin_p * sin_p)
    r1 = a * (1.0 - e2) / math.pow(1.0 - e2 * sin_p * sin_p, 1.5)
    d = x / (n1 * k0)

    lat = phi1 - (n1 * tan_p / r1) * (
        d * d / 2.0
        - (5.0 + 3.0 * t1 + 10.0 * c1 - 4.0 * c1 * c1 - 9.0 * ep2) * (d**4) / 24.0
        + (61.0 + 90.0 * t1 + 298.0 * c1 + 45.0 * t1 * t1 - 252.0 * ep2 - 3.0 * c1 * c1)
        * (d**6)
        / 720.0
    )
    lon = lon0 + (
        d
        - (1.0 + 2.0 * t1 + c1) * (d**3) / 6.0
        + (5.0 - 2.0 * c1 + 28.0 * t1 - 3.0 * c1 * c1 + 8.0 * ep2 + 24.0 * t1 * t1)
        * (d**5)
        / 120.0
    ) / cos_p

    return (
        round(math.degrees(lon), DECIMALI_WGS84),
        round(math.degrees(lat), DECIMALI_WGS84),
    )


def area_firmata(anello: list[tuple[float, float]]) -> float:
    totale = 0.0
    for (x1, y1), (x2, y2) in zip(anello, anello[1:]):
        totale += x1 * y2 - x2 * y1
    return 0.5 * totale


def punto_in_anello(punto: tuple[float, float], anello: list[tuple[float, float]]) -> bool:
    px, py = punto
    dentro = False
    for (x1, y1), (x2, y2) in zip(anello, anello[1:]):
        if (y1 > py) != (y2 > py):
            inter = (x2 - x1) * (py - y1) / (y2 - y1) + x1
            if px < inter:
                dentro = not dentro
    return dentro


def normalizza_anello_wgs84(
    punti_utm: list[tuple[float, float]], orario: bool
) -> list[list[float]] | None:
    convertiti = [utm32n_to_wgs84(x, y) for x, y in punti_utm]
    puliti: list[tuple[float, float]] = []
    for pt in convertiti:
        if not puliti or puliti[-1] != pt:
            puliti.append(pt)
    if len(puliti) >= 2 and puliti[0] != puliti[-1]:
        puliti.append(puliti[0])
    if len(puliti) < 4:
        return None
    area = area_firmata(puliti)
    if abs(area) == 0.0:
        return None
    # d3-geo richiede anelli esterni orari (area < 0) e buchi antiorari (area > 0).
    atteso_orario = area < 0.0
    if atteso_orario != orario:
        puliti.reverse()
    return [[x, y] for x, y in puliti]


def costruisci_geometria(anelli_utm: list[list[tuple[float, float]]]) -> dict:
    esterni: list[tuple[float, list[tuple[float, float]], list[list[float]]]] = []
    buchi: list[tuple[list[tuple[float, float]], list[list[float]]]] = []

    for ring_utm in anelli_utm:
        area_utm = area_firmata(ring_utm)
        if area_utm <= 0.0:
            wgs = normalizza_anello_wgs84(ring_utm, orario=True)
            if wgs is not None:
                esterni.append((abs(area_utm), ring_utm, wgs))
        else:
            wgs = normalizza_anello_wgs84(ring_utm, orario=False)
            if wgs is not None:
                buchi.append((ring_utm, wgs))

    if not esterni and anelli_utm:
        for ring_utm in anelli_utm:
            wgs = normalizza_anello_wgs84(ring_utm, orario=True)
            if wgs is not None:
                esterni.append((abs(area_firmata(ring_utm)), ring_utm, wgs))

    poligoni: list[list[list[list[float]]]] = [[ext[2]] for ext in esterni]
    for hole_utm, hole_wgs in buchi:
        punto = hole_utm[0]
        contenitori = [
            (area_ext, idx)
            for idx, (area_ext, ext_utm, _) in enumerate(esterni)
            if punto_in_anello(punto, ext_utm)
        ]
        if contenitori:
            _, idx_min = min(contenitori, key=lambda coppia: coppia[0])
            poligoni[idx_min].append(hole_wgs)

    if len(poligoni) == 1:
        return {"type": "Polygon", "coordinates": poligoni[0]}
    return {"type": "MultiPolygon", "coordinates": poligoni}


def leggi_confini_campania(dati_zip: bytes):
    zf = zipfile.ZipFile(io.BytesIO(dati_zip))
    dbf_raw = zf.read("Com01012026_g/Com01012026_g_WGS84.dbf")
    shp_raw = zf.read("Com01012026_g/Com01012026_g_WGS84.shp")
    shx_raw = zf.read("Com01012026_g/Com01012026_g_WGS84.shx")

    num_rec, header_len, rec_len = struct.unpack("<IHH", dbf_raw[4:12])
    campi = []
    pos = 32
    while dbf_raw[pos] != 0x0D:
        campi.append(
            (dbf_raw[pos : pos + 11].split(b"\x00", 1)[0].decode("ascii"), dbf_raw[pos + 16])
        )
        pos += 32

    geometrie_utm: dict[str, tuple[str, list[list[tuple[float, float]]]]] = {}
    for idx in range(num_rec):
        if dbf_raw[header_len + idx * rec_len] != 0x20:
            continue
        doff = header_len + idx * rec_len + 1
        rec: dict[str, str] = {}
        for fn, fl in campi:
            rec[fn] = dbf_raw[doff : doff + fl].decode("utf-8").strip()
            doff += fl
        if rec["COD_REG"] != CODICE_REGIONE_CAMPANIA:
            continue

        soff = struct.unpack(">I", shx_raw[100 + idx * 8 : 104 + idx * 8])[0] * 2
        num_parts, num_pts = struct.unpack("<II", shp_raw[soff + 44 : soff + 52])
        parts = list(
            struct.unpack(f"<{num_parts}I", shp_raw[soff + 52 : soff + 52 + 4 * num_parts])
        ) + [num_pts]
        pts_off = soff + 52 + 4 * num_parts
        punti = [
            struct.unpack("<dd", shp_raw[pts_off + i * 16 : pts_off + (i + 1) * 16])
            for i in range(num_pts)
        ]
        anelli = [punti[parts[p] : parts[p + 1]] for p in range(num_parts)]
        geometrie_utm[rec["PRO_COM_T"]] = (rec["COMUNE"], anelli)

    return geometrie_utm


def calcola_adiacenze(
    comuni: dict[str, dict], geometrie_utm: dict[str, tuple[str, list[list[tuple[float, float]]]]]
) -> list[tuple[str, str]]:
    segmenti: dict[tuple[tuple[float, float], tuple[float, float]], set[str]] = defaultdict(set)
    for codice_istat, (_, anelli) in geometrie_utm.items():
        codice_catalogo = comuni[codice_istat]["codiceCatalogo"]
        for anello in anelli:
            arrotondati = [(round(x, 3), round(y, 3)) for x, y in anello]
            for a, b in zip(arrotondati, arrotondati[1:]):
                if a != b:
                    segmenti[(min(a, b), max(a, b))].add(codice_catalogo)

    coppie: set[tuple[str, str]] = set()
    for proprietari in segmenti.values():
        if len(proprietari) > 1:
            ordinati = sorted(proprietari)
            for i in range(len(ordinati)):
                for j in range(i + 1, len(ordinati)):
                    coppie.add((ordinati[i], ordinati[j]))
    return sorted(coppie)


def sql_str(valore: str) -> str:
    return "'" + valore.replace("'", "''") + "'"


def genera_sql_migrazione(
    comuni: dict[str, dict], adiacenze: list[tuple[str, str]]
) -> str:
    righe: list[str] = [
        "-- Catalogo completo dei comuni della Campania e relative adiacenze amministrative (#251).",
        "-- Fonte: ISTAT 1° gennaio 2026 (Elenco comuni italiani e Confini amministrativi Limiti01012026_g, licenza CC BY 4.0).",
        "-- Generato con: python scripts/genera_catalogo_campania.py",
        "",
        "alter table catalogo_comuni add column codice_istat varchar(6);",
        "alter table catalogo_comuni add column provincia varchar(100);",
        "alter table catalogo_comuni add column sigla_provincia varchar(2);",
        "alter table catalogo_comuni add column citta_metropolitana boolean not null default false;",
        "",
        "alter table catalogo_comuni add constraint catalogo_comuni_codice_istat_uk unique (codice_istat);",
        "alter table catalogo_comuni add constraint catalogo_comuni_codice_istat_chk",
        "    check (codice_istat is null or (length(codice_istat) = 6 and regexp_like(codice_istat, '^[0-9]{6}$')));",
        "alter table catalogo_comuni add constraint catalogo_comuni_sigla_provincia_chk",
        "    check (sigla_provincia is null or (length(sigla_provincia) = 2 and regexp_like(sigla_provincia, '^[A-Z]{2}$')));",
        "",
        "-- Collega i codici storici esistenti al codice ISTAT ufficiale senza duplicare i comuni.",
    ]

    for codice_istat, codice_storico in sorted(CODICI_STORICI.items()):
        c = comuni[codice_istat]
        righe.append(
            f"update catalogo_comuni set nome = {sql_str(c['nome'])}, "
            f"codice_istat = {sql_str(c['codiceIstat'])}, "
            f"provincia = {sql_str(c['provincia'])}, "
            f"sigla_provincia = {sql_str(c['siglaProvincia'])}, "
            f"citta_metropolitana = {'true' if c['cittaMetropolitana'] else 'false'} "
            f"where codice = {sql_str(codice_storico)};"
        )

    righe.append("")
    ordinati = sorted(comuni.values(), key=lambda c: c["codiceIstat"])
    for inizio in range(0, len(ordinati), 50):
        blocco = ordinati[inizio : inizio + 50]
        righe.append(
            "insert into catalogo_comuni (codice, nome, codice_istat, provincia, sigla_provincia, citta_metropolitana)"
        )
        righe.append(
            "select v.codice, v.nome, v.codice_istat, v.provincia, v.sigla_provincia, v.citta_metropolitana"
        )
        righe.append("from (")
        for idx, c in enumerate(blocco):
            prefisso = "    " if idx == 0 else "    union all "
            cm = "true" if c["cittaMetropolitana"] else "false"
            if idx == 0:
                righe.append(
                    f"{prefisso}select cast({sql_str(c['codiceCatalogo'])} as varchar(50)) as codice, "
                    f"cast({sql_str(c['nome'])} as varchar(255)) as nome, "
                    f"cast({sql_str(c['codiceIstat'])} as varchar(6)) as codice_istat, "
                    f"cast({sql_str(c['provincia'])} as varchar(100)) as provincia, "
                    f"cast({sql_str(c['siglaProvincia'])} as varchar(2)) as sigla_provincia, "
                    f"{cm} as citta_metropolitana"
                )
            else:
                righe.append(
                    f"{prefisso}select {sql_str(c['codiceCatalogo'])}, "
                    f"{sql_str(c['nome'])}, "
                    f"{sql_str(c['codiceIstat'])}, "
                    f"{sql_str(c['provincia'])}, "
                    f"{sql_str(c['siglaProvincia'])}, "
                    f"{cm}"
                )
        righe.append(") v")
        righe.append(
            "where not exists (select 1 from catalogo_comuni c "
            "where c.codice = v.codice or c.codice_istat = v.codice_istat);"
        )
        righe.append("")

    righe.append("-- Adiacenze amministrative basate su tratto di confine condiviso (comune_a < comune_b).")
    for inizio in range(0, len(adiacenze), 100):
        blocco = adiacenze[inizio : inizio + 100]
        righe.append("insert into catalogo_adiacenze (comune_a, comune_b)")
        righe.append("select v.comune_a, v.comune_b")
        righe.append("from (")
        for idx, (a, b) in enumerate(blocco):
            prefisso = "    " if idx == 0 else "    union all "
            if idx == 0:
                righe.append(
                    f"{prefisso}select cast({sql_str(a)} as varchar(50)) as comune_a, "
                    f"cast({sql_str(b)} as varchar(50)) as comune_b"
                )
            else:
                righe.append(f"{prefisso}select {sql_str(a)}, {sql_str(b)}")
        righe.append(") v")
        righe.append(
            "where not exists (select 1 from catalogo_adiacenze a "
            "where a.comune_a = v.comune_a and a.comune_b = v.comune_b);"
        )
        righe.append("")

    return "\n".join(righe)


def genera_geojson(
    comuni: dict[str, dict], geometrie_utm: dict[str, tuple[str, list[list[tuple[float, float]]]]]
) -> dict:
    features = []
    for codice_istat in sorted(comuni):
        c = comuni[codice_istat]
        _, anelli_utm = geometrie_utm[codice_istat]
        features.append(
            {
                "type": "Feature",
                "properties": {
                    "codiceIstat": c["codiceIstat"],
                    "codiceCatalogo": c["codiceCatalogo"],
                    "nome": c["nome"],
                    "provincia": c["provincia"],
                    "siglaProvincia": c["siglaProvincia"],
                    "cittaMetropolitana": c["cittaMetropolitana"],
                },
                "geometry": costruisci_geometria(anelli_utm),
            }
        )

    return {
        "type": "FeatureCollection",
        "metadata": {
            "fonte": "ISTAT - Confini delle unita amministrative e Elenco comuni italiani",
            "edizione": "2026-01-01",
            "licenza": "CC BY 4.0 (https://www.istat.it/note-legali/)",
            "regioneCodice": "15",
            "regioneNome": "Campania",
            "totaleComuni": len(features),
        },
        "features": features,
    }


def main() -> int:
    radice = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--cache-dir",
        type=Path,
        default=None,
        help="Cartella opzionale fuori dal repository in cui conservare i sorgenti ISTAT scaricati.",
    )
    parser.add_argument(
        "--sql-out",
        type=Path,
        default=radice / "src/main/resources/db/migration/V27__catalogo_comuni_campania.sql",
    )
    parser.add_argument(
        "--geojson-out",
        type=Path,
        default=radice / "src/main/resources/static/dati/comuni-campania-istat-2026.json",
    )
    argomenti = parser.parse_args()

    dati_xlsx = scarica_o_leggi(URL_ELENCO_ISTAT, argomenti.cache_dir, "Elenco-comuni-italiani.xlsx")
    dati_zip = scarica_o_leggi(URL_CONFINI_ISTAT, argomenti.cache_dir, "Limiti01012026_g.zip")

    comuni = leggi_elenco_campania_xlsx(dati_xlsx)
    geometrie_utm = leggi_confini_campania(dati_zip)

    if set(comuni) != set(geometrie_utm):
        raise SystemExit(
            f"Disallineamento codici ISTAT tra elenco e confini: {set(comuni) ^ set(geometrie_utm)}"
        )
    disallineamenti_nomi = [
        (k, comuni[k]["nome"], geometrie_utm[k][0])
        for k in sorted(comuni)
        if comuni[k]["nome"] != geometrie_utm[k][0]
    ]
    if disallineamenti_nomi:
        raise SystemExit(f"Disallineamento denominazioni ISTAT: {disallineamenti_nomi}")

    adiacenze = calcola_adiacenze(comuni, geometrie_utm)
    sql = genera_sql_migrazione(comuni, adiacenze)
    geojson = genera_geojson(comuni, geometrie_utm)

    argomenti.sql_out.parent.mkdir(parents=True, exist_ok=True)
    argomenti.sql_out.write_text(sql, encoding="utf-8", newline="\n")

    argomenti.geojson_out.parent.mkdir(parents=True, exist_ok=True)
    argomenti.geojson_out.write_text(
        json.dumps(geojson, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
        newline="\n",
    )

    print(
        f"Generati {len(comuni)} comuni, {len(adiacenze)} adiacenze -> "
        f"{argomenti.sql_out} ({argomenti.sql_out.stat().st_size} B) e "
        f"{argomenti.geojson_out} ({argomenti.geojson_out.stat().st_size} B)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
