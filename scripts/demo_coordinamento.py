"""Avvia PICUS e prepara il database dedicato alla demo di coordinamento."""

import argparse
from contextlib import closing
from http.client import HTTPConnection, HTTPException
from pathlib import Path
import subprocess
import sys
from time import monotonic, sleep


RADICE = Path(__file__).resolve().parents[1]
PROGETTO = "picus-demo-coordinamento"
COMPOSE = ("docker", "compose", "-f", "docker-compose.yml", "-p", PROGETTO)
MAILPIT = ("-f", "docker-compose.mailpit.yml", "--profile", "mailpit")
PSQL = ("exec", "-T", "picus-db", "psql", "-X", "-U", "picus", "-d", "picus", "-v", "ON_ERROR_STOP=1")


def compose(*argomenti, mailpit=False, **opzioni):
    configurazione = COMPOSE + MAILPIT if mailpit else COMPOSE
    return subprocess.run([*configurazione, *argomenti], cwd=RADICE, check=True, **opzioni)


def login_disponibile():
    with closing(HTTPConnection("127.0.0.1", 8080, timeout=2)) as connessione:
        try:
            connessione.request("GET", "/login")
            return connessione.getresponse().status == 200
        except (OSError, HTTPException):
            return False


def attendi_demo():
    scadenza = monotonic() + 90
    while monotonic() < scadenza:
        if login_disponibile():
            # Il profilo demo crea gli account in una transazione dopo l'avvio HTTP.
            esito = compose(
                *PSQL, "-At", "-c",
                "select exists (select 1 from utenze_account where email = 'banco@picus.test' and ruolo = 'BANCO')",
                stdout=subprocess.PIPE, text=True, timeout=10,
            )
            if esito.stdout.strip() == "t":
                return
        sleep(2)
    raise TimeoutError("PICUS non è pronto. Controlla i log del progetto della demo.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mailpit", action="store_true", help="Avvia anche la casella email locale.")
    opzioni = parser.parse_args()
    sql = (RADICE / "scripts/db/demo-coordinamento.sql").read_bytes()
    try:
        compose("up", "--build", "--detach", mailpit=opzioni.mailpit)
        print("Attendo l'avvio di PICUS e degli account demo...", flush=True)
        attendi_demo()
        compose(*PSQL, input=sql, timeout=30)
    except FileNotFoundError:
        print("Docker non trovato. Installa e avvia Docker con Docker Compose.", file=sys.stderr)
        return 1
    except (subprocess.SubprocessError, TimeoutError) as errore:
        print(f"Preparazione della demo interrotta: {errore}", file=sys.stderr)
        configurazione = COMPOSE + MAILPIT if opzioni.mailpit else COMPOSE
        print(f"Log: {' '.join(configurazione)} logs picus-webapp", file=sys.stderr)
        return 1

    print("Demo pronta su http://localhost:8080")
    if opzioni.mailpit:
        print("Casella email locale su http://localhost:8025")
    return 0


if __name__ == "__main__":
    sys.exit(main())
