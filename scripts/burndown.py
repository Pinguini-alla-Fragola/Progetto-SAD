#!/usr/bin/env python3
"""Burndown di uno sprint da un GitHub Project (v2): scrive un CSV e un grafico SVG.

Punti di un task: campo Number, altrimenti la taglia (PUNTI_PER_TAGLIA), altrimenti 1.
Esclude Pull Request, Epic e contenuti non disponibili. Solo libreria standard.

Variabili d'ambiente (le prime tre sono obbligatorie):
  PROJECT_TOKEN, PROJECT_OWNER, PROJECT_NUMBER
  OWNER_TYPE (user), ITERATION_TITLE (sprint in corso), OUT_DIR (docs/burndown)
  ITERATION_FIELD (Iteration), STATUS_FIELD (Status), DONE_VALUE (Done),
  POINTS_FIELD (Estimate), SIZE_FIELD (Size)
"""
import csv
import html
import json
import os
import re
import urllib.request
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

PUNTI_PER_TAGLIA = {"XS": 1, "S": 2, "M": 3, "L": 5, "XL": 8}   # da concordare nel team
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


def scarica_dati(cfg):
    """Legge il Project da GitHub e restituisce i task conteggiati, divisi per sprint:
    {titolo: {"inizio": date, "giorni": int, "task": [{"titolo", "punti", "creato", "chiuso"}]}}"""
    tipo = "organization" if cfg["tipo"] == "organization" else "user"
    query = QUERY.replace("TIPO_PROPRIETARIO", tipo)
    righe, cursore = [], None
    while True:
        corpo = json.dumps({"query": query, "variables": {"login": cfg["proprietario"], "num": cfg["numero"],
                                                          "after": cursore}}).encode()
        richiesta = urllib.request.Request("https://api.github.com/graphql", data=corpo, headers={
            "Authorization": f"Bearer {cfg['token']}", "Content-Type": "application/json"})
        with urllib.request.urlopen(richiesta) as risposta:
            risposta = json.load(risposta)
        if "errors" in risposta:
            raise SystemExit(f"Errore GraphQL: {risposta['errors']}")
        pagina = risposta["data"][tipo]["projectV2"]["items"]
        righe += pagina["nodes"]
        if not pagina["pageInfo"]["hasNextPage"]:
            break
        cursore = pagina["pageInfo"]["endCursor"]

    data = lambda testo: datetime.fromisoformat(testo.replace("Z", "+00:00")) if testo else None
    sprint = {}
    for riga in righe:
        campi = {(v.get("field") or {}).get("name"): v for v in riga["fieldValues"]["nodes"]}   # nome campo -> valore
        it = campi.get(cfg["campo_sprint"], {})
        if "startDate" not in it:
            continue
        # lo sprint esiste anche se il task è escluso
        s = sprint.setdefault(it["title"], {"task": []})
        s["inizio"], s["giorni"] = date.fromisoformat(it["startDate"]), int(it["duration"])
        contenuto = riga["content"] or {}
        epic = (contenuto.get("issueType") or {}).get("name", "").strip().casefold() == "epic"
        if not contenuto or contenuto.get("__typename") == "PullRequest" or (contenuto.get("__typename") == "Issue" and epic):
            continue
        punti = campi.get(cfg["campo_punti"], {}).get("number")
        if punti is None:
            punti = PUNTI_PER_TAGLIA.get(campi.get(cfg["campo_taglia"], {}).get("name"), 1)
        fatto = campi.get(cfg["campo_stato"], {}).get("name") == cfg["valore_fatto"]
        # completato: chiusura dell'issue se c'è, altrimenti ultimo aggiornamento
        chiuso = (data(contenuto.get("closedAt")) or data(riga["updatedAt"])) if fatto else None
        s["task"].append({"titolo": contenuto.get("title", "?"), "punti": float(punti),
                          "creato": data(riga["createdAt"]), "chiuso": chiuso})
    return sprint


def scegli_sprint(dati, titolo, oggi):
    """Titolo dello sprint da disegnare: quello richiesto, altrimenti quello in corso, altrimenti l'ultimo."""
    if titolo:
        if titolo not in dati:
            raise SystemExit(f"Iterazione «{titolo}» non trovata. Disponibili: {sorted(dati)}")
        return titolo
    if not dati:
        raise SystemExit("Nessuna iterazione trovata negli item del Project.")
    for nome, s in dati.items():
        if s["inizio"] <= oggi < s["inizio"] + timedelta(days=s["giorni"]):
            return nome
    return max(dati, key=lambda nome: dati[nome]["inizio"])


def calcola_punti(sprint, adesso):
    """Punti a ogni mezzanotte dello sprint fino a adesso, più adesso se lo sprint è in corso.
    Restituisce (base, righe) con righe = [(giorni dall'inizio, scope, completati, rimanenti)]."""
    task, giorni = sprint["task"], sprint["giorni"]
    t0 = datetime.combine(sprint["inizio"], datetime.min.time(), UTC)
    istanti = [t0 + timedelta(days=k) for k in range(giorni + 1) if t0 + timedelta(days=k) <= adesso]
    if adesso < t0 + timedelta(days=giorni):
        istanti.append(adesso)
    # i task creati nel primo giorno (planning) fanno parte dello scope iniziale
    aggiunto = lambda t: t0 if t["creato"] < t0 + timedelta(days=1) else t["creato"]
    righe = []
    for istante in istanti:
        scope = sum(t["punti"] for t in task if aggiunto(t) <= istante)
        fatti = sum(t["punti"] for t in task if t["chiuso"] and t["chiuso"] <= istante)
        righe.append(((istante - t0).total_seconds() / 86400, scope, fatti, scope - fatti))
    base = righe[0][1] if righe and righe[0][1] > 0 else sum(t["punti"] for t in task)
    return base, righe


def disegna_grafico(titolo, sprint, base, righe, adesso):
    """Restituisce l'SVG: riquadri di stato, punti rimanenti, linea ideale, previsione."""
    inizio, giorni = sprint["inizio"], sprint["giorni"]
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
    passo = next((p for p in (1, 2, 5, 10, 20, 50, 100) if y_max / p <= 8), 100)
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


def main():
    env = os.environ
    cfg = {"token": env["PROJECT_TOKEN"], "tipo": env.get("OWNER_TYPE", "user"),
           "proprietario": env["PROJECT_OWNER"], "numero": int(env["PROJECT_NUMBER"]),
           "campo_sprint": env.get("ITERATION_FIELD", "Iteration"), "campo_stato": env.get("STATUS_FIELD", "Status"),
           "valore_fatto": env.get("DONE_VALUE", "Done"), "campo_punti": env.get("POINTS_FIELD", "Estimate"),
           "campo_taglia": env.get("SIZE_FIELD", "Size")}
    dati = scarica_dati(cfg)
    adesso = datetime.now(UTC)
    titolo = scegli_sprint(dati, env.get("ITERATION_TITLE", ""), adesso.date())
    base, righe = calcola_punti(dati[titolo], adesso)
    svg = disegna_grafico(titolo, dati[titolo], base, righe, adesso)

    cartella = Path(env.get("OUT_DIR", "docs/burndown"))
    cartella.mkdir(parents=True, exist_ok=True)
    nome = re.sub(r"[^a-z0-9]+", "-", titolo.lower()).strip("-")
    with open(cartella / f"burndown-{nome}.csv", "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["giorno", "scope", "completati", "rimanenti"])
        w.writerows([(f"{r[0]:.2f}", r[1], r[2], r[3]) for r in righe])
    (cartella / f"burndown-{nome}.svg").write_text(svg, encoding="utf-8")
    (cartella / "burndown-latest.svg").write_text(svg, encoding="utf-8")     # il README punta a questo
    print(f"{titolo}: {len(dati[titolo]['task'])} item conteggiati, base {base:g} punti, rimanenti {righe[-1][3]:g}"
          if righe else "nessun dato")


if __name__ == "__main__":
    main()
