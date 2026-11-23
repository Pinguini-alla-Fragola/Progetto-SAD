#!/usr/bin/python3
"""Inviti e sessioni di accesso al sottodominio della demo PICUS."""

import argparse
from collections import deque
from contextlib import closing, contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
from hashlib import sha256
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
from threading import BoundedSemaphore, Lock
import time


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


@dataclass(frozen=True)
class Access:
    session: str
    expires: int
    return_code: str | None = None


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


def redeem_legacy(connection, invite, session, now):
    if invite["riscattato"] is not None or invite["scadenza"] <= now:
        return None
    expires = now + LEGACY_SESSION_SECONDS
    connection.execute("""
        UPDATE inviti SET riscattato = ?, hash_sessione = ?, scadenza_sessione = ? WHERE id = ?
    """, (now, digest(session), expires, invite["id"]))
    return Access(session, expires)


def redeem(path, token, now):
    if not TOKEN_PATTERN.fullmatch(token):
        return None
    session = secrets.token_urlsafe(32)
    token_hash = digest(token)
    with database(path) as connection:
        connection.execute("BEGIN IMMEDIATE")
        invite = connection.execute("""
            SELECT * FROM inviti WHERE (hash_token = ? OR hash_rientro = ?) AND revocato IS NULL
        """, (token_hash, token_hash)).fetchone()
        if invite is None:
            return None
        if invite["durata_ore"] is None:
            return redeem_legacy(connection, invite, session, now)

        return_code = None
        expires = invite["scadenza"]
        if invite["riscattato"] is None:
            if invite["attiva_entro"] <= now:
                return None
            expires = now + invite["durata_ore"] * 60 * 60
            return_code = secrets.token_urlsafe(32)
            connection.execute("""
                UPDATE inviti SET riscattato = ?, scadenza = ?, hash_rientro = ? WHERE id = ?
            """, (now, expires, digest(return_code), invite["id"]))
        if expires <= now:
            return None
        connection.execute("INSERT INTO sessioni_invito VALUES (?, ?)", (digest(session), invite["id"]))
        return Access(session, expires, return_code)


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
    def __init__(self, address, path):
        self.database_path = path
        self.redemption_limit = RedemptionLimit()
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
        if self.path != "/invito/riscatta":
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
            if not isinstance(body, dict) or not isinstance(body.get("token"), str):
                self.respond(400)
                return
        except (ValueError, UnicodeError):
            self.respond(400)
            return
        now = int(time.time())
        try:
            access = redeem(self.server.database_path, body["token"], now)
        except sqlite3.Error:
            logging.exception("Riscatto dell'invito non riuscito")
            self.respond(503)
            return
        if access is None:
            self.respond(410)
            return
        cookie = (f"{COOKIE}={access.session}; Path=/; Max-Age={access.expires - now}; "
                  "Secure; HttpOnly; SameSite=Lax")
        if access.return_code is None:
            self.respond(204, Set_Cookie=cookie)
            return
        body = json.dumps({"codice": access.return_code, "url": invite_url(access.return_code),
                           "scadenza": timestamp(access.expires)}).encode("utf-8")
        self.respond(200, body, "application/json", Set_Cookie=cookie)


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
