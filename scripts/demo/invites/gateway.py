#!/usr/bin/python3
"""Inviti e sessioni di accesso al sottodominio della demo PICUS."""

import argparse
from collections import deque
from contextlib import closing, contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
from email.message import EmailMessage
from email.utils import formatdate, make_msgid
from hashlib import sha256
import hmac
from http.cookies import CookieError, SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from ipaddress import ip_address
import json
import logging
from math import ceil
import os
from pathlib import Path
import re
import secrets
import sqlite3
import smtplib
from threading import BoundedSemaphore, Lock
import time

import bcrypt


ORIGIN = "https://demo.picus.strawberrypenguin.it"
ACCOUNT = "picus-invites"
DATABASE = Path("/var/lib/picus-demo-invites/inviti.db")
ASSETS = Path(__file__).resolve().parent
INVITE_ASSETS = {
    "/invito": ("invito.html", "text/html; charset=utf-8"),
    "/invito/script.js": ("invito.js", "text/javascript; charset=utf-8"),
    "/invito/logo.svg": ("logo.svg", "image/svg+xml"),
    "/invito/inter.woff2": ("inter-latin.woff2", "font/woff2"),
}
COOKIE = "__Host-picus_invito"
LEGACY_SESSION_SECONDS = 8 * 60 * 60
ACTIVATION_SECONDS = 48 * 60 * 60
ATTEMPT_WINDOW_SECONDS = 60
ATTEMPTS_PER_IP = 30
TOTAL_ATTEMPTS = 300
TOKEN_PATTERN = re.compile(r"[a-zA-Z0-9_-]{43}")
LAST_TIMESTAMP = 253402300799
OTP_SECONDS = 10 * 60
MAIL_DOMAIN = "mail.picus.strawberrypenguin.it"
MAIL_FROM = f"noreply@{MAIL_DOMAIN}"
OTP_PASSWORD = DATABASE.parent / "otp-smtp.password"
EMAIL_PATTERN = re.compile(r"[a-z0-9_+%-]+(?:[.][a-z0-9_+%-]+)*@"
                           r"(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?[.])+[a-z0-9-]{2,63}")
DUMMY_HASH = bcrypt.hashpw(b"segnaposto per login PICUS", bcrypt.gensalt(rounds=12))



@dataclass(frozen=True)
class Access:
    session: str
    expires: int


@contextmanager
def database(path):
    with closing(sqlite3.connect(path, timeout=3)) as connection, connection:
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        yield connection


def initialize(path):
    with database(path) as connection:
        connection.execute("BEGIN IMMEDIATE")
        connection.execute("""
            CREATE TABLE IF NOT EXISTS inviti (
                id TEXT PRIMARY KEY,
                etichetta TEXT NOT NULL,
                hash_token BLOB NOT NULL UNIQUE,
                scadenza INTEGER NOT NULL,
                riscattato INTEGER,
                revocato INTEGER,
                hash_sessione BLOB UNIQUE,
                scadenza_sessione INTEGER
            )
        """)
        columns = {row["name"] for row in connection.execute("PRAGMA table_info(inviti)")}
        if "durata_ore" not in columns:
            connection.execute("ALTER TABLE inviti ADD COLUMN durata_ore INTEGER")
        if "hash_rientro" not in columns:
            connection.execute("ALTER TABLE inviti ADD COLUMN hash_rientro BLOB")
        if "attiva_entro" not in columns:
            connection.execute("ALTER TABLE inviti ADD COLUMN attiva_entro INTEGER")
        connection.execute("""
            UPDATE inviti SET attiva_entro = ?
            WHERE durata_ore IS NOT NULL AND riscattato IS NULL AND attiva_entro IS NULL
        """, (int(time.time()) + ACTIVATION_SECONDS,))
        connection.execute("CREATE UNIQUE INDEX IF NOT EXISTS inviti_hash_rientro ON inviti(hash_rientro)")
        connection.execute("""
            CREATE TABLE IF NOT EXISTS sessioni_invito (
                hash_sessione BLOB PRIMARY KEY NOT NULL,
                invito_id TEXT NOT NULL REFERENCES inviti(id) ON DELETE CASCADE
            )
        """)
        connection.execute("CREATE INDEX IF NOT EXISTS sessioni_invito_id ON sessioni_invito(invito_id)")
        connection.execute("""
            CREATE TABLE IF NOT EXISTS visitatori (
                email TEXT PRIMARY KEY NOT NULL,
                hash_password BLOB NOT NULL
            )
        """)
        connection.execute("""
            CREATE TABLE IF NOT EXISTS accessi_personali (
                invito_id TEXT PRIMARY KEY REFERENCES inviti(id) ON DELETE CASCADE,
                email TEXT NOT NULL REFERENCES visitatori(email)
            )
        """)
        connection.execute("CREATE INDEX IF NOT EXISTS accessi_personali_email ON accessi_personali(email)")
        connection.execute("""
            CREATE TABLE IF NOT EXISTS registrazioni_demo (
                invito_id TEXT PRIMARY KEY REFERENCES inviti(id) ON DELETE CASCADE,
                hash_verifica BLOB NOT NULL UNIQUE,
                email TEXT NOT NULL,
                hash_password BLOB NOT NULL,
                hash_otp BLOB NOT NULL,
                scadenza INTEGER NOT NULL,
                tentativi INTEGER NOT NULL
            )
        """)
        connection.execute("""
            CREATE TABLE IF NOT EXISTS invii_otp (
                invito_id TEXT NOT NULL REFERENCES inviti(id) ON DELETE CASCADE,
                email TEXT NOT NULL,
                inviato INTEGER NOT NULL
            )
        """)



def digest(value):
    return sha256(value.encode("ascii")).digest()


def invite_url(code):
    return f"{ORIGIN}/invito#{code}"


def new_invite(path, label, hours, now):
    token = secrets.token_urlsafe(32)
    identifier = secrets.token_hex(8)
    activate_by = now + ACTIVATION_SECONDS
    # La scadenza viene fissata solo alla prima conferma. Zero indica un invito inattivo.
    with database(path) as connection:
        connection.execute(
            "INSERT INTO inviti (id, etichetta, hash_token, scadenza, durata_ore, attiva_entro) "
            "VALUES (?, ?, ?, 0, ?, ?)",
            (identifier, label, digest(token), hours, activate_by),
        )
    return {"id": identifier, "durata_ore": hours, "attiva_entro": timestamp(activate_by),
            "scadenza": None, "codice": token, "url": invite_url(token)}



class AccessError(Exception):
    def __init__(self, status, message, retry=0):
        super().__init__(message)
        self.status = status
        self.retry = retry


INVITE_UNAVAILABLE = "Codice scaduto, revocato o già associato a un account."
INVALID_LOGIN = "Email o password errata, oppure accesso scaduto."
INVALID_OTP = "Codice errato o scaduto. Se necessario, richiedine un altro."


def unbound_invite(connection, now, token_hash=None, identifier=None):
    invite = connection.execute("""
        SELECT i.* FROM inviti i LEFT JOIN accessi_personali p ON p.invito_id = i.id
        WHERE i.revocato IS NULL AND p.invito_id IS NULL
          AND (i.id = ? OR i.hash_token = ? OR i.hash_rientro = ?)
    """, (identifier, token_hash, token_hash)).fetchone()
    if invite is None:
        raise AccessError(410, INVITE_UNAVAILABLE)
    if invite["durata_ore"] is None:
        expires = invite["scadenza_sessione"] if invite["riscattato"] else invite["scadenza"]
    else:
        expires = invite["scadenza"] if invite["riscattato"] else invite["attiva_entro"]
    if expires <= now:
        raise AccessError(410, INVITE_UNAVAILABLE)
    return invite


def validate_invite(path, token, now):
    with database(path) as connection:
        unbound_invite(connection, now, token_hash=token_digest(token))


def token_digest(token):
    if not isinstance(token, str) or not TOKEN_PATTERN.fullmatch(token):
        raise AccessError(410, INVITE_UNAVAILABLE)
    return digest(token)


def email_address(value):
    if isinstance(value, str) and len(value) <= 254:
        value = value.strip().lower()
        if EMAIL_PATTERN.fullmatch(value) and len(value.split("@")[0]) <= 64:
            return value
    raise AccessError(400, "Inserisci un indirizzo email valido.")


def password_bytes(value):
    # Stessi criteri di PoliticaPassword e SecurityConfig in PICUS.
    java_spaces = {"\u0085", "\u00a0", "\u2007", "\u202f"}
    if not isinstance(value, str) or all(c.isspace() and c not in java_spaces for c in value):
        raise AccessError(400, "La password è obbligatoria.")
    if len(value) < 12:
        raise AccessError(400, "La password deve contenere almeno 12 caratteri.")
    try:
        data = value.encode("utf-8")
    except UnicodeError:
        raise AccessError(400, "La password contiene caratteri non validi.") from None
    if len(data) > 72:
        raise AccessError(400, "La password può occupare al massimo 72 byte.")
    return data


def otp_digest(challenge, code):
    return hmac.digest(challenge.encode("ascii"), code.encode("ascii"), "sha256")


def start_registration(path, token, email, password, now, deliver):
    email = email_address(email)
    password = password_bytes(password)
    token_hash = token_digest(token)
    with database(path) as connection:
        unbound_invite(connection, now, token_hash=token_hash)
        existing = connection.execute("SELECT hash_password FROM visitatori WHERE email = ?", (email,)).fetchone()
    if existing and bcrypt.checkpw(password, existing["hash_password"]):
        password_hash = existing["hash_password"]
    else:
        password_hash = bcrypt.hashpw(password, bcrypt.gensalt(rounds=12))

    challenge = secrets.token_urlsafe(32)
    challenge_hash = digest(challenge)
    code = f"{secrets.randbelow(1_000_000):06d}"
    with database(path) as connection:
        connection.execute("BEGIN IMMEDIATE")
        invite = unbound_invite(connection, now, token_hash=token_hash)
        identifier = invite["id"]
        connection.execute("DELETE FROM registrazioni_demo WHERE scadenza <= ?", (now,))
        connection.execute("DELETE FROM invii_otp WHERE inviato <= ?", (now - 3600,))
        sends = list(connection.execute("SELECT * FROM invii_otp ORDER BY inviato"))
        recent = [row["inviato"] for row in sends if row["invito_id"] == identifier]
        recipient = [row["inviato"] for row in sends if row["email"] == email]
        limit = max(recent[-1] + 60 - now if recent else 0,
                    recipient[0] + 3600 - now if len(recipient) >= 5 else 0,
                    sends[0]["inviato"] + 3600 - now if len(sends) >= 60 else 0)
        if limit > 0:
            raise AccessError(429, "Attendi prima di richiedere un altro codice.", limit)
        connection.execute("INSERT INTO invii_otp VALUES (?, ?, ?)", (identifier, email, now))
        connection.execute("""
            INSERT OR REPLACE INTO registrazioni_demo
                (invito_id, hash_verifica, email, hash_password, hash_otp, scadenza, tentativi)
            VALUES (?, ?, ?, ?, ?, ?, 0)
        """, (identifier, challenge_hash, email, password_hash, otp_digest(challenge, code), now + OTP_SECONDS))

    try:
        deliver(email, code)
    except (OSError, smtplib.SMTPException):
        with database(path) as connection:
            connection.execute("DELETE FROM registrazioni_demo WHERE hash_verifica = ?", (challenge_hash,))
        raise AccessError(503, "Invio del codice temporaneamente indisponibile. Riprova tra un minuto.") from None
    return challenge


def complete_registration(path, challenge, code, now):
    if not isinstance(challenge, str) or not TOKEN_PATTERN.fullmatch(challenge):
        raise AccessError(400, INVALID_OTP)
    if not isinstance(code, str) or not re.fullmatch(r"[0-9]{6}", code):
        raise AccessError(400, "Inserisci il codice di 6 cifre.")
    with database(path) as connection:
        connection.execute("BEGIN IMMEDIATE")
        pending = connection.execute("""
            SELECT * FROM registrazioni_demo WHERE hash_verifica = ? AND scadenza > ? AND tentativi < 5
        """, (digest(challenge), now)).fetchone()
        if pending is None:
            raise AccessError(400, INVALID_OTP)
        if not hmac.compare_digest(pending["hash_otp"], otp_digest(challenge, code)):
            connection.execute("UPDATE registrazioni_demo SET tentativi = tentativi + 1 WHERE invito_id = ?",
                               (pending["invito_id"],))
            connection.commit()
            raise AccessError(400, INVALID_OTP)

        invite = unbound_invite(connection, now, identifier=pending["invito_id"])
        email = pending["email"]
        password_hash = pending["hash_password"]
        connection.execute("INSERT OR IGNORE INTO visitatori VALUES (?, ?)", (email, password_hash))
        stored = connection.execute("SELECT hash_password FROM visitatori WHERE email = ?", (email,)).fetchone()
        connection.execute("DELETE FROM registrazioni_demo WHERE invito_id = ?", (invite["id"],))
        if not hmac.compare_digest(stored["hash_password"], password_hash):
            connection.commit()
            raise AccessError(403, "Per un indirizzo già registrato, usa la password che hai scelto in precedenza.")

        if invite["riscattato"] is None:
            seconds = LEGACY_SESSION_SECONDS if invite["durata_ore"] is None else invite["durata_ore"] * 3600
            expires = now + seconds
        else:
            expires = invite["scadenza_sessione"] if invite["durata_ore"] is None else invite["scadenza"]
        connection.execute("""
            UPDATE inviti SET riscattato = coalesce(riscattato, ?), scadenza = ?, hash_rientro = NULL
            WHERE id = ?
        """, (now, expires, invite["id"]))
        connection.execute("INSERT INTO accessi_personali VALUES (?, ?)", (invite["id"], email))
        return issue_session(connection, invite["id"], expires)


def issue_session(connection, identifier, expires):
    session = secrets.token_urlsafe(32)
    connection.execute("INSERT INTO sessioni_invito VALUES (?, ?)", (digest(session), identifier))
    return Access(session, expires)


def login(path, email, password, now):
    email = email_address(email)
    password = password_bytes(password)
    with database(path) as connection:
        visitor = connection.execute("SELECT hash_password FROM visitatori WHERE email = ?", (email,)).fetchone()
    if not bcrypt.checkpw(password, visitor["hash_password"] if visitor else DUMMY_HASH) or visitor is None:
        raise AccessError(401, INVALID_LOGIN)
    with database(path) as connection:
        connection.execute("BEGIN IMMEDIATE")
        invite = connection.execute("""
            SELECT i.id, i.scadenza FROM accessi_personali p JOIN inviti i ON i.id = p.invito_id
            WHERE p.email = ? AND i.revocato IS NULL AND i.scadenza > ?
            ORDER BY i.scadenza DESC LIMIT 1
        """, (email, now)).fetchone()
        if invite is None:
            raise AccessError(401, INVALID_LOGIN)
        return issue_session(connection, invite["id"], invite["scadenza"])


def send_otp(email, code):
    password = OTP_PASSWORD.read_text(encoding="ascii").strip()
    if not password:
        raise OSError("Credenziale SMTP mancante")
    message = EmailMessage()
    message["From"] = f"Demo PICUS <{MAIL_FROM}>"
    message["To"] = email
    message["Subject"] = "PICUS | Codice di accesso alla demo"
    message["Date"] = formatdate()
    message["Message-ID"] = make_msgid(domain=MAIL_DOMAIN)
    message.set_content(f"Il tuo codice per confermare l’indirizzo email della demo PICUS è {code}.\n"
                        "Il codice scade tra 10 minuti e può essere usato una sola volta.\n"
                        "Se non hai richiesto un accesso, puoi ignorare questa email.\n")
    with smtplib.SMTP("127.0.0.1", 2525, timeout=4) as client:
        client.login(f"picus-otp@{MAIL_DOMAIN}", password)
        client.send_message(message, from_addr=MAIL_FROM, to_addrs=[email])


def valid_session(path, session, now):
    if not TOKEN_PATTERN.fullmatch(session):
        return False
    with database(path) as connection:
        session_hash = digest(session)
        row = connection.execute("""
            SELECT 1 FROM inviti
            WHERE hash_sessione = ? AND scadenza_sessione > ? AND revocato IS NULL
            UNION ALL
            SELECT 1 FROM sessioni_invito JOIN inviti ON inviti.id = sessioni_invito.invito_id
            WHERE sessioni_invito.hash_sessione = ? AND inviti.scadenza > ? AND inviti.revocato IS NULL
            LIMIT 1
        """, (session_hash, now, session_hash, now)).fetchone()
    return row is not None


def revoke(path, identifier, now):
    with database(path) as connection:
        result = connection.execute("""
            UPDATE inviti SET revocato = coalesce(revocato, ?) WHERE id = ?
        """, (now, identifier))
        return result.rowcount == 1


def timestamp(seconds):
    return datetime.fromtimestamp(seconds, timezone.utc).isoformat()


def invite_status(row, now):
    if row["revocato"] is not None:
        return "revocato"
    if row["durata_ore"] is not None:
        if row["riscattato"] is None:
            return "da attivare" if row["attiva_entro"] > now else "scaduto"
        return "attivo" if row["scadenza"] > now else "scaduto"
    if row["riscattato"] is not None:
        return "sessione attiva" if row["scadenza_sessione"] > now else "sessione scaduta"
    return "da riscattare" if row["scadenza"] > now else "scaduto"


class RedemptionLimit:
    def __init__(self, per_ip=ATTEMPTS_PER_IP, total=TOTAL_ATTEMPTS, window=ATTEMPT_WINDOW_SECONDS):
        self.per_ip = per_ip
        self.total = total
        self.window = window
        self.events = deque()
        self.by_ip = {}
        self.lock = Lock()

    def retry_after(self, client):
        """Registra un tentativo ammesso, oppure restituisce i secondi da attendere."""
        with self.lock:
            now = time.monotonic()
            cutoff = now - self.window
            while self.events and self.events[0][0] <= cutoff:
                _, expired_client = self.events.popleft()
                bucket = self.by_ip[expired_client]
                bucket.popleft()
                if not bucket:
                    del self.by_ip[expired_client]

            bucket = self.by_ip.get(client)
            wait_until = now
            if bucket is not None and len(bucket) >= self.per_ip:
                wait_until = bucket[0] + self.window
            if len(self.events) >= self.total:
                wait_until = max(wait_until, self.events[0][0] + self.window)
            if wait_until > now:
                return ceil(wait_until - now)

            if bucket is None:
                bucket = self.by_ip[client] = deque()
            bucket.append(now)
            self.events.append((now, client))
            return 0


class Gateway(ThreadingHTTPServer):
    def __init__(self, address, path, deliver=send_otp):
        self.database_path = path
        self.redemption_limit = RedemptionLimit()
        self.login_limit = RedemptionLimit(per_ip=8, total=200, window=300)
        self.deliver = deliver
        self.workers = BoundedSemaphore(12)
        super().__init__(address, InviteRequest)

    def process_request(self, request, client_address):
        if not self.workers.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except BaseException:
            self.workers.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.workers.release()


class InviteRequest(BaseHTTPRequestHandler):
    def setup(self):
        self.request.settimeout(5)
        super().setup()

    def log_message(self, *_args):
        # URL e intestazioni non fanno parte dei log dell'accesso.
        pass

    def respond(self, code, body=b"", content_type="text/plain; charset=utf-8", **headers):
        self.send_response(code)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Referrer-Policy", "same-origin")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Security-Policy", "default-src 'none'; script-src 'self'; "
                         "style-src 'unsafe-inline'; connect-src 'self'; img-src 'self'; font-src 'self'; "
                         "base-uri 'none'; "
                         "frame-ancestors 'none'; form-action 'self'")
        if code != 204:
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
        for key, value in headers.items():
            self.send_header(key.replace("_", "-"), value)
        self.end_headers()
        if self.command != "HEAD" and body:
            self.wfile.write(body)

    def do_GET(self):
        route = self.path.partition("?")[0]
        if asset := INVITE_ASSETS.get(route):
            filename, content_type = asset
            self.respond(200, (ASSETS / filename).read_bytes(), content_type)
        elif route == "/verifica":
            self.check_session()
        else:
            self.respond(404)

    do_HEAD = do_GET

    def check_session(self):
        cookie = SimpleCookie()
        try:
            cookie.load("; ".join(self.headers.get_all("Cookie", [])))
        except CookieError:
            cookie.clear()
        candidate = cookie.get(COOKIE)
        try:
            if candidate and valid_session(self.server.database_path, candidate.value, int(time.time())):
                self.respond(204)
                return
        except sqlite3.Error:
            logging.exception("Verifica della sessione non riuscita")
            self.respond(503)
            return

        method = self.headers.get("X-Forwarded-Method", "GET")
        accept = self.headers.get("Accept", "")
        if method in ("GET", "HEAD") and "text/html" in accept:
            self.respond(303, Location="/invito")
        else:
            self.respond(401, b'{"errore":"Invito richiesto o sessione scaduta"}', "application/json")

    def do_POST(self):
        if self.path not in {"/invito/riscatta", "/invito/registra", "/invito/conferma", "/invito/login"}:
            self.respond(404)
            return
        # Caddy sovrascrive questa intestazione prima di inoltrare al servizio locale.
        client = self.headers.get("X-Picus-Client-IP", self.client_address[0])
        try:
            address = ip_address(client)
        except ValueError:
            address = ip_address(self.client_address[0])
        canonical_ip = getattr(address, "ipv4_mapped", None) or address
        delay = self.server.redemption_limit.retry_after(str(canonical_ip))
        if delay:
            self.respond(429, Retry_After=str(delay))
            return
        if self.headers.get("Origin") != ORIGIN:
            self.respond(403)
            return
        if self.headers.get_content_type() != "application/json":
            self.respond(415)
            return
        try:
            length = int(self.headers.get("Content-Length", ""))
            if not 0 < length <= 1024:
                self.respond(413)
                return
            body = json.loads(self.rfile.read(length))
            if not isinstance(body, dict):
                self.respond(400)
                return
        except (ValueError, UnicodeError):
            self.respond(400)
            return
        now = int(time.time())
        try:
            path = self.server.database_path
            if self.path == "/invito/riscatta":
                validate_invite(path, body.get("token"), now)
                self.respond(204)
            elif self.path == "/invito/registra":
                challenge = start_registration(path, body.get("token"), body.get("email"),
                                               body.get("password"), now, self.server.deliver)
                self.json_response(202, {"verifica": challenge})
            elif self.path == "/invito/conferma":
                self.set_access(complete_registration(path, body.get("verifica"), body.get("otp"), now), now)
            else:
                email = email_address(body.get("email"))
                delay = self.server.login_limit.retry_after(email)
                if delay:
                    raise AccessError(429, "Troppi tentativi. Riprova più tardi.", delay)
                self.set_access(login(path, email, body.get("password"), now), now)
        except AccessError as error:
            headers = {"Retry_After": str(error.retry)} if error.retry else {}
            self.json_response(error.status, {"errore": str(error)}, **headers)
        except sqlite3.Error:
            logging.exception("Operazione sull’accesso demo non riuscita")
            self.respond(503)

    def json_response(self, code, body, **headers):
        self.respond(code, json.dumps(body).encode("utf-8"), "application/json", **headers)

    def set_access(self, access, now):
        cookie = (f"{COOKIE}={access.session}; Path=/; Max-Age={access.expires - now}; "
                  "Secure; HttpOnly; SameSite=Lax")
        self.respond(204, Set_Cookie=cookie)



def drop_root():
    if os.getuid() == 0:
        import pwd

        user = pwd.getpwnam(ACCOUNT)
        os.initgroups(ACCOUNT, user.pw_gid)
        os.setgid(user.pw_gid)
        os.setuid(user.pw_uid)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    create = commands.add_parser("crea", help="crea un invito e mostra il link una sola volta")
    create.add_argument("--etichetta", required=True)
    create.add_argument("--ore", type=int, default=24, help="ore di validità dal primo accesso (predefinito: 24)")
    create.add_argument("--json", action="store_true", help="stampa il risultato in formato JSON")
    commands.add_parser("elenca", help="elenca gli inviti e il loro stato, senza i link")
    cancel = commands.add_parser("revoca", help="revoca i codici e tutte le sessioni dell'invito")
    cancel.add_argument("id")
    commands.add_parser("serve", help="avvia il servizio usato da Caddy")
    args = parser.parse_args()

    now = int(time.time())
    if args.command == "crea":
        args.etichetta = args.etichetta.strip()
        if args.ore < 1 or now + args.ore * 3600 > LAST_TIMESTAMP:
            parser.error("specifica un numero intero positivo di ore con una scadenza rappresentabile")
        if not 1 <= len(args.etichetta) <= 80 or not args.etichetta.isprintable():
            parser.error("specifica un'etichetta leggibile fino a 80 caratteri")

    os.umask(0o077)
    drop_root()
    initialize(DATABASE)
    if args.command == "serve":
        with Gateway(("127.0.0.1", 8787), DATABASE) as server:
            server.serve_forever()
    elif args.command == "crea":
        invite = new_invite(DATABASE, args.etichetta, args.ore, now)
        if args.json:
            print(json.dumps(invite))
        else:
            print(f"ID {invite['id']}\nAttiva entro: {invite['attiva_entro']}\n"
                  f"Durata: {invite['durata_ore']} ore dal primo accesso\n"
                  f"Codice: {invite['codice']}\n{invite['url']}")
    elif args.command == "elenca":
        with database(DATABASE) as connection:
            for row in connection.execute("SELECT * FROM inviti ORDER BY scadenza DESC, id"):
                deadline = row["scadenza"] or row["attiva_entro"]
                deadline = timestamp(deadline) if deadline is not None else "-"
                print(f"{row['id']}\t{row['etichetta']}\t{invite_status(row, now)}\t{deadline}")
    elif revoke(DATABASE, args.id, now):
        print(f"Invito revocato: {args.id}")
    else:
        parser.exit(1, "Invito sconosciuto.\n")


if __name__ == "__main__":
    main()
