"""Scrive nel riepilogo di GitHub Actions i fallimenti riportati da Surefire."""

from collections import defaultdict
from html import escape
from pathlib import Path
import re
from xml.etree import ElementTree


def leggi_report(cartella):
    fallimenti = defaultdict(set)
    report_illeggibili = 0
    for rapporto in sorted(cartella.glob("TEST-*.xml")):
        try:
            suite = ElementTree.parse(rapporto).getroot()
        except (ElementTree.ParseError, OSError):
            report_illeggibili += 1
            continue

        for caso in suite.iter("testcase"):
            if caso.find("failure") is not None or caso.find("error") is not None:
                classe = caso.get("classname") or suite.get("name") or "Classe sconosciuta"
                fallimenti[classe].add(caso.get("name", "Test senza nome"))
    return fallimenti, report_illeggibili


def main():
    fallimenti, report_illeggibili = leggi_report(Path("target/surefire-reports"))
    print("## Esito della build Java\n")
    if not fallimenti:
        print("Nessun test fallito nei report Surefire disponibili.")
        print("Per la causa, apri il log del passaggio **Build e test con Maven Wrapper**.\n")
    else:
        print("Test falliti o terminati con errore:\n")
        for classe, test in sorted(fallimenti.items()):
            for nome in sorted(test):
                print(f"- <code>{escape(classe)}#{escape(nome)}</code>")

        classi = {classe.split("$", 1)[0] for classe in fallimenti}
        selezione = ",".join(
            classe for classe in sorted(classi) if re.fullmatch(r"[\w.]+", classe)
        )
        if selezione:
            print("\nPer ripetere le classi interessate in locale:\n")
            print(f"```bash\nbash ./mvnw '-Dtest={selezione}' test\n```\n")
            print(f"```powershell\n.\\mvnw.cmd '-Dtest={selezione}' test\n```")

    if report_illeggibili:
        print(f"\nReport Surefire illeggibili: {report_illeggibili}.")


if __name__ == "__main__":
    main()
