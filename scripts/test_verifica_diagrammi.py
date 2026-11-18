import struct
from pathlib import Path
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
