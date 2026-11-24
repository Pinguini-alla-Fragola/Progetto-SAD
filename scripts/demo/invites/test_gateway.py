from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

import gateway


class InvitiTest(unittest.TestCase):
    NOW = 1_800_000_000
    EMAIL = "collaudo@picus.test"
    PASSWORD = "password visita 🐧"

    def setUp(self):
        directory = TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.path = Path(directory.name) / "inviti.db"
        gateway.initialize(self.path)
        self.invite = gateway.new_invite(self.path, "revisore test", 1, self.NOW)
        self.token = self.invite["codice"]
        self.mail = []

    def send(self, email, code):
        self.mail.append((email, code))

    def start(self, *, now=None, token=None, password=None):
        return gateway.start_registration(self.path, token or self.token, self.EMAIL,
                                          password or self.PASSWORD, self.NOW if now is None else now, self.send)

    def check(self, challenge, elapsed=0, code=None):
        return gateway.complete_registration(self.path, challenge, self.mail[-1][1] if code is None else code,
                                             self.NOW + elapsed)

    def test_invio_e_conferma_concorrente_con_codice_monouso(self):
        gateway.validate_invite(self.path, self.token, self.NOW)
        challenge = self.start()
        self.assertEqual(self.mail[0][0], self.EMAIL)
        self.assertRegex(self.mail[0][1], r"^[0-9]{6}$")

        def redeem(_):
            try:
                return self.check(challenge, 180)
            except gateway.AccessError:
                return None

        with ThreadPoolExecutor(max_workers=6) as pool:
            results = list(pool.map(redeem, range(6)))
        sessions = [access for access in results if access is not None]
        self.assertEqual(len(sessions), 1)
        [access] = sessions
        self.assertEqual(access.expires, self.NOW + 180 + 3600)
        self.assertTrue(gateway.valid_session(self.path, access.session, self.NOW + 181))
        self.assertFalse(gateway.valid_session(self.path, access.session, access.expires))
        with self.assertRaises(gateway.AccessError):
            gateway.validate_invite(self.path, self.token, self.NOW + 181)
        with gateway.database(self.path) as connection:
            row = connection.execute("SELECT hash_password FROM visitatori").fetchone()
            self.assertTrue(row[0].startswith(b"$2b$12$"))
            self.assertIsNone(connection.execute("SELECT 1 FROM registrazioni_demo").fetchone())

    def test_email_password_login_e_revoca(self):
        challenge = self.start()
        with self.assertRaises(gateway.AccessError):
            gateway.login(self.path, self.EMAIL, self.PASSWORD, self.NOW)
        first = self.check(challenge, 5)
        self.assertTrue(gateway.valid_session(self.path, first.session, self.NOW + 6))
        second = gateway.login(self.path, self.EMAIL.upper(), self.PASSWORD, self.NOW + 6)
        self.assertEqual(first.expires, second.expires)
        with self.assertRaises(gateway.AccessError):
            gateway.login(self.path, self.EMAIL, "password errata", self.NOW + 7)
        self.assertTrue(gateway.revoke(self.path, self.invite["id"], self.NOW + 8))
        self.assertFalse(gateway.valid_session(self.path, second.session, self.NOW + 8))
        with self.assertRaises(gateway.AccessError):
            gateway.login(self.path, self.EMAIL, self.PASSWORD, self.NOW + 8)

    def test_otp_scadenza_tentativi_e_nuovo_invio(self):
        first = self.start()
        with self.assertRaises(gateway.AccessError) as cooldown:
            self.start(now=self.NOW + 5)
        self.assertEqual(cooldown.exception.retry, 55)
        wrong = "000000" if self.mail[-1][1] != "000000" else "000001"
        for _ in range(5):
            with self.assertRaises(gateway.AccessError):
                self.check(first, 11, wrong)
        with self.assertRaises(gateway.AccessError):
            self.check(first, 12)
        second = self.start(now=self.NOW + 61)
        with self.assertRaises(gateway.AccessError):
            self.check(second, 61 + gateway.OTP_SECONDS)
        self.assertEqual(len(self.mail), 2)

    def test_email_verificata_non_sovrascrive_la_password_di_un_account(self):
        self.check(self.start(), 1)
        renewed = gateway.new_invite(self.path, "rinnovo", 2, self.NOW + 2)
        different = self.start(now=self.NOW + 2, token=renewed["codice"], password="password diversa")
        with self.assertRaises(gateway.AccessError):
            self.check(different, 3)
        same = self.start(now=self.NOW + 63, token=renewed["codice"])
        later_access = self.check(same, 64)
        self.assertEqual(later_access.expires, self.NOW + 64 + 7200)

    def test_scadenza_e_revoca_valgono_anche_dopo_invio_otp(self):
        deadline = self.NOW + gateway.ACTIVATION_SECONDS
        self.assertEqual(self.invite["attiva_entro"], gateway.timestamp(deadline))
        challenge = self.start(now=deadline - 1)
        with self.assertRaises(gateway.AccessError):
            self.check(challenge, gateway.ACTIVATION_SECONDS)
        another = gateway.new_invite(self.path, "da revocare", 1, self.NOW)
        challenge = self.start(token=another["codice"])
        self.assertTrue(gateway.revoke(self.path, another["id"], self.NOW + 20))
        with self.assertRaises(gateway.AccessError):
            self.check(challenge, 21)

    def test_criteri_password_unicode(self):
        self.assertEqual(gateway.password_bytes("🐧" * 18), ("🐧" * 18).encode("utf-8"))
        for value in ("a" * 11, "🐧" * 19, " " * 13):
            with self.subTest(value=value), self.assertRaises(gateway.AccessError):
                gateway.password_bytes(value)

    def test_errore_email_non_riscatta_invito(self):
        with self.assertRaises(gateway.AccessError) as failed:
            gateway.start_registration(self.path, self.token, self.EMAIL, self.PASSWORD,
                                       self.NOW, lambda *_: (_ for _ in ()).throw(OSError()))
        self.assertEqual(failed.exception.status, 503)
        gateway.validate_invite(self.path, self.token, self.NOW + 1)
        with gateway.database(self.path) as connection:
            self.assertIsNone(connection.execute("SELECT 1 FROM registrazioni_demo").fetchone())

    def test_migrazione_conserva_sessioni_e_inviti_attivi_senza_prolungarli(self):
        legacy = self.path.with_name("legacy.db")
        old_token, old_session, unused_token = (letter * 43 for letter in "abc")
        with gateway.database(legacy) as connection:
            connection.execute("""
                CREATE TABLE inviti (
                    id TEXT PRIMARY KEY, etichetta TEXT NOT NULL, hash_token BLOB NOT NULL UNIQUE,
                    scadenza INTEGER NOT NULL, riscattato INTEGER, revocato INTEGER,
                    hash_sessione BLOB UNIQUE, scadenza_sessione INTEGER
                )
            """)
            connection.execute("INSERT INTO inviti VALUES (?, ?, ?, ?, ?, NULL, ?, ?)", (
                "gia-attivo", "precedente", gateway.digest(old_token), self.NOW + 3600,
                self.NOW - 10, gateway.digest(old_session), self.NOW + 300,
            ))
            connection.execute("INSERT INTO inviti (id, etichetta, hash_token, scadenza) VALUES (?, ?, ?, ?)", (
                "non-usato", "precedente", gateway.digest(unused_token), self.NOW + 3600,
            ))
        gateway.initialize(legacy)
        gateway.initialize(legacy)
        self.assertTrue(gateway.valid_session(legacy, old_session, self.NOW))
        self.assertFalse(gateway.valid_session(legacy, old_session, self.NOW + 300))
        gateway.validate_invite(legacy, unused_token, self.NOW)
        challenge = gateway.start_registration(legacy, old_token, self.EMAIL, self.PASSWORD, self.NOW, self.send)
        access = gateway.complete_registration(legacy, challenge, self.mail[-1][1], self.NOW + 1)
        self.assertEqual(access.expires, self.NOW + 300)

    def test_migrazione_non_rinnova_la_finestra_degli_inviti_in_attesa(self):
        with gateway.database(self.path) as connection:
            connection.execute("ALTER TABLE inviti DROP COLUMN attiva_entro")
        with patch("gateway.time.time", return_value=self.NOW + 3600) as clock:
            gateway.initialize(self.path)
            clock.return_value += 3600
            gateway.initialize(self.path)
        with gateway.database(self.path) as connection:
            row = connection.execute("SELECT * FROM inviti").fetchone()
        deadline = self.NOW + 49 * 3600
        self.assertEqual(row["attiva_entro"], deadline)
        self.assertEqual(gateway.invite_status(row, deadline - 1), "da attivare")
        self.assertEqual(gateway.invite_status(row, deadline), "scaduto")


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
