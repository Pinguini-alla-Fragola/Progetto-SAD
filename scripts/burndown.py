#!/usr/bin/env python3
"""Burndown di uno sprint da un GitHub Project (v2), pesato con la taglia dei task.

Legge gli item della iterazione scelta, calcola i punti rimanenti giorno per giorno
(scope - completati) e scrive un CSV e un grafico SVG con la linea ideale.
Solo libreria standard: nessuna dipendenza da installare nella CI.

Variabili d'ambiente (le prime tre servono, le altre hanno un default):
  PROJECT_TOKEN     token personale classico con scope `project` e `repo`
  PROJECT_OWNER     login proprietario del project (es. CapHello)
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
Scrive solo l'iterazione in corso (e la copia `burndown-latest.svg` per il README): i file degli sprint conclusi restano com'erano, cosi'
i task non finiti e spostati nello sprint successivo non alterano la storia (un file per sprint).

I punti di un task: campo Number se presente, altrimenti mappatura della taglia
(SIZE_POINTS qui sotto), altrimenti 1.  Prova senza API:  python burndown.py --demo
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

SIZE_POINTS = {"XS": 1, "S": 2, "M": 3, "L": 5, "XL": 8}   # mappatura da concordare nel team
# Calendario del progetto PICUS (presentazione, slide 11-12): data di inizio -> durata in giorni.
# La preparatoria dura 3 settimane (eccezione); le tre iterazioni di costruzione 2 settimane.
PIANO = {date(2026, 10, 15): 21, date(2026, 11, 5): 14, date(2026, 11, 19): 14, date(2026, 12, 3): 14}
UTC = timezone.utc

QUERY = """
query($login:String!, $num:Int!, $after:String) {
  OWNER(login:$login) {
    projectV2(number:$num) {
      title
      items(first:100, after:$after) {
        pageInfo { hasNextPage endCursor }
        nodes {
          createdAt updatedAt
          content {
            __typename
            ... on Issue { number title closedAt }
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


def graphql(token, owner_type, login, num):
    query = QUERY.replace("OWNER", "organization" if owner_type == "organization" else "user")
    nodes, after = [], None
    while True:
        body = json.dumps({"query": query, "variables": {"login": login, "num": num, "after": after}}).encode()
        req = urllib.request.Request("https://api.github.com/graphql", data=body, headers={
            "Authorization": f"Bearer {token}", "Content-Type": "application/json"})
        with urllib.request.urlopen(req) as r:
            data = json.load(r)
        if "errors" in data:
            raise SystemExit(f"Errore GraphQL: {data['errors']}")
        proj = data["data"][owner_type if owner_type == "organization" else "user"]["projectV2"]
        nodes += proj["items"]["nodes"]
        if not proj["items"]["pageInfo"]["hasNextPage"]:
            return proj["title"], nodes
        after = proj["items"]["pageInfo"]["endCursor"]


def parse_ts(s):
    return datetime.fromisoformat(s.replace("Z", "+00:00")) if s else None


def normalize(nodes, cfg):
    """Trasforma gli item grezzi in dizionari semplici (iterazione, punti, date)."""
    items = []
    for n in nodes:
        f = {"iter": None, "status": None, "points": None, "size": None}
        for v in n["fieldValues"]["nodes"]:
            name = (v.get("field") or {}).get("name")
            if v["__typename"] == "ProjectV2ItemFieldIterationValue" and name == cfg["iter_field"]:
                f["iter"] = v
            elif v["__typename"] == "ProjectV2ItemFieldSingleSelectValue":
                if name == cfg["status_field"]:
                    f["status"] = v["name"]
                elif name == cfg["size_field"]:
                    f["size"] = v["name"]
            elif v["__typename"] == "ProjectV2ItemFieldNumberValue" and name == cfg["points_field"]:
                f["points"] = v["number"]
        c = n["content"] or {}
        points = f["points"] if f["points"] is not None else SIZE_POINTS.get(f["size"], 1)
        done = f["status"] == cfg["done_value"]
        # data di completamento: chiusura dell'issue se c'è, altrimenti ultimo aggiornamento
        done_at = (parse_ts(c.get("closedAt")) or parse_ts(n["updatedAt"])) if done else None
        items.append({"title": c.get("title", "?"), "iter": f["iter"], "points": float(points),
                      "created": parse_ts(n["createdAt"]), "done_at": done_at})
    return items


def pick_iteration(items, title, today):
    iters = {}
    for it in items:
        v = it["iter"]
        if v:
            iters[v["title"]] = (date.fromisoformat(v["startDate"]), int(v["duration"]))
    if title:
        if title not in iters:
            raise SystemExit(f"Iterazione «{title}» non trovata. Disponibili: {sorted(iters)}")
        return title, *iters[title]
    for t, (s, d) in iters.items():
        if s <= today < s + timedelta(days=d):
            return t, s, d
    t = max(iters, key=lambda k: iters[k][0])      # nessuna in corso: l'ultima
    return t, *iters[t]


def compute(items, title, start, days, now):
    """Serie (giorni dall'inizio, scope, completati, rimanenti) a ogni mezzanotte e adesso."""
    mine = [i for i in items if i["iter"] and i["iter"]["title"] == title]
    t0 = datetime.combine(start, datetime.min.time(), UTC)
    stops = [t0 + timedelta(days=k) for k in range(days + 1) if t0 + timedelta(days=k) <= now]
    if now < t0 + timedelta(days=days):
        stops.append(now)                           # punto «adesso» se lo sprint è in corso
    rows = []
    for t in stops:
        scope = sum(i["points"] for i in mine if i["created"] <= t)
        done = sum(i["points"] for i in mine if i["done_at"] and i["done_at"] <= t)
        rows.append(((t - t0).total_seconds() / 86400, scope, done, scope - done))
    base = rows[0][1] if rows and rows[0][1] > 0 else sum(i["points"] for i in mine)
    return mine, base, rows


def _nice_step(ymax):
    for s in (1, 2, 5, 10, 20, 50, 100):
        if ymax / s <= 8:
            return s
    return 100


def render_svg(title, start, days, base, rows, now):
    """Grafico in stile dashboard (riquadri di stato, previsione) con l'area colorata tra reale e ideale."""
    W, H = 960, 600
    x_now = rows[-1][0] if rows else 0.0
    scope, done, rem = (rows[-1][1], rows[-1][2], rows[-1][3]) if rows else (0.0, 0.0, 0.0)
    finished = x_now >= days
    ideal = lambda x: base * (1 - x / days)
    behind = rem - ideal(x_now)
    left = max(0.0, days - x_now)
    velocity = done / x_now if x_now > 0 else 0.0
    forecast_end = rem if finished else max(0.0, rem - velocity * left)
    ymax = max([base, scope, 1.0] + [r[1] for r in rows]) * 1.12
    px0, py0, pw, ph = 100, 220, 790, 280
    X = lambda d: px0 + pw * d / days
    Y = lambda v: py0 + ph * (1 - v / ymax)
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" font-family="Arial">',
         f'<rect width="{W}" height="{H}" fill="#f3f4f6"/>']

    def text(x, y, s, size=12, anchor="middle", color="#0f172a", weight="normal"):
        o.append(f'<text x="{x:.1f}" y="{y:.1f}" font-size="{size}" text-anchor="{anchor}" fill="{color}" '
                 f'font-weight="{weight}">{html.escape(str(s))}</text>')

    def line(x1, y1, x2, y2, color, w=1.5, dash=""):
        d = f' stroke-dasharray="{dash}"' if dash else ""
        o.append(f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="{color}" stroke-width="{w}"{d}/>')

    def rect(x, y, w, h, fill, rx=8, stroke="none", sw=1, op=1.0):
        o.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{rx}" fill="{fill}" '
                 f'fill-opacity="{op}" stroke="{stroke}" stroke-width="{sw}"/>')

    def poly(pts, fill="none", op=1.0, stroke="none", w=1.5, dash="", closed=True):
        tag, f = ("polygon", fill) if closed else ("polyline", "none")
        d = f' stroke-dasharray="{dash}"' if dash else ""
        o.append(f'<{tag} points="' + " ".join(f"{x:.1f},{y:.1f}" for x, y in pts) +
                 f'" fill="{f}" fill-opacity="{op}" stroke="{stroke}" stroke-width="{w}" stroke-linejoin="round"{d}/>')

    # intestazione
    text(40, 42, f"Burndown · {title}", 22, "start", "#111827", "bold")
    text(40, 62, f"{start:%d/%m/%Y} → {start + timedelta(days=days):%d/%m/%Y} · aggiornato al "
                 f"{now:%d/%m %H:%M} UTC", 12, "start", "#6b7280")
    # riquadri
    if finished:
        stato = ("Concluso", "tutto completato" if rem <= 0 else f"{rem:g} punti non completati",
                 "#047857" if rem <= 0 else "#b91c1c")
    elif behind > 0.5:
        stato = ("In ritardo", f"{behind:.1f} punti sopra la linea ideale", "#b91c1c")
    elif behind < -0.5:
        stato = ("In anticipo", f"{-behind:.1f} punti sotto la linea ideale", "#1d4ed8")
    else:
        stato = ("In linea", "sulla linea ideale", "#047857")
    pct = f"{done / scope:.0%} del lavoro" if scope else "nessun task"
    cards = [("RIMANENTI", f"{rem:g}", "punti", "#1d4ed8"),
             ("COMPLETATI", f"{done:g} / {scope:g}", pct, "#047857"),
             ("GIORNI RIMASTI", f"{left:.1f}", f"su {days}", "#6d28d9"),
             ("STATO", stato[0], stato[1], stato[2])]
    for k, (t, v, sub, col) in enumerate(cards):
        x = 40 + k * 224
        rect(x, 82, 208, 92, "#ffffff", 12, "#e5e7eb")
        rect(x, 82, 6, 92, col, 3)
        text(x + 20, 106, t, 11, "start", "#6b7280", "bold")
        text(x + 20, 142, v, 28 if len(str(v)) < 9 else 22, "start", col, "bold")
        text(x + 20, 162, sub, 11, "start", "#6b7280")
    rect(40, 192, 880, 394, "#ffffff", 12, "#e5e7eb")
    # griglia
    st = _nice_step(ymax)
    for v in range(0, int(ymax) + 1, st):
        line(px0, Y(v), px0 + pw, Y(v), "#f1f5f9", 1)
        text(px0 - 8, Y(v) + 4, v, 11, "end", "#6b7280")
    every = max(1, round(days / 7))
    for d in range(0, days + 1, every):
        line(X(d), py0, X(d), py0 + ph, "#f8fafc", 1)
        text(X(d), py0 + ph + 16, f"{start + timedelta(days=d):%d/%m}", 10, "middle", "#6b7280")
    # area colorata tra reale e ideale (rosa = in ritardo, azzurra = in anticipo)
    xs, acts = [r[0] for r in rows], [r[3] for r in rows]
    for i in range(len(xs) - 1):
        x0, x1, a0, a1 = xs[i], xs[i + 1], acts[i], acts[i + 1]
        d0, d1 = a0 - ideal(x0), a1 - ideal(x1)
        if d0 * d1 >= 0:
            col = "#f9a8d4" if (d0 + d1) > 0 else "#93c5fd"
            poly([(X(x0), Y(a0)), (X(x1), Y(a1)), (X(x1), Y(ideal(x1))), (X(x0), Y(ideal(x0)))], col, 0.5)
        else:
            t = d0 / (d0 - d1)
            xc = x0 + t * (x1 - x0)
            yc = ideal(xc)
            poly([(X(x0), Y(a0)), (X(xc), Y(yc)), (X(x0), Y(ideal(x0)))], "#f9a8d4" if d0 > 0 else "#93c5fd", 0.5)
            poly([(X(xc), Y(yc)), (X(x1), Y(a1)), (X(x1), Y(ideal(x1)))], "#f9a8d4" if d1 > 0 else "#93c5fd", 0.5)
    poly([(X(0), Y(base)), (X(days), Y(0))], stroke="#6b7280", w=2, dash="7 5", closed=False)
    if not finished and velocity > 0:
        poly([(X(x_now), Y(rem)), (X(days), Y(forecast_end))], stroke="#ea580c", w=2.2, dash="3 4", closed=False)
    if rows:
        poly([(X(x), Y(a)) for x, a in zip(xs, acts)], stroke="#2563eb", w=3, closed=False)
        for x, a in zip(xs, acts):
            o.append(f'<circle cx="{X(x):.1f}" cy="{Y(a):.1f}" r="3.2" fill="#ffffff" stroke="#2563eb" stroke-width="2"/>')
    if not finished:
        line(X(x_now), py0, X(x_now), py0 + ph, "#ef4444", 1.3, "4 3")
        rect(X(x_now) - 24, py0 - 4, 48, 18, "#ef4444", 9)
        text(X(x_now), py0 + 9, "OGGI", 10, "middle", "#ffffff", "bold")
        if velocity > 0:
            ok = forecast_end < 0.5
            msg = ("Previsione: sprint completato in tempo" if ok
                   else f"Previsione: {forecast_end:.0f} punti non completati a fine sprint")
            bx, by = X(days), py0 + 32
            o.append(f'<circle cx="{bx:.1f}" cy="{Y(forecast_end):.1f}" r="5" fill="#ea580c"/>')
            line(bx, by + 26, bx, Y(forecast_end) - 6, "#ea580c", 1.2, "3 3")
            rect(bx - 290, by, 290, 26, "#f0fdf4" if ok else "#fff7ed", 13, "#16a34a" if ok else "#ea580c", 1.2)
            text(bx - 145, by + 17, msg, 11, "middle", "#15803d" if ok else "#c2410c", "bold")
    # legenda
    items = [(100, 534, "#2563eb", "", "punti rimanenti"), (260, 534, "#6b7280", "7 5", "ideale")]
    if not finished and velocity > 0:
        items.append((340, 534, "#ea580c", "3 4", f"previsione al ritmo attuale ({velocity:.1f} punti/giorno)"))
    for x, y, col, dash, txt in items:
        line(x, y, x + 30, y, col, 3, dash)
        text(x + 38, y + 4, txt, 12, "start", "#374151")
    rect(100, 551, 22, 14, "#f9a8d4", 3, op=0.7)
    text(128, 562, "area rosa: in ritardo rispetto all'ideale", 12, "start", "#374151")
    rect(430, 551, 22, 14, "#93c5fd", 3, op=0.7)
    text(458, 562, "area azzurra: in anticipo rispetto all'ideale", 12, "start", "#374151")
    o.append("</svg>")
    return "\n".join(o)


def demo_items():
    """Dati finti: sprint di 21 giorni, 10 task di taglie diverse, qualcuno aggiunto in corso."""
    start = date(2026, 10, 15)
    def it(title, pts, created_day, done_day):
        t0 = datetime.combine(start, datetime.min.time(), UTC)
        return {"title": title, "points": float(pts), "created": t0 + timedelta(days=created_day),
                "done_at": t0 + timedelta(days=done_day, hours=12) if done_day is not None else None,
                "iter": {"title": "S1 - Preparazione", "startDate": start.isoformat(), "duration": 21}}
    return [it("S1-01", 3, -2, 5), it("S1-02", 5, -2, 8), it("S1-03", 5, -2, 12), it("S1-04", 3, -2, 9),
            it("S1-05", 5, -2, 15), it("S1-06", 3, -2, None), it("S1-07", 5, -2, None), it("S1-08", 2, -2, None),
            it("S1-09", 3, 4, None), it("S1-10", 2, 6, None)]


def inspect(ptitle, nodes):
    """Diagnostica: mostra project, campi e valori trovati, per controllare i nomi in configurazione."""
    seen = {}
    for n in nodes:
        for v in n["fieldValues"]["nodes"]:
            name = (v.get("field") or {}).get("name")
            if not name:
                continue
            val = v.get("name") or v.get("number") or (f"{v['title']} ({v['startDate']}, {v['duration']} gg)"
                                                       if "startDate" in v else "?")
            seen.setdefault((name, v["__typename"].replace("ProjectV2ItemField", "")), set()).add(str(val))
    print(f"Project «{ptitle}»: {len(nodes)} item")
    for (name, kind), vals in sorted(seen.items()):
        print(f"  campo «{name}» [{kind}]: {sorted(vals)}")
    iters = {}
    for n in nodes:
        for v in n["fieldValues"]["nodes"]:
            if "startDate" in v:
                iters[v["title"]] = (date.fromisoformat(v["startDate"]), int(v["duration"]))
    print("Iterazioni trovate (con item) e confronto con il piano del progetto:")
    for t, (st, d) in sorted(iters.items(), key=lambda kv: kv[1][0]):
        esito = "ok" if PIANO.get(st) == d else (
            f"ATTENZIONE: il piano prevede {PIANO[st]} giorni da quella data" if st in PIANO
            else "ATTENZIONE: data di inizio non presente nel piano")
        print(f"  «{t}»: {st:%d/%m/%Y}, {d} gg, fine {st + timedelta(days=d):%d/%m/%Y} -> {esito}")
    pf, sf = os.environ.get("POINTS_FIELD", "Estimate"), os.environ.get("SIZE_FIELD", "Size")
    print(f"Task per iterazione e stima (campo Number «{pf}», altrimenti taglia «{sf}», altrimenti 1 punto):")
    for t in sorted(iters, key=lambda k: iters[k][0]):
        tot = no = 0
        for n in nodes:
            vals = n["fieldValues"]["nodes"]
            if not any("startDate" in v and v["title"] == t for v in vals):
                continue
            tot += 1
            has_num = any(v["__typename"] == "ProjectV2ItemFieldNumberValue" and (v.get("field") or {}).get("name") == pf
                          for v in vals)
            has_size = any(v["__typename"] == "ProjectV2ItemFieldSingleSelectValue" and (v.get("field") or {}).get("name") == sf
                           for v in vals)
            no += not (has_num or has_size)
        print(f"  «{t}»: {tot} task, {no} senza stima" + ("  <- contati come 1 punto" if no else ""))
    print("Item senza issue collegata (bozze):", sum(1 for n in nodes if (n['content'] or {}).get('__typename') == 'DraftIssue'))


def main():
    if "--demo" in sys.argv:
        items = demo_items()
        now = datetime(2026, 10, 30, 12, tzinfo=UTC)
        out = Path(os.environ.get("OUT_DIR", "."))
    else:
        cfg = {"iter_field": os.environ.get("ITERATION_FIELD", "Iteration"),
               "status_field": os.environ.get("STATUS_FIELD", "Status"),
               "done_value": os.environ.get("DONE_VALUE", "Done"),
               "points_field": os.environ.get("POINTS_FIELD", "Estimate"),
               "size_field": os.environ.get("SIZE_FIELD", "Size")}
        ptitle, nodes = graphql(os.environ["PROJECT_TOKEN"], os.environ.get("OWNER_TYPE", "user"),
                                os.environ["PROJECT_OWNER"], int(os.environ["PROJECT_NUMBER"]))
        if "--inspect" in sys.argv:
            inspect(ptitle, nodes)
            return
        items = normalize(nodes, cfg)
        now = datetime.now(UTC)
        out = Path(os.environ.get("OUT_DIR", "docs/burndown"))
    title, start, days = pick_iteration(items, os.environ.get("ITERATION_TITLE", ""), now.date())
    mine, base, rows = compute(items, title, start, days, now)
    out.mkdir(parents=True, exist_ok=True)
    slug = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")
    with open(out / f"burndown-{slug}.csv", "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["giorno", "scope", "completati", "rimanenti"])
        w.writerows([(f"{r[0]:.2f}", r[1], r[2], r[3]) for r in rows])
    svg = render_svg(title, start, days, base, rows, now)
    (out / f"burndown-{slug}.svg").write_text(svg, encoding="utf-8")
    # copia con nome fisso: il README punta sempre a questo file, che mostra lo sprint in corso
    (out / "burndown-latest.svg").write_text(svg, encoding="utf-8")
    print(f"{title}: {len(mine)} task, base {base:g} punti, rimanenti {rows[-1][3]:g}" if rows else "nessun dato")


if __name__ == "__main__":
    main()
