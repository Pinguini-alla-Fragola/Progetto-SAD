"""Rigenera i diagrammi e verifica le anteprime versionate."""

import argparse
from collections import Counter
from pathlib import Path
import re
import subprocess
import sys
from tempfile import TemporaryDirectory
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[1]
SORGENTI = {".mmd", ".puml", ".dot"}
ANTEPRIME = (".svg", ".png")
CONFIGURAZIONE = {Path(".github/workflows/diagrams.yml"), Path("scripts/verifica_diagrammi.py")}
SVG = "{http://www.w3.org/2000/svg}"


def percorsi_git(*args):
    output = subprocess.check_output(["git", *args], cwd=ROOT)
    return {Path(nome) for nome in output.decode("utf-8").split("\0") if nome}


def diagrammi_da_verificare(file_versionati, base):
    diagrammi = {file for file in file_versionati if file.suffix in SORGENTI}
    if not base or base == "0" * 40:
        return diagrammi

    if subprocess.run(
        ["git", "cat-file", "-e", f"{base}^{{commit}}"],
        cwd=ROOT,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    ).returncode:
        print("Commit base non disponibile. Verifico tutti i diagrammi.")
        return diagrammi

    modificati = percorsi_git("diff", "--name-only", "--no-renames", "-z", base, "--")
    if modificati & CONFIGURAZIONE:
        return diagrammi

    include_modificati = any(file.suffix == ".iuml" for file in modificati)
    anteprime_modificate = {file.with_suffix("") for file in modificati if file.suffix in ANTEPRIME}
    # Una modifica a un include condiviso può riguardare più sorgenti PlantUML.
    return {
        file for file in diagrammi
        if file in modificati or file.with_suffix("") in anteprime_modificate
        or (include_modificati and file.suffix == ".puml")
    }


def renderizza(sorgente, svg, opzioni):
    if sorgente.suffix == ".puml":
        comando = [
            "java", "-DPLANTUML_SECURITY_PROFILE=ALLOWLIST", f"-Dplantuml.allowlist.path={ROOT / 'docs'}",
            "-jar", str(opzioni.plantuml_jar.resolve()), "-failfast2", "-charset", "UTF-8",
            "-tsvg", "-o", str(svg.parent), str(sorgente),
        ]
    elif sorgente.suffix == ".dot":
        comando = [opzioni.dot, "-Tsvg", "-o", str(svg), str(sorgente)]
    else:
        comando = [opzioni.mmdc, "-i", str(sorgente), "-o", str(svg), "-b", "white", "--no-font-embed"]
        if opzioni.puppeteer_config:
            comando.extend(["-p", str(opzioni.puppeteer_config.resolve())])

    subprocess.run(comando, cwd=ROOT, check=True, timeout=90)


def parole_svg(elemento):
    # Le interruzioni di riga possono dipendere dai font del runner.
    return tuple(re.findall(r"\w+|[^\w\s]", " ".join(elemento.itertext())))


def contenuto_svg(file):
    radice = ElementTree.parse(file).getroot()
    if radice.tag != SVG + "svg":
        raise ValueError(f"{file.name} non è un SVG")

    testi = Counter()
    collegamenti = Counter()
    for elemento in radice.iter():
        if elemento.tag in (SVG + "text", SVG + "foreignObject"):
            testi.update(parole_svg(elemento))

        if elemento.tag == SVG + "g" and elemento.get("class") == "edge":
            collegamenti[("DOT", elemento.findtext(SVG + "title", default=""))] += 1
        if "data-entity-1" in elemento.attrib and "data-entity-2" in elemento.attrib:
            etichetta = tuple(parola for testo in elemento.iter(SVG + "text") for parola in parole_svg(testo))
            collegamenti[("PlantUML", elemento.get("data-entity-1"), elemento.get("data-entity-2"), etichetta)] += 1

    return testi, collegamenti


def verifica_anteprime(sorgente, svg_generato, file_versionati):
    anteprime = {sorgente.with_suffix(estensione) for estensione in ANTEPRIME} & file_versionati
    if not anteprime:
        raise ValueError("manca un'anteprima PNG o SVG con lo stesso nome del sorgente")

    contenuto_generato = contenuto_svg(svg_generato)
    for anteprima in sorted(anteprime):
        file = ROOT / anteprima
        if file.suffix == ".svg":
            if contenuto_svg(file) != contenuto_generato:
                raise ValueError(f"{anteprima} differisce dal diagramma rigenerato. Aggiorna l'esportazione.")
        else:
            dati = file.read_bytes()
            if not dati.startswith(b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR") or not dati.endswith(
                b"\x00\x00\x00\x00IEND\xaeB\x60\x82"
            ):
                raise ValueError(f"{anteprima} non è un PNG integro")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", help="Commit base del diff. Se omesso, verifica tutti i diagrammi.")
    parser.add_argument("--plantuml-jar", type=Path, required=True)
    parser.add_argument("--mmdc", default="mmdc")
    parser.add_argument("--dot", default="dot")
    parser.add_argument("--puppeteer-config", type=Path)
    opzioni = parser.parse_args()

    file_versionati = percorsi_git("ls-files", "-z", "--", "docs")
    diagrammi = diagrammi_da_verificare(file_versionati, opzioni.base)
    errori = 0
    with TemporaryDirectory(prefix="picus-diagrammi-") as directory:
        for sorgente in sorted(diagrammi):
            svg = (Path(directory) / sorgente).with_suffix(".svg")
            svg.parent.mkdir(parents=True, exist_ok=True)
            print(f"Verifica {sorgente.as_posix()}", flush=True)
            try:
                renderizza(ROOT / sorgente, svg, opzioni)
                verifica_anteprime(sorgente, svg, file_versionati)
            except (OSError, subprocess.SubprocessError, ElementTree.ParseError, ValueError) as errore:
                print(f"{sorgente.as_posix()}: {errore}", file=sys.stderr)
                errori += 1

    print(f"Diagrammi verificati: {len(diagrammi) - errori}/{len(diagrammi)}")
    return 1 if errori else 0


if __name__ == "__main__":
    sys.exit(main())
