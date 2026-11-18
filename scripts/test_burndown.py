"""Regressioni sul perimetro del burndown, senza rete o credenziali."""
import io
import json
import unittest
from datetime import date, datetime
from unittest import mock

import burndown


CFG = {"token": "x", "tipo": "organization", "proprietario": "Pinguini-alla-Fragola", "numero": 2,
       "campo_sprint": "Iteration", "campo_stato": "Status", "valore_fatto": "Done",
       "campo_punti": "Estimate", "campo_taglia": "Size"}
START = date(2026, 10, 15)
NOW = datetime(2026, 10, 25, 12, tzinfo=burndown.UTC)
ITERATION = {"__typename": "ProjectV2ItemFieldIterationValue", "title": "S1 - Preparazione",
             "startDate": START.isoformat(), "duration": 21, "field": {"name": "Iteration"}}


def node(title, kind="Issue", issue_type="Task", points=3, done=False):
    content = {"__typename": kind, "title": title,
               "closedAt": "2026-10-20T12:00:00Z" if done else None}
    if kind == "Issue":
        content["issueType"] = {"name": issue_type} if issue_type else None
    values = [ITERATION, {"__typename": "ProjectV2ItemFieldSingleSelectValue",
                         "name": "Done" if done else "In progress", "field": {"name": "Status"}}]
    if points is not None:
        values.append({"__typename": "ProjectV2ItemFieldNumberValue",
                       "number": points, "field": {"name": "Estimate"}})
    return {"createdAt": "2026-10-14T12:00:00Z", "updatedAt": "2026-10-21T12:00:00Z",
            "content": content, "fieldValues": {"nodes": values}}


def scarica(nodes):
    """Esegue scarica_dati con una risposta di GitHub finta."""
    risposta = {"data": {"organization": {"projectV2": {"items": {
        "pageInfo": {"hasNextPage": False, "endCursor": None}, "nodes": nodes}}}}}
    with mock.patch.object(burndown.urllib.request, "urlopen",
                           return_value=io.BytesIO(json.dumps(risposta).encode())):
        return burndown.scarica_dati(CFG)


class BurndownScopeTests(unittest.TestCase):
    def calculate(self, nodes):
        sprint = scarica(nodes)[ITERATION["title"]]
        base, rows = burndown.calcola_punti(sprint, NOW)
        return sprint["task"], base, rows

    def test_epics_and_pull_requests_affect_neither_scope_nor_completed(self):
        nodes = [node("Task conclusa", points=3, done=True),
                 node("Story aperta", issue_type="Story", points=5),
                 node("Epic conclusa", issue_type="Epic", points=8, done=True),
                 node("Epic aperta", issue_type="Epic", points=13),
                 node("PR conclusa", kind="PullRequest", points=5, done=True),
                 node("PR aperta", kind="PullRequest", points=8)]
        mine, base, rows = self.calculate(nodes)
        self.assertEqual([i["titolo"] for i in mine], ["Task conclusa", "Story aperta"])
        self.assertEqual(base, 8)
        self.assertEqual(rows[-1][1:], (8, 5.5, 2.5))     # 3 chiusi + metà di 5 (In progress)
        self.assertEqual(rows[0][1:], (8, 2.5, 5.5))

    def test_native_type_controls_filter_instead_of_title(self):
        nodes = [node("Richieste e appuntamenti", issue_type=" ePiC ", points=8),
                 node("[EPIC] è solo parte del titolo", issue_type="Task", points=2)]
        mine, base, rows = self.calculate(nodes)
        self.assertEqual(len(mine), 1)
        self.assertEqual(base, 2)
        self.assertEqual(rows[-1][3], 1)     # In progress: resta il 50%

    def test_questions_untyped_issues_and_drafts_keep_existing_estimates(self):
        nodes = [node("Domanda", issue_type="Question", points=2),
                 node("Senza tipo", issue_type=None, points=3),
                 node("Bozza", kind="DraftIssue", points=None)]
        mine, base, _ = self.calculate(nodes)
        self.assertEqual([i["punti"] for i in mine], [2, 3, 1])
        self.assertEqual(base, 6)

    def test_missing_content_creates_no_phantom_points(self):
        unavailable = node("Non accessibile", points=8, done=True)
        unavailable["content"] = None
        mine, base, rows = self.calculate([unavailable, node("Task", points=2)])
        self.assertEqual(len(mine), 1)
        self.assertEqual(base, 2)
        self.assertEqual(rows[-1][1:], (2, 1, 1))

    def test_iteration_with_only_excluded_items_still_produces_zero_chart(self):
        dati = scarica([node("Epic", issue_type="Epic"), node("PR", kind="PullRequest", done=True)])
        title = burndown.scegli_sprint(dati, "", NOW.date())
        base, rows = burndown.calcola_punti(dati[title], NOW)
        self.assertEqual(dati[title]["task"], [])
        self.assertEqual(base, 0)
        self.assertTrue(all(row[1:] == (0, 0, 0) for row in rows))
        self.assertIn("nessun task", burndown.disegna_grafico(title, dati[title], base, rows, NOW))

    def test_estimate_zero_and_size_fallback_are_preserved(self):
        sized = node("Con taglia", points=None)
        sized["fieldValues"]["nodes"].append({"__typename": "ProjectV2ItemFieldSingleSelectValue",
                                               "name": "L", "field": {"name": "Size"}})
        mine, base, _ = self.calculate([node("Zero", points=0), sized])
        self.assertEqual([i["punti"] for i in mine], [0, 5])
        self.assertEqual(base, 5)

    def test_remaining_work_depends_on_status(self):
        def task(stato, eventi=(), chiuso=None):
            return {"punti": 8.0, "chiuso": chiuso, "stato_ora": stato, "eventi": list(eventi)}
        t = datetime(2026, 10, 20, tzinfo=burndown.UTC)
        for stato, resto in [("Backlog", 8), ("Ready", 6), ("In progress", 4), ("In review", 2), ("Done", 0)]:
            self.assertEqual(burndown.residuo(task(stato, chiuso=t if stato == "Done" else None), NOW), resto, stato)

    def test_status_history_is_replayed_over_time(self):
        giorno = lambda g: datetime(2026, 10, g, 12, tzinfo=burndown.UTC)
        t = {"punti": 4.0, "chiuso": giorno(22), "stato_ora": "Done",
             "eventi": [(giorno(16), "Backlog", "Ready"), (giorno(18), "Ready", "In progress"),
                        (giorno(20), "In progress", "In review"), (giorno(22), "In review", "Done")]}
        attesi = [(15, 4), (17, 3), (19, 2), (21, 1), (23, 0)]
        self.assertEqual([burndown.residuo(t, giorno(g)) for g, _ in attesi], [r for _, r in attesi])

    def test_sprint_choice(self):
        dati = {"S1": {"inizio": date(2026, 10, 15), "giorni": 21, "task": []},
                "S2": {"inizio": date(2026, 11, 5), "giorni": 14, "task": []}}
        self.assertEqual(burndown.scegli_sprint(dati, "", date(2026, 11, 4)), "S1")    # ultimo giorno di S1
        self.assertEqual(burndown.scegli_sprint(dati, "", date(2026, 11, 5)), "S2")    # primo giorno di S2
        self.assertEqual(burndown.scegli_sprint(dati, "", date(2027, 1, 1)), "S2")     # nessuno in corso: l'ultimo
        self.assertEqual(burndown.scegli_sprint(dati, "S1", date(2026, 11, 5)), "S1")  # scelto a mano
        with self.assertRaises(SystemExit):
            burndown.scegli_sprint(dati, "S9", date(2026, 11, 5))


if __name__ == "__main__":
    unittest.main()
