"""Applica il tema PICUS all'HTML generato dal report JaCoCo."""

import argparse
import re
import shutil
from pathlib import Path


def apply_theme(report):
    repo = Path(__file__).resolve().parents[1]
    styles = repo / "docs/architecture/Javadocs"
    resources = report / "jacoco-resources"
    if not (report / "index.html").is_file() or not (resources / "report.css").is_file():
        raise ValueError(f"Report HTML JaCoCo incompleto in {report}")

    for asset in (
        styles / "picus-theme.css",
        styles / "picus-javadoc-theme.js",
        styles / "picus-coverage.css",
        repo / "docs/assets/picus-logo.svg",
    ):
        shutil.copyfile(asset, resources / asset.name)

    for page in report.rglob("*.html"):
        depth = len(page.relative_to(report).parent.parts)
        assets = "../" * depth + "jacoco-resources"
        html = page.read_text(encoding="utf-8")
        stylesheet = f'<link rel="stylesheet" href="{assets}/picus-coverage.css" type="text/css"/>'
        if stylesheet in html:
            continue
        breadcrumb = '<div class="breadcrumb" id="breadcrumb">'
        if html.count("</head>") != 1 or html.count(breadcrumb) != 1:
            raise ValueError(f"Struttura HTML JaCoCo inattesa in {page}")
        head = (
            '<meta name="viewport" content="width=device-width, initial-scale=1"/>'
            f'<link rel="stylesheet" href="{assets}/picus-theme.css" type="text/css"/>'
            f'{stylesheet}<script src="{assets}/picus-javadoc-theme.js" type="text/javascript"></script>'
        )
        header = (
            '<div class="picus-coverage-header">'
            '<a class="picus-brand" href="https://strawberrypenguin.it/" '
            'aria-label="PICUS, pagina iniziale del progetto">'
            f'<img src="{assets}/picus-logo.svg" width="38" height="38" alt=""/>PICUS</a>'
            '<button type="button" class="picus-theme-toggle" hidden="hidden">Tema chiaro</button>'
            '</div>'
        )
        html = html.replace("</head>", head + "</head>").replace(breadcrumb, header + breadcrumb)
        html = re.sub(
            r'(<table class="coverage"[^>]*>.*?</table>)',
            r'<div class="picus-coverage-table" tabindex="0" role="region" aria-label="Dati di copertura">\1</div>',
            html,
            flags=re.DOTALL,
        )
        page.write_text(html, encoding="utf-8", newline="\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path, help="Cartella del report JaCoCo")
    args = parser.parse_args()
    try:
        apply_theme(args.report)
    except (OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")
