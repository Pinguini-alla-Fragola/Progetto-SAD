from contextlib import redirect_stderr, redirect_stdout
import io
import struct
from pathlib import Path
from subprocess import CompletedProcess
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
from zlib import crc32

from PIL import Image

import verifica_diagrammi as verifica


class VerificaDiagrammiTest(unittest.TestCase):
    def setUp(self):
        directory = TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        radice = patch.object(verifica, "ROOT", self.root)
        radice.start()
        self.addCleanup(radice.stop)

    def svg(self, nome, contenuto):
        file = self.root / nome
        file.write_text(f'<svg xmlns="http://www.w3.org/2000/svg">{contenuto}</svg>', encoding="utf-8")
        return file

    def png(self):
        file = self.root / "diagramma.png"
        Image.new("RGB", (16, 16), "white").save(file)
        return file

    def test_aggiornamento_include_sorgenti_nuove_e_ignora_anteprime_o_sorgenti_eliminate(self):
        modificato, nuovo, eliminato, invariato = map(Path, ("a.dot", "b.mmd", "c.puml", "d.dot"))
        for file in (modificato, nuovo, invariato):
            (self.root / file).touch()

        with (
            patch.object(verifica.subprocess, "run", return_value=CompletedProcess([], 0)),
            patch.object(verifica, "percorsi_git", side_effect=[{modificato, eliminato, Path("d.svg")}, {nuovo}]),
        ):
            scelti = verifica.diagrammi_da_verificare({modificato, eliminato, invariato}, "HEAD", aggiorna=True)

        self.assertEqual(scelti, {modificato, nuovo})

    def test_aggiornamento_sostituisce_l_anteprima_solo_con_svg_valido(self):
        sorgente = Path("a.dot")
        anteprima = self.svg("a.svg", "<text>Prima</text>")
        (self.root / sorgente).touch()

        def render(content):
            def scrivi(_sorgente, svg, _opzioni):
                svg.write_text(content, encoding="utf-8")
            return scrivi

        with (
            patch.object(verifica.sys, "argv", ["verifica_diagrammi.py", "--aggiorna"]),
            patch.object(verifica, "percorsi_git", return_value={sorgente}),
            patch.object(verifica, "diagrammi_da_verificare", return_value={sorgente}),
            redirect_stdout(io.StringIO()),
            redirect_stderr(io.StringIO()),
        ):
            with patch.object(verifica, "renderizza", side_effect=render("<invalido>")):
                self.assertEqual(verifica.main(), 1)
            self.assertIn("Prima", anteprima.read_text(encoding="utf-8"))
            svg = '<svg xmlns="http://www.w3.org/2000/svg"><text>Dopo</text></svg>'
            with patch.object(verifica, "renderizza", side_effect=render(svg)):
                self.assertEqual(verifica.main(), 0)
            self.assertEqual(anteprima.read_text(encoding="utf-8"), svg)

    def test_mermaid_rileva_transizione_senza_etichetta_rimossa(self):
        freccia = '<path data-et="edge" data-id="edge0" d="M0 0 L10 0" marker-end="url(#fine)"/>'
        generato = self.svg("generato.svg", f"<text>Creata</text>{freccia}<text>Presa in carico</text>")
        self.svg("diagramma.svg", "<text>Creata</text><text>Presa in carico</text>")

        with self.assertRaisesRegex(ValueError, "differisce"):
            verifica.verifica_anteprime(Path("diagramma.mmd"), generato, {Path("diagramma.svg")})

    def test_plantuml_rileva_messaggi_riordinati(self):
        ordine = (
            '<g class="message" data-entity-1="part1" data-entity-2="part2"><text>Verifica</text></g>',
            '<g class="message" data-entity-1="part2" data-entity-2="part3"><text>Salva</text></g>',
        )
        generato = self.svg("generato.svg", "".join(ordine))
        self.svg("diagramma.svg", "".join(reversed(ordine)))

        with self.assertRaisesRegex(ValueError, "differisce"):
            verifica.verifica_anteprime(Path("diagramma.puml"), generato, {Path("diagramma.svg")})

    def test_png_valido_da_solo_non_e_un_anteprima_verificata(self):
        self.png()
        generato = self.svg("generato.svg", "<text>Diagramma MVC</text>")

        with self.assertRaisesRegex(ValueError, "manca l'anteprima SVG"):
            verifica.verifica_anteprime(Path("diagramma.mmd"), generato, {Path("diagramma.png")})

    def test_png_e_svg_validi(self):
        self.png()
        generato = self.svg("generato.svg", "<text>Diagramma MVC</text>")
        self.svg("diagramma.svg", "<text>Diagramma MVC</text>")

        verifica.verifica_anteprime(
            Path("diagramma.mmd"), generato, {Path("diagramma.svg"), Path("diagramma.png")}
        )

    def test_png_con_idat_corrotto_e_crc_errato(self):
        file = self.png()
        dati = bytearray(file.read_bytes())
        idat = dati.index(b"IDAT")
        dati[idat + 4] ^= 1
        file.write_bytes(dati)

        with self.assertRaisesRegex(ValueError, "non è un PNG integro"):
            verifica.verifica_png(file)

    def test_png_con_dati_non_decodificabili_e_crc_valido(self):
        file = self.png()
        dati = file.read_bytes()
        idat = dati.index(b"IDAT")
        lunghezza = int.from_bytes(dati[idat - 4:idat], "big")
        blocco = b"IDAT" + b"\0" * lunghezza
        file.write_bytes(dati[:idat] + blocco + struct.pack(">I", crc32(blocco)) + dati[idat + 8 + lunghezza:])

        with self.assertRaisesRegex(ValueError, "non è un PNG integro"):
            verifica.verifica_png(file)
