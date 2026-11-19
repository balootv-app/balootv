"""Comprueba que una APK de la edición pública no lleva nada de casa. Solo dice sí o no: nunca imprime claves.

Busca dentro de la APK (dex, recursos y assets, también comprimidos) las claves de local.properties que no pueden
salir del PC (TorBox y secreto de Trakt), las direcciones de las fuentes de %USERPROFILE%/.balootv/fuentes-casa.txt
y la tipografía de Apple, y comprueba las arquitecturas y el paquete (`app.balootv.tv`, con aapt del SDK).
Uso: python -I scripts/balootv/inspeccionar_apk.py <app.apk> [--arquitecturas armeabi-v7a,arm64-v8a] [--paquete app.balootv.tv]
"""
import argparse
import glob
import os
import re
import subprocess
import sys
import zipfile
from urllib.parse import urlsplit

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PROHIBIDAS = ["TORBOX_API_KEY", "TRAKT_CLIENT_SECRET", "TMDB_API_KEY"]  # TMDB_API_KEY es la de casa; la pública lleva TMDB_API_KEY_PUBLICA


def leer_props(ruta):
    props = {}
    if os.path.exists(ruta):
        for linea in open(ruta, encoding="utf-8"):
            linea = linea.strip()
            if linea and not linea.startswith("#") and "=" in linea:
                k, v = linea.split("=", 1)
                props[k.strip()] = v.strip().replace(chr(92) + ":", ":")
    return props


def hosts_de_casa():
    ruta = os.path.join(os.path.expanduser("~"), ".balootv", "fuentes-casa.txt")
    if not os.path.exists(ruta):
        return []
    hosts = set()
    for linea in open(ruta, encoding="utf-8"):
        linea = linea.strip()
        if linea and not linea.startswith("#"):
            hosts.add(urlsplit(linea).hostname or "")
    return sorted(h for h in hosts if h)


def paquete_apk(apk):
    """El applicationId de la APK según aapt (el de build-tools más nuevo del SDK); None si no hay aapt."""
    sdk = os.environ.get("ANDROID_HOME") or os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk")
    candidatos = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "aapt.exe" if os.name == "nt" else "aapt")))
    if not candidatos:
        return None
    salida = subprocess.run([candidatos[-1], "dump", "badging", apk], capture_output=True, text=True,
                            encoding="utf-8", errors="replace").stdout
    m = re.search(r"package: name='([^']+)'", salida)
    return m.group(1) if m else None


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    p = argparse.ArgumentParser()
    p.add_argument("apk")
    p.add_argument("--arquitecturas", default="armeabi-v7a,arm64-v8a")
    p.add_argument("--paquete", default="app.balootv.tv")
    a = p.parse_args()
    props = leer_props(os.path.join(ROOT, "local.properties"))
    buscar = {f"clave {k}": props[k].encode() for k in PROHIBIDAS if len(props.get(k, "")) >= 8}
    buscar.update({f"fuente de casa n.º {i + 1}": h.encode() for i, h in enumerate(hosts_de_casa())})
    fallos = []
    with zipfile.ZipFile(a.apk) as z:
        nombres = z.namelist()
        for n in nombres:
            datos = z.read(n)
            for que, patron in buscar.items():
                if patron in datos:
                    fallos.append(f"{que} dentro de {n}")
        if any("sf_pro" in n.lower() for n in nombres):
            fallos.append("lleva la tipografía de Apple (SF Pro)")
        abis = sorted({n.split("/")[1] for n in nombres if n.startswith("lib/") and n.count("/") >= 2})
    faltan = [x for x in a.arquitecturas.split(",") if x and x not in abis]
    if faltan:
        fallos.append(f"faltan arquitecturas: {', '.join(faltan)} (lleva {', '.join(abis) or 'ninguna'})")
    paquete = paquete_apk(a.apk)
    if paquete is None:
        fallos.append("no se ha podido leer el paquete (¿falta aapt en build-tools del SDK?)")
    elif paquete != a.paquete:
        fallos.append(f"el paquete es {paquete}, no {a.paquete}")
    print(f"{os.path.basename(a.apk)}: paquete {paquete}, {len(buscar)} datos de casa buscados, arquitecturas {', '.join(abis)}")
    if fallos:
        print("NO SE PUEDE REPARTIR:")
        for f in sorted(set(fallos)):
            print(f"  - {f}")
        sys.exit(1)
    print("Limpia: sin claves, sin fuentes de casa, sin SF Pro y con su paquete.")


if __name__ == "__main__":
    main()
