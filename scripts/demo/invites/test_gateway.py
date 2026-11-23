from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

import gateway


class InvitiTest(unittest.TestCase):
    NOW = 1_800_000_000

    def setUp(self):
        directory = TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.path = Path(directory.name) / "inviti.db"
        gateway.initialize(self.path)
        self.invite = gateway.new_invite(self.path, "revisore test", 1, self.NOW)
        self.token = self.invite["codice"]

    def test_solo_un_codice_di_rientro_con_accessi_in_parallelo(self):
        with ThreadPoolExecutor(max_workers=6) as pool:
            accesses = list(pool.map(lambda _: gateway.redeem(self.path, self.token, self.NOW), range(6)))
        self.assertEqual(sum(access.return_code is not None for access in accesses), 1)
        self.assertEqual({access.expires for access in accesses}, {self.NOW + 3600})
        self.assertEqual(len({access.session for access in accesses}), 6)

    def test_durata_dal_primo_accesso_e_scadenza_condivisa(self):
        self.assertIsNone(self.invite["scadenza"])
        first = gateway.redeem(self.path, self.token, self.NOW)
        self.assertNotEqual(first.return_code, self.token)
        self.assertEqual(first.expires, self.NOW + 3600)
        second = gateway.redeem(self.path, first.return_code, self.NOW + 1800)
        third = gateway.redeem(self.path, self.token, self.NOW + 2000)
        self.assertEqual(second.expires, first.expires)
        self.assertEqual(third.expires, first.expires)
        self.assertIsNone(second.return_code)

        for access in (first, second, third):
            self.assertTrue(gateway.valid_session(self.path, access.session, first.expires - 1))
            self.assertFalse(gateway.valid_session(self.path, access.session, first.expires))
        for code in (self.token, first.return_code):
            self.assertIsNone(gateway.redeem(self.path, code, first.expires))

    def test_revoca_invito_non_usato(self):
        self.assertTrue(gateway.revoke(self.path, self.invite["id"], self.NOW))
        self.assertIsNone(gateway.redeem(self.path, self.token, self.NOW))

    def test_scadenza_dell_attivazione_e_indipendente_dalla_durata(self):
        deadline = self.NOW + 48 * 3600
        self.assertEqual(self.invite["attiva_entro"], gateway.timestamp(deadline))
        self.assertIsNone(gateway.redeem(self.path, self.token, deadline))

        late = gateway.new_invite(self.path, "attivazione al limite", 36, self.NOW)
        access = gateway.redeem(self.path, late["codice"], deadline - 1)
        self.assertEqual(access.expires, deadline - 1 + 36 * 3600)
        self.assertIsNotNone(gateway.redeem(self.path, access.return_code, deadline + 60))

    def test_migrazione_non_rinnova_la_finestra_degli_inviti_in_attesa(self):
        with gateway.database(self.path) as database:
            database.execute("ALTER TABLE inviti DROP COLUMN attiva_entro")
        with patch("gateway.time.time", return_value=self.NOW + 3600) as clock:
            gateway.initialize(self.path)
            clock.return_value += 3600
            gateway.initialize(self.path)
        with gateway.database(self.path) as database:
            row = database.execute("SELECT * FROM inviti").fetchone()
        deadline = self.NOW + 49 * 3600
        self.assertEqual(row["attiva_entro"], deadline)
        self.assertEqual(gateway.invite_status(row, deadline - 1), "da attivare")
        self.assertEqual(gateway.invite_status(row, deadline), "scaduto")

    def test_revoca_sessione_e_archiviazione_degli_hash(self):
        first = gateway.redeem(self.path, self.token, self.NOW)
        second = gateway.redeem(self.path, first.return_code, self.NOW + 1)
        with gateway.database(self.path) as database:
            row = database.execute("SELECT hash_token, hash_rientro FROM inviti").fetchone()
            hashes = {row[0] for row in database.execute("SELECT hash_sessione FROM sessioni_invito")}
        self.assertEqual(tuple(row), (gateway.digest(self.token), gateway.digest(first.return_code)))
        self.assertEqual(hashes, {gateway.digest(first.session), gateway.digest(second.session)})

        self.assertTrue(gateway.revoke(self.path, self.invite["id"], self.NOW + 2))
        self.assertFalse(gateway.valid_session(self.path, first.session, self.NOW + 2))
        self.assertFalse(gateway.valid_session(self.path, second.session, self.NOW + 2))
        self.assertIsNone(gateway.redeem(self.path, first.return_code, self.NOW + 2))

    def test_migrazione_conserva_la_sessione_e_gli_inviti_precedenti(self):
        legacy = self.path.with_name("legacy.db")
        old_token, old_session, unused_token = (letter * 43 for letter in "abc")
        with gateway.database(legacy) as database:
            database.execute("""
                CREATE TABLE inviti (
                    id TEXT PRIMARY KEY, etichetta TEXT NOT NULL, hash_token BLOB NOT NULL UNIQUE,
                    scadenza INTEGER NOT NULL, riscattato INTEGER, revocato INTEGER,
                    hash_sessione BLOB UNIQUE, scadenza_sessione INTEGER
                )
            """)
            database.execute("INSERT INTO inviti VALUES (?, ?, ?, ?, ?, NULL, ?, ?)", (
                "gia-attivo", "precedente", gateway.digest(old_token), self.NOW + 3600,
                self.NOW - 10, gateway.digest(old_session), self.NOW + 30,
            ))
            database.execute("INSERT INTO inviti (id, etichetta, hash_token, scadenza) VALUES (?, ?, ?, ?)", (
                "non-usato", "precedente", gateway.digest(unused_token), self.NOW + 3600,
            ))
        gateway.initialize(legacy)
        gateway.initialize(legacy)
        self.assertTrue(gateway.valid_session(legacy, old_session, self.NOW))
        self.assertFalse(gateway.valid_session(legacy, old_session, self.NOW + 30))
        self.assertIsNone(gateway.redeem(legacy, old_token, self.NOW))
        previous = gateway.redeem(legacy, unused_token, self.NOW)
        self.assertIsNone(previous.return_code)
        self.assertIsNone(gateway.redeem(legacy, unused_token, self.NOW))
        self.assertEqual(previous.expires, self.NOW + gateway.LEGACY_SESSION_SECONDS)


class LimiteRiscattoTest(unittest.TestCase):
    def test_limite_per_ip_non_occupa_la_quota_globale_e_scade(self):
        limiter = gateway.RedemptionLimit(per_ip=2, total=3, window=60)
        with patch("gateway.time.monotonic", return_value=100) as clock:
            self.assertEqual(limiter.retry_after("192.0.2.1"), 0)
            self.assertEqual(limiter.retry_after("192.0.2.1"), 0)
            self.assertEqual(limiter.retry_after("192.0.2.1"), 60)
            self.assertEqual(limiter.retry_after("192.0.2.2"), 0)
            self.assertEqual(limiter.retry_after("192.0.2.3"), 60)
            self.assertNotIn("192.0.2.3", limiter.by_ip)
            clock.return_value = 159.5
            self.assertEqual(limiter.retry_after("192.0.2.2"), 1)
            clock.return_value = 160
            self.assertEqual(limiter.retry_after("192.0.2.3"), 0)
        self.assertEqual(set(limiter.by_ip), {"192.0.2.3"})

    def test_limite_globale_con_tentativi_concorrenti(self):
        limiter = gateway.RedemptionLimit(per_ip=2, total=3)
        with patch("gateway.time.monotonic", return_value=100), ThreadPoolExecutor(max_workers=6) as pool:
            delays = list(pool.map(limiter.retry_after, (f"192.0.2.{i}" for i in range(1, 7))))
        self.assertEqual(sorted(delays), [0, 0, 0, 60, 60, 60])


if __name__ == "__main__":
    unittest.main()
