"""Verifica le anteprime dei diagrammi o rigenera gli SVG delle sorgenti cambiate."""

import argparse
from collections import Counter
from pathlib import Path
import re
from shutil import which
import subprocess
import sys
from tempfile import TemporaryDirectory
from xml.etree import ElementTree

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SORGENTI = {".mmd", ".puml", ".dot"}
ANTEPRIME = (".svg", ".png")
CONFIGURAZIONE = {Path(".github/workflows/diagrams.yml"), Path("scripts/verifica_diagrammi.py")}
SVG = "{http://www.w3.org/2000/svg}"
FINE_PNG = b"\x00\x00\x00\x00IEND\xaeB\x60\x82"


def percorsi_git(*args):
    output = subprocess.check_output(["git", *args], cwd=ROOT)
    return {Path(nome) for nome in output.decode("utf-8").split("\0") if nome}


def diagrammi_da_verificare(file_versionati, base, aggiorna=False):
    diagrammi = {file for file in file_versionati if file.suffix in SORGENTI}
    if not base or base == "0" * 40:
        if not aggiorna:
            return diagrammi
        antenato = subprocess.run(
            ["git", "merge-base", "HEAD", "origin/main"], cwd=ROOT,
            capture_output=True, text=True,
        )
        base = antenato.stdout.strip() if antenato.returncode == 0 else "HEAD"

    if subprocess.run(
        ["git", "cat-file", "-e", f"{base}^{{commit}}"],
        cwd=ROOT,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    ).returncode:
        if aggiorna:
            raise ValueError(f"Commit base non disponibile: {base}")
        print("Commit base non disponibile. Verifico tutti i diagrammi.")
        return diagrammi

    modificati = percorsi_git("diff", "--name-only", "--no-renames", "-z", base, "--")
    if aggiorna:
        nuovi = percorsi_git("ls-files", "--others", "--exclude-standard", "-z", "--", "docs")
        modificati |= nuovi
        diagrammi |= {file for file in nuovi if file.suffix in SORGENTI}
        diagrammi = {file for file in diagrammi if (ROOT / file).is_file()}
        anteprime_modificate = set()
    else:
        if modificati & CONFIGURAZIONE:
            return diagrammi
        anteprime_modificate = {file.with_suffix("") for file in modificati if file.suffix in ANTEPRIME}

    include_modificati = any(file.suffix == ".iuml" for file in modificati)
    # Una modifica a un include condiviso può riguardare più sorgenti PlantUML.
    return {
        file for file in diagrammi
        if file in modificati or file.with_suffix("") in anteprime_modificate
        or (include_modificati and file.suffix == ".puml")
    }


def renderizza(sorgente, svg, opzioni):
    if sorgente.suffix == ".puml":
        if not opzioni.plantuml_jar:
            raise ValueError("Indica il JAR di PlantUML con --plantuml-jar.")
        comando = [
            "java", "-DPLANTUML_SECURITY_PROFILE=ALLOWLIST", f"-Dplantuml.allowlist.path={ROOT / 'docs'}",
            "-jar", str(opzioni.plantuml_jar.resolve()), "-failfast2", "-charset", "UTF-8",
            "-tsvg", "-o", str(svg.parent), str(sorgente),
        ]
    elif sorgente.suffix == ".dot":
        comando = [opzioni.dot, "-Tsvg", "-o", str(svg), str(sorgente)]
    else:
        comando = [which(opzioni.mmdc) or opzioni.mmdc, "-i", str(sorgente), "-o", str(svg),
                   "-b", "white", "--no-font-embed"]
        if opzioni.puppeteer_config:
            comando.extend(["-p", str(opzioni.puppeteer_config.resolve())])

    subprocess.run(comando, cwd=ROOT, check=True, timeout=90)


def parole_svg(elemento):
    # Le interruzioni di riga possono dipendere dai font del runner.
    return tuple(re.findall(r"\w+|[^\w\s]", " ".join(elemento.itertext())))


def freccia_mermaid(elemento, id_radice):
    # Il prefisso dei marker dipende anche dal nome assegnato al diagramma.
    marker = tuple(
        elemento.get(estremo, "").removeprefix(f"url(#{id_radice}")
        for estremo in ("marker-start", "marker-end")
    )
    return (
        "Mermaid", elemento.get("data-et"), elemento.get("data-id"),
        elemento.get("data-from"), elemento.get("data-to"), *marker,
    )


def contenuto_svg(file):
    radice = ElementTree.parse(file).getroot()
    if radice.tag != SVG + "svg":
        raise ValueError(f"{file.name} non è un SVG")

    testi = Counter()
    collegamenti = Counter()
    messaggi_plantuml = []
    for elemento in radice.iter():
        if elemento.tag in (SVG + "text", SVG + "foreignObject"):
            testi.update(parole_svg(elemento))

        if elemento.tag == SVG + "g" and elemento.get("class") == "edge":
            collegamenti[("DOT", elemento.findtext(SVG + "title", default=""))] += 1
        if "data-entity-1" in elemento.attrib and "data-entity-2" in elemento.attrib:
            etichetta = tuple(parola for testo in elemento.iter(SVG + "text") for parola in parole_svg(testo))
            collegamento = ("PlantUML", elemento.get("data-entity-1"), elemento.get("data-entity-2"), etichetta)
            collegamenti[collegamento] += 1
            if elemento.tag == SVG + "g" and "message" in elemento.get("class", "").split():
                messaggi_plantuml.append(collegamento)
        if elemento.tag in (SVG + "path", SVG + "line") and elemento.get("data-et") in {"edge", "message"}:
            collegamenti[freccia_mermaid(elemento, radice.get("id", ""))] += 1

    return testi, collegamenti, messaggi_plantuml


def verifica_png(file):
    try:
        with file.open("rb") as dati:
            dati.seek(-len(FINE_PNG), 2)
            if dati.read() != FINE_PNG:
                raise ValueError("blocco IEND assente o non valido")
        with Image.open(file, formats=("PNG",)) as immagine:
            immagine.verify()
        with Image.open(file, formats=("PNG",)) as immagine:
            immagine.load()
    except (OSError, SyntaxError, ValueError, Image.DecompressionBombError) as errore:
        raise ValueError(f"{file.relative_to(ROOT)} non è un PNG integro: {errore}") from errore


def verifica_anteprime(sorgente, svg_generato, file_versionati):
    svg = sorgente.with_suffix(".svg")
    if svg not in file_versionati:
        raise ValueError(f"manca l'anteprima SVG versionata: {svg}")
    if contenuto_svg(ROOT / svg) != contenuto_svg(svg_generato):
        raise ValueError(f"{svg} differisce dal diagramma rigenerato. Aggiorna l'esportazione.")

    png = sorgente.with_suffix(".png")
    if png in file_versionati:
        verifica_png(ROOT / png)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", help="Commit base del diff (in aggiornamento, antenato comune con origin/main).")
    parser.add_argument("--aggiorna", action="store_true", help="Rigenera gli SVG delle sorgenti nuove o modificate.")
    parser.add_argument("--plantuml-jar", type=Path, help="Percorso del JAR per i diagrammi PlantUML.")
    parser.add_argument("--mmdc", default="mmdc")
    parser.add_argument("--dot", default="dot")
    parser.add_argument("--puppeteer-config", type=Path)
    opzioni = parser.parse_args()

    file_versionati = percorsi_git("ls-files", "-z", "--", "docs")
    try:
        diagrammi = diagrammi_da_verificare(file_versionati, opzioni.base, opzioni.aggiorna)
    except (subprocess.SubprocessError, ValueError) as errore:
        print(errore, file=sys.stderr)
        return 1

    errori = 0
    azione = "Rigenera" if opzioni.aggiorna else "Verifica"
    for sorgente in sorted(diagrammi):
        print(f"{azione} {sorgente.as_posix()}", flush=True)
        cartella = (ROOT / sorgente).parent if opzioni.aggiorna else None
        try:
            # Genera su un file temporaneo prima di sostituire l'anteprima.
            with TemporaryDirectory(prefix="picus-diagrammi-", dir=cartella) as directory:
                svg = Path(directory) / sorgente.with_suffix(".svg").name
                renderizza(ROOT / sorgente, svg, opzioni)
                if opzioni.aggiorna:
                    contenuto_svg(svg)
                    svg.replace(ROOT / sorgente.with_suffix(".svg"))
                else:
                    verifica_anteprime(sorgente, svg, file_versionati)
        except (OSError, subprocess.SubprocessError, ElementTree.ParseError, ValueError) as errore:
            print(f"{sorgente.as_posix()}: {errore}", file=sys.stderr)
            errori += 1

    esito = "rigenerati" if opzioni.aggiorna else "verificati"
    print(f"Diagrammi {esito}: {len(diagrammi) - errori}/{len(diagrammi)}")
    return 1 if errori else 0


if __name__ == "__main__":
    sys.exit(main())
