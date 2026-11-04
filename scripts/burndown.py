#!/usr/bin/env python3
"""Burndown di uno sprint da un GitHub Project (v2), pesato con la taglia dei task.

Legge gli item della iterazione scelta, calcola i punti rimanenti giorno per giorno
(scope - completati) e scrive un CSV e un grafico SVG con la linea ideale.
Esclude Pull Request, issue di tipo nativo Epic e contenuti non disponibili.
L'appartenenza al Project e all'iterazione non viene modificata.
Solo libreria standard: nessuna dipendenza da installare nella CI.

Variabili d'ambiente (le prime tre servono, le altre hanno un default):
  PROJECT_TOKEN     token personale classico con scope `project` e `repo`
  PROJECT_OWNER     login proprietario del project (es. Pinguini-alla-Fragola)
  PROJECT_NUMBER    numero del project (dall'URL .../projects/<numero>)
  OWNER_TYPE        user | organization                      (default: user)
  ITERATION_TITLE   titolo dell'iterazione; vuoto = quella in corso oggi
  ITERATION_FIELD   nome del campo iterazione                (default: Iteration)
  STATUS_FIELD      nome del campo stato                     (default: Status)
  DONE_VALUE        valore di stato che vale «fatto»         (default: Done)
  POINTS_FIELD      campo Number con i punti                 (default: Estimate)
  SIZE_FIELD        campo Size (selezione singola)           (default: Size)
  OUT_DIR           cartella di output                       (default: docs/burndown)

Il grafico ha riquadri di stato, previsione a fine sprint e area colorata (rosa = in ritardo,
azzurra = in anticipo rispetto alla linea ideale).
Scrive solo l'iterazione in corso (e la copia `burndown-latest.svg` per il README): i file degli
sprint conclusi restano com'erano, così i task spostati nello sprint successivo non alterano la storia.

I punti di un task: campo Number se presente, altrimenti mappatura della taglia
(PUNTI_PER_TAGLIA qui sotto), altrimenti 1.  Prova senza API:  python burndown.py --demo
Controlla i nomi dei campi sul project reale:  python burndown.py --inspect
"""
import csv
import html
import json
import os
import re
import sys
import urllib.request
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

PUNTI_PER_TAGLIA = {"XS": 1, "S": 2, "M": 3, "L": 5, "XL": 8}   # mappatura da concordare nel team
# Calendario del progetto PICUS (presentazione, slide 11-12): data di inizio -> durata in giorni.
# La preparatoria dura 3 settimane (eccezione); le tre iterazioni di costruzione 2 settimane.
PIANO_SPRINT = {date(2026, 10, 15): 21, date(2026, 11, 5): 14, date(2026, 11, 19): 14, date(2026, 12, 3): 14}
UTC = timezone.utc

# TIPO_PROPRIETARIO diventa "user" o "organization"
QUERY = """
query($login:String!, $num:Int!, $after:String) {
  TIPO_PROPRIETARIO(login:$login) {
    projectV2(number:$num) {
      title
      items(first:100, after:$after) {
        pageInfo { hasNextPage endCursor }
        nodes {
          createdAt updatedAt
          content {
            __typename
            ... on Issue { number title closedAt issueType { name } }
            ... on PullRequest { number title closedAt }
            ... on DraftIssue { title }
          }
          fieldValues(first:30) {
            nodes {
              __typename
              ... on ProjectV2ItemFieldSingleSelectValue { name field { ... on ProjectV2FieldCommon { name } } }
              ... on ProjectV2ItemFieldNumberValue { number field { ... on ProjectV2FieldCommon { name } } }
              ... on ProjectV2ItemFieldIterationValue { title startDate duration field { ... on ProjectV2FieldCommon { name } } }
            }
          }
        }
      }
    }
  }
}
"""


def scarica_righe(token, tipo_proprietario, login, numero):
    tipo = "organization" if tipo_proprietario == "organization" else "user"
    query = QUERY.replace("TIPO_PROPRIETARIO", tipo)
    righe, cursore = [], None
    while True:
        corpo = json.dumps({"query": query, "variables": {"login": login, "num": numero, "after": cursore}}).encode()
        richiesta = urllib.request.Request("https://api.github.com/graphql", data=corpo, headers={
            "Authorization": f"Bearer {token}", "Content-Type": "application/json"})
        with urllib.request.urlopen(richiesta) as risposta:
            dati = json.load(risposta)
        if "errors" in dati:
            raise SystemExit(f"Errore GraphQL: {dati['errors']}")
        project = dati["data"][tipo]["projectV2"]
        righe += project["items"]["nodes"]
        if not project["items"]["pageInfo"]["hasNextPage"]:
            return project["title"], righe
        cursore = project["items"]["pageInfo"]["endCursor"]


def leggi_data(testo):
    return datetime.fromisoformat(testo.replace("Z", "+00:00")) if testo else None


def motivo_esclusione(contenuto):
    """Classifica il contenuto con i tipi GitHub, senza dedurli dal titolo."""
    if not contenuto:
        return "Contenuto non disponibile"
    if contenuto.get("__typename") == "PullRequest":
        return "Pull Request"
    tipo_issue = (contenuto.get("issueType") or {}).get("name", "")
    if contenuto.get("__typename") == "Issue" and tipo_issue.strip().casefold() == "epic":
        return "Epic"
    return None


def normalizza(righe, config):
    """Normalizza gli item; mantiene i metadati degli esclusi per scegliere l'iterazione."""
    elementi = []
    for riga in righe:
        campi = {"iter": None, "stato": None, "punti": None, "taglia": None}
        for valore in riga["fieldValues"]["nodes"]:
            nome = (valore.get("field") or {}).get("name")
            if valore["__typename"] == "ProjectV2ItemFieldIterationValue" and nome == config["iter_field"]:
                campi["iter"] = valore
            elif valore["__typename"] == "ProjectV2ItemFieldSingleSelectValue":
                if nome == config["status_field"]:
                    campi["stato"] = valore["name"]
                elif nome == config["size_field"]:
                    campi["taglia"] = valore["name"]
            elif valore["__typename"] == "ProjectV2ItemFieldNumberValue" and nome == config["points_field"]:
                campi["punti"] = valore["number"]
        contenuto = riga["content"] or {}
        punti = campi["punti"] if campi["punti"] is not None else PUNTI_PER_TAGLIA.get(campi["taglia"], 1)
        fatto = campi["stato"] == config["done_value"]
        # data di completamento: chiusura dell'issue se c'è, altrimenti ultimo aggiornamento
        fatto_il = (leggi_data(contenuto.get("closedAt")) or leggi_data(riga["updatedAt"])) if fatto else None
        elementi.append({"title": contenuto.get("title", "?"), "iter": campi["iter"], "points": float(punti),
                         "created": leggi_data(riga["createdAt"]), "done_at": fatto_il,
                         "excluded": motivo_esclusione(contenuto)})
    return elementi


def scegli_iterazione(elementi, titolo, oggi):
    iterazioni = {}
    for el in elementi:
        it = el["iter"]
        if it:
            iterazioni[it["title"]] = (date.fromisoformat(it["startDate"]), int(it["duration"]))
    if titolo:
        if titolo not in iterazioni:
            raise SystemExit(f"Iterazione «{titolo}» non trovata. Disponibili: {sorted(iterazioni)}")
        return titolo, *iterazioni[titolo]
    if not iterazioni:
        raise SystemExit("Nessuna iterazione trovata negli item del Project.")
    for t, (inizio, durata) in iterazioni.items():
        if inizio <= oggi < inizio + timedelta(days=durata):
            return t, inizio, durata
    t = max(iterazioni, key=lambda k: iterazioni[k][0])      # nessuna in corso: l'ultima
    return t, *iterazioni[t]


def calcola(elementi, titolo, inizio, giorni, adesso):
    """Serie (giorni dall'inizio, scope, completati, rimanenti) a ogni mezzanotte e adesso."""
    miei = [e for e in elementi if not e.get("excluded") and e["iter"] and e["iter"]["title"] == titolo]
    t0 = datetime.combine(inizio, datetime.min.time(), UTC)
    istanti = [t0 + timedelta(days=k) for k in range(giorni + 1) if t0 + timedelta(days=k) <= adesso]
    if adesso < t0 + timedelta(days=giorni):
        istanti.append(adesso)                      # punto «adesso» se lo sprint è in corso
    # i task aggiunti al project nel primo giorno (planning) fanno parte dello scope iniziale:
    # solo quelli aggiunti dopo sono «aumento di perimetro»
    tolleranza = t0 + timedelta(days=1)
    aggiunto = lambda e: t0 if e["created"] < tolleranza else e["created"]
    righe = []
    for t in istanti:
        scope = sum(e["points"] for e in miei if aggiunto(e) <= t)
        fatti = sum(e["points"] for e in miei if e["done_at"] and e["done_at"] <= t)
        righe.append(((t - t0).total_seconds() / 86400, scope, fatti, scope - fatti))
    base = righe[0][1] if righe and righe[0][1] > 0 else sum(e["points"] for e in miei)
    return miei, base, righe


def _passo_griglia(massimo):
    for passo in (1, 2, 5, 10, 20, 50, 100):
        if massimo / passo <= 8:
            return passo
    return 100


def disegna_svg(titolo, inizio, giorni, base, righe, adesso):
    """Grafico in stile dashboard (riquadri di stato, previsione) con l'area colorata tra reale e ideale."""
    W, H = 960, 600
    giorno_ora = righe[-1][0] if righe else 0.0
    scope, fatti, rimanenti = (righe[-1][1], righe[-1][2], righe[-1][3]) if righe else (0.0, 0.0, 0.0)
    concluso = giorno_ora >= giorni
    ideale = lambda g: base * (1 - g / giorni)
    scarto = rimanenti - ideale(giorno_ora)          # > 0 in ritardo, < 0 in anticipo
    giorni_rimasti = max(0.0, giorni - giorno_ora)
    velocita = fatti / giorno_ora if giorno_ora > 0 else 0.0
    previsione = rimanenti if concluso else max(0.0, rimanenti - velocita * giorni_rimasti)
    y_max = max([base, scope, 1.0] + [r[1] for r in righe]) * 1.12
    px0, py0, pw, ph = 100, 220, 790, 280            # zona del grafico
    X = lambda g: px0 + pw * g / giorni
    Y = lambda v: py0 + ph * (1 - v / y_max)
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" font-family="Arial">',
         f'<rect width="{W}" height="{H}" fill="#f3f4f6"/>']

    def testo(x, y, s, dim=12, ancora="middle", colore="#0f172a", peso="normal"):
        o.append(f'<text x="{x:.1f}" y="{y:.1f}" font-size="{dim}" text-anchor="{ancora}" fill="{colore}" '
                 f'font-weight="{peso}">{html.escape(str(s))}</text>')

    def linea(x1, y1, x2, y2, colore, sp=1.5, tratto=""):
        d = f' stroke-dasharray="{tratto}"' if tratto else ""
        o.append(f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="{colore}" stroke-width="{sp}"{d}/>')

    def rettangolo(x, y, w, h, riempimento, rx=8, bordo="none", sp=1, op=1.0):
        o.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{rx}" fill="{riempimento}" '
                 f'fill-opacity="{op}" stroke="{bordo}" stroke-width="{sp}"/>')

    def poligono(punti, riempimento="none", op=1.0, bordo="none", sp=1.5, tratto="", chiuso=True):
        tag, f = ("polygon", riempimento) if chiuso else ("polyline", "none")
        d = f' stroke-dasharray="{tratto}"' if tratto else ""
        o.append(f'<{tag} points="' + " ".join(f"{x:.1f},{y:.1f}" for x, y in punti) +
                 f'" fill="{f}" fill-opacity="{op}" stroke="{bordo}" stroke-width="{sp}" stroke-linejoin="round"{d}/>')

    # intestazione
    testo(40, 42, f"Burndown · {titolo}", 22, "start", "#111827", "bold")
    testo(40, 62, f"{inizio:%d/%m/%Y} → {inizio + timedelta(days=giorni):%d/%m/%Y} · aggiornato al "
                  f"{adesso:%d/%m %H:%M} UTC", 12, "start", "#6b7280")
    # riquadri
    if concluso:
        stato = ("Concluso", "tutto completato" if rimanenti <= 0 else f"{rimanenti:g} punti non completati",
                 "#047857" if rimanenti <= 0 else "#b91c1c")
    elif scarto > 0.5:
        stato = ("In ritardo", f"{scarto:.1f} punti sopra la linea ideale", "#b91c1c")
    elif scarto < -0.5:
        stato = ("In anticipo", f"{-scarto:.1f} punti sotto la linea ideale", "#1d4ed8")
    else:
        stato = ("In linea", "sulla linea ideale", "#047857")
    perc = f"{fatti / scope:.0%} del lavoro" if scope else "nessun task"
    riquadri = [("RIMANENTI", f"{rimanenti:g}", "punti", "#1d4ed8"),
                ("COMPLETATI", f"{fatti:g} / {scope:g}", perc, "#047857"),
                ("GIORNI RIMASTI", f"{giorni_rimasti:.1f}", f"su {giorni}", "#6d28d9"),
                ("STATO", stato[0], stato[1], stato[2])]
    for k, (etichetta, valore, sotto, colore) in enumerate(riquadri):
        x = 40 + k * 224
        rettangolo(x, 82, 208, 92, "#ffffff", 12, "#e5e7eb")
        rettangolo(x, 82, 6, 92, colore, 3)
        testo(x + 20, 106, etichetta, 11, "start", "#6b7280", "bold")
        testo(x + 20, 142, valore, 28 if len(str(valore)) < 9 else 22, "start", colore, "bold")
        testo(x + 20, 162, sotto, 11, "start", "#6b7280")
    rettangolo(40, 192, 880, 394, "#ffffff", 12, "#e5e7eb")
    # griglia
    passo = _passo_griglia(y_max)
    for v in range(0, int(y_max) + 1, passo):
        linea(px0, Y(v), px0 + pw, Y(v), "#f1f5f9", 1)
        testo(px0 - 8, Y(v) + 4, v, 11, "end", "#6b7280")
    ogni = max(1, round(giorni / 7))
    for g in range(0, giorni + 1, ogni):
        linea(X(g), py0, X(g), py0 + ph, "#f8fafc", 1)
        testo(X(g), py0 + ph + 16, f"{inizio + timedelta(days=g):%d/%m}", 10, "middle", "#6b7280")
    # area colorata tra reale e ideale (rosa = in ritardo, azzurra = in anticipo)
    xs, reali = [r[0] for r in righe], [r[3] for r in righe]
    for i in range(len(xs) - 1):
        x0, x1, a0, a1 = xs[i], xs[i + 1], reali[i], reali[i + 1]
        d0, d1 = a0 - ideale(x0), a1 - ideale(x1)
        if d0 * d1 >= 0:
            colore = "#f9a8d4" if (d0 + d1) > 0 else "#93c5fd"
            poligono([(X(x0), Y(a0)), (X(x1), Y(a1)), (X(x1), Y(ideale(x1))), (X(x0), Y(ideale(x0)))], colore, 0.5)
        else:
            # le linee si incrociano: due triangoli divisi nel punto d'incrocio
            quota = d0 / (d0 - d1)
            xc = x0 + quota * (x1 - x0)
            yc = ideale(xc)
            poligono([(X(x0), Y(a0)), (X(xc), Y(yc)), (X(x0), Y(ideale(x0)))], "#f9a8d4" if d0 > 0 else "#93c5fd", 0.5)
            poligono([(X(xc), Y(yc)), (X(x1), Y(a1)), (X(x1), Y(ideale(x1)))], "#f9a8d4" if d1 > 0 else "#93c5fd", 0.5)
    poligono([(X(0), Y(base)), (X(giorni), Y(0))], bordo="#6b7280", sp=2, tratto="7 5", chiuso=False)
    if not concluso and velocita > 0:
        poligono([(X(giorno_ora), Y(rimanenti)), (X(giorni), Y(previsione))], bordo="#ea580c", sp=2.2, tratto="3 4", chiuso=False)
    if righe:
        poligono([(X(x), Y(a)) for x, a in zip(xs, reali)], bordo="#2563eb", sp=3, chiuso=False)
        for x, a in zip(xs, reali):
            o.append(f'<circle cx="{X(x):.1f}" cy="{Y(a):.1f}" r="3.2" fill="#ffffff" stroke="#2563eb" stroke-width="2"/>')
    if not concluso:
        linea(X(giorno_ora), py0, X(giorno_ora), py0 + ph, "#ef4444", 1.3, "4 3")
        rettangolo(X(giorno_ora) - 24, py0 - 4, 48, 18, "#ef4444", 9)
        testo(X(giorno_ora), py0 + 9, "OGGI", 10, "middle", "#ffffff", "bold")
        if velocita > 0:
            in_tempo = previsione < 0.5
            msg = ("Previsione: sprint completato in tempo" if in_tempo
                   else f"Previsione: {previsione:.0f} punti non completati a fine sprint")
            bx, by = X(giorni), py0 + 32
            o.append(f'<circle cx="{bx:.1f}" cy="{Y(previsione):.1f}" r="5" fill="#ea580c"/>')
            linea(bx, by + 26, bx, Y(previsione) - 6, "#ea580c", 1.2, "3 3")
            rettangolo(bx - 290, by, 290, 26, "#f0fdf4" if in_tempo else "#fff7ed", 13, "#16a34a" if in_tempo else "#ea580c", 1.2)
            testo(bx - 145, by + 17, msg, 11, "middle", "#15803d" if in_tempo else "#c2410c", "bold")
    # legenda
    voci = [(100, 534, "#2563eb", "", "punti rimanenti"), (260, 534, "#6b7280", "7 5", "ideale")]
    if not concluso and velocita > 0:
        voci.append((340, 534, "#ea580c", "3 4", f"previsione al ritmo attuale ({velocita:.1f} punti/giorno)"))
    for x, y, colore, tratto, descrizione in voci:
        linea(x, y, x + 30, y, colore, 3, tratto)
        testo(x + 38, y + 4, descrizione, 12, "start", "#374151")
    rettangolo(100, 551, 22, 14, "#f9a8d4", 3, op=0.7)
    testo(128, 562, "area rosa: in ritardo rispetto all'ideale", 12, "start", "#374151")
    rettangolo(430, 551, 22, 14, "#93c5fd", 3, op=0.7)
    testo(458, 562, "area azzurra: in anticipo rispetto all'ideale", 12, "start", "#374151")
    o.append("</svg>")
    return "\n".join(o)


def dati_demo():
    """Dati finti: sprint di 21 giorni, 10 task di taglie diverse, qualcuno aggiunto in corso."""
    inizio = date(2026, 10, 15)

    def task(titolo, punti, giorno_creato, giorno_fatto):
        t0 = datetime.combine(inizio, datetime.min.time(), UTC)
        return {"title": titolo, "points": float(punti), "created": t0 + timedelta(days=giorno_creato),
                "done_at": t0 + timedelta(days=giorno_fatto, hours=12) if giorno_fatto is not None else None,
                "iter": {"title": "S1 - Preparazione", "startDate": inizio.isoformat(), "duration": 21}}

    return [task("S1-01", 3, -2, 5), task("S1-02", 5, -2, 8), task("S1-03", 5, -2, 12), task("S1-04", 3, -2, 9),
            task("S1-05", 5, -2, 15), task("S1-06", 3, -2, None), task("S1-07", 5, -2, None), task("S1-08", 2, -2, None),
            task("S1-09", 3, 4, None), task("S1-10", 2, 6, None)]


def ispeziona(titolo_project, righe):
    """Diagnostica: mostra project, campi e valori trovati, per controllare i nomi in configurazione."""
    visti = {}
    for riga in righe:
        for valore in riga["fieldValues"]["nodes"]:
            nome = (valore.get("field") or {}).get("name")
            if not nome:
                continue
            testo = valore.get("name") or valore.get("number") or (
                f"{valore['title']} ({valore['startDate']}, {valore['duration']} gg)" if "startDate" in valore else "?")
            visti.setdefault((nome, valore["__typename"].replace("ProjectV2ItemField", "")), set()).add(str(testo))
    print(f"Project «{titolo_project}»: {len(righe)} item")
    contabili = [r for r in righe if not motivo_esclusione(r["content"])]
    print(f"Item conteggiabili: {len(contabili)}; esclusi: {len(righe) - len(contabili)}")
    for riga in righe:
        motivo = motivo_esclusione(riga["content"])
        if motivo:
            print(f"  escluso [{motivo}]: {(riga['content'] or {}).get('title', '?')}")
    for (nome, tipo), valori in sorted(visti.items()):
        print(f"  campo «{nome}» [{tipo}]: {sorted(valori)}")
    iterazioni = {}
    for riga in righe:
        for valore in riga["fieldValues"]["nodes"]:
            if "startDate" in valore:
                iterazioni[valore["title"]] = (date.fromisoformat(valore["startDate"]), int(valore["duration"]))
    print("Iterazioni trovate (con item) e confronto con il piano del progetto:")
    for titolo, (inizio, durata) in sorted(iterazioni.items(), key=lambda kv: kv[1][0]):
        esito = "ok" if PIANO_SPRINT.get(inizio) == durata else (
            f"ATTENZIONE: il piano prevede {PIANO_SPRINT[inizio]} giorni da quella data" if inizio in PIANO_SPRINT
            else "ATTENZIONE: data di inizio non presente nel piano")
        print(f"  «{titolo}»: {inizio:%d/%m/%Y}, {durata} gg, fine {inizio + timedelta(days=durata):%d/%m/%Y} -> {esito}")
    campo_punti, campo_taglia = os.environ.get("POINTS_FIELD", "Estimate"), os.environ.get("SIZE_FIELD", "Size")
    print(f"Task per iterazione e stima (campo Number «{campo_punti}», altrimenti taglia «{campo_taglia}», altrimenti 1 punto):")
    for titolo in sorted(iterazioni, key=lambda k: iterazioni[k][0]):
        totale = senza_stima = 0
        for riga in contabili:
            valori = riga["fieldValues"]["nodes"]
            if not any("startDate" in v and v["title"] == titolo for v in valori):
                continue
            totale += 1
            ha_numero = any(v["__typename"] == "ProjectV2ItemFieldNumberValue" and (v.get("field") or {}).get("name") == campo_punti
                            for v in valori)
            ha_taglia = any(v["__typename"] == "ProjectV2ItemFieldSingleSelectValue" and (v.get("field") or {}).get("name") == campo_taglia
                            for v in valori)
            senza_stima += not (ha_numero or ha_taglia)
        print(f"  «{titolo}»: {totale} task, {senza_stima} senza stima" + ("  <- contati come 1 punto" if senza_stima else ""))
        campo_stato = os.environ.get("STATUS_FIELD", "Status")
        somma = 0.0
        for riga in contabili:
            valori = riga["fieldValues"]["nodes"]
            if not any("startDate" in v and v["title"] == titolo for v in valori):
                continue
            numero = next((v["number"] for v in valori if v["__typename"] == "ProjectV2ItemFieldNumberValue"
                           and (v.get("field") or {}).get("name") == campo_punti), None)
            taglia = next((v["name"] for v in valori if v["__typename"] == "ProjectV2ItemFieldSingleSelectValue"
                           and (v.get("field") or {}).get("name") == campo_taglia), None)
            stato = next((v["name"] for v in valori if v["__typename"] == "ProjectV2ItemFieldSingleSelectValue"
                          and (v.get("field") or {}).get("name") == campo_stato), "?")
            if numero is not None:
                punti, da = numero, campo_punti
            elif taglia in PUNTI_PER_TAGLIA:
                punti, da = PUNTI_PER_TAGLIA[taglia], f"{campo_taglia}={taglia}"
            else:
                punti, da = 1, "NESSUNA STIMA (default)"
            somma += punti
            nome_task = (riga["content"] or {}).get("title", "?")
            print(f"      {punti:>4g} pt  [{da}]  {stato:<12} {nome_task[:60]}  (aggiunto al project: {riga['createdAt'][:16]})")
        print(f"      totale punti dell'iterazione: {somma:g}")
    print("Item senza issue collegata (bozze):", sum(1 for r in righe if (r['content'] or {}).get('__typename') == 'DraftIssue'))


def main():
    if "--demo" in sys.argv:
        elementi = dati_demo()
        adesso = datetime(2026, 10, 30, 12, tzinfo=UTC)
        cartella = Path(os.environ.get("OUT_DIR", "."))
    else:
        config = {"iter_field": os.environ.get("ITERATION_FIELD", "Iteration"),
                  "status_field": os.environ.get("STATUS_FIELD", "Status"),
                  "done_value": os.environ.get("DONE_VALUE", "Done"),
                  "points_field": os.environ.get("POINTS_FIELD", "Estimate"),
                  "size_field": os.environ.get("SIZE_FIELD", "Size")}
        titolo_project, righe = scarica_righe(os.environ["PROJECT_TOKEN"], os.environ.get("OWNER_TYPE", "user"),
                                              os.environ["PROJECT_OWNER"], int(os.environ["PROJECT_NUMBER"]))
        if "--inspect" in sys.argv:
            ispeziona(titolo_project, righe)
            return
        elementi = normalizza(righe, config)
        adesso = datetime.now(UTC)
        cartella = Path(os.environ.get("OUT_DIR", "docs/burndown"))
    titolo, inizio, giorni = scegli_iterazione(elementi, os.environ.get("ITERATION_TITLE", ""), adesso.date())
    miei, base, righe = calcola(elementi, titolo, inizio, giorni, adesso)
    cartella.mkdir(parents=True, exist_ok=True)
    slug = re.sub(r"[^a-z0-9]+", "-", titolo.lower()).strip("-")
    with open(cartella / f"burndown-{slug}.csv", "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["giorno", "scope", "completati", "rimanenti"])
        w.writerows([(f"{r[0]:.2f}", r[1], r[2], r[3]) for r in righe])
    svg = disegna_svg(titolo, inizio, giorni, base, righe, adesso)
    (cartella / f"burndown-{slug}.svg").write_text(svg, encoding="utf-8")
    # copia con nome fisso: il README punta sempre a questo file, che mostra lo sprint in corso
    (cartella / "burndown-latest.svg").write_text(svg, encoding="utf-8")
    print(f"{titolo}: {len(miei)} item conteggiati, base {base:g} punti, rimanenti {righe[-1][3]:g}" if righe else "nessun dato")


if __name__ == "__main__":
    main()
