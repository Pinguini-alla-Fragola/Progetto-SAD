"""Controlla i prerequisiti locali per sviluppare e avviare PICUS."""

import os
from pathlib import Path
import re
import socket
import subprocess
import sys


RADICE = Path(__file__).resolve().parents[1]


def esegui(*comando):
    try:
        esito = subprocess.run(
            comando, cwd=RADICE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            encoding="utf-8", errors="replace", timeout=10,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    return esito.stdout if esito.returncode == 0 else None


def versione(comando, formato):
    risultato = re.search(formato, esegui(*comando) or "")
    return risultato[1] if risultato else None


def porta_libera():
    with socket.socket() as porta:
        try:
            porta.bind(("127.0.0.1", 8080))
            return True
        except OSError:
            return False


def main():
    java_home = os.environ.get("JAVA_HOME")
    java = Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java") if java_home else "java"
    java_major = versione((java, "-version"), r'\b(?:openjdk|java)\s+(?:version\s+)?"?(\d+)')
    node_major = versione(("node", "--version"), r"^v(\d+)")
    compose = esegui("docker", "compose", "version") is not None
    # Compose verifica le variabili con le stesse regole usate all'avvio.
    configurazione = compose and esegui("docker", "compose", "-f", "docker-compose.yml", "config", "--quiet") is not None
    esiti = [
        ("Java 25", java_major == "25", f"Rilevata versione {java_major or 'sconosciuta'}. Imposta JAVA_HOME sul JDK 25."),
        ("Node.js 24", node_major == "24", f"Rilevata versione {node_major or 'sconosciuta'}. Installa Node.js 24."),
        ("Docker Compose", compose, "Installa il plugin Docker Compose o Docker Desktop."),
        ("Motore Docker", esegui("docker", "info", "--format", "{{.ServerVersion}}") is not None,
         "Avvia Docker e controlla il contesto attivo con docker context ls."),
        ("Configurazione Compose", configurazione,
         "Controlla Compose e imposta PICUS_DB_PASSWORD e PICUS_DEMO_PASSWORD come in .env.example."),
        ("Porta locale 8080", porta_libera(), "Controlla quale applicazione usa la porta 8080 prima di avviare PICUS."),
    ]
    for nome, riuscito, rimedio in esiti:
        print(f"OK      {nome}" if riuscito else f"ERRORE  {nome}. {rimedio}")
    return 0 if all(riuscito for _, riuscito, _ in esiti) else 1


if __name__ == "__main__":
    sys.exit(main())
