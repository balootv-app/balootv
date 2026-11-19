"""Prepara SF Pro para BalooTV fuera de Git: %USERPROFILE%/.balootv/assets/fuentes/sf_pro.ttf.

Las fuentes de Apple no se suben al repositorio. Para regenerarlas:
  1. Descarga SF-Pro.dmg de https://developer.apple.com/fonts/
  2. Con 7-Zip: SF-Pro.dmg → SFProFonts.pkg → SFProFontsPackage.pkg/Payload → Library/Fonts/SF-Pro.ttf
  3. python -I scripts/balootv/fuentes_apple.py <ruta a SF-Pro.ttf>

Recorta la fuente variable (21 MB) a lo que usa la app (~210 KB): anchura normal, pesos 400–800, tamaño óptico
17–28 (Text a Display) y caracteres latinos con la puntuación y los símbolos de la interfaz. Gradle añade esa
carpeta a los assets; sin ella la app usa Inter.
"""
import os
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

DESTINO = os.path.join(os.path.expanduser("~"), ".balootv", "assets", "fuentes", "sf_pro.ttf")

CARACTERES = (list(range(0x20, 0x7F)) + list(range(0xA0, 0x180)) + [0x131] + list(range(0x2C6, 0x2DE))
              + list(range(0x2010, 0x2028)) + list(range(0x2030, 0x203B)) + [0x20AC, 0x2122]
              + list(range(0x2190, 0x2195)) + [0x2212, 0x2265, 0x232B, 0x2423, 0x25B6, 0x2713, 0xFB01, 0xFB02])


def main(origen):
    fuente = instancer.instantiateVariableFont(TTFont(origen), {"wdth": 100, "wght": (400, 800), "opsz": (17, 28)})
    opciones = subset.Options()
    opciones.layout_features = ["kern", "liga", "calt", "ccmp", "locl", "case", "tnum", "pnum", "frac", "mark", "mkmk"]
    opciones.name_IDs = ["*"]
    opciones.notdef_outline = True
    recorte = subset.Subsetter(opciones)
    recorte.populate(unicodes=CARACTERES)
    recorte.subset(fuente)
    os.makedirs(os.path.dirname(DESTINO), exist_ok=True)
    fuente.save(DESTINO)
    print(f"{DESTINO}: {os.path.getsize(DESTINO) // 1024} KB, {len(fuente.getGlyphOrder())} glifos")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
