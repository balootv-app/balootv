"""Prepara una versión de la edición pública para GitHub Releases. No sube nada.

Compila `publica` (32 y 64 bits), comprueba cada APK con inspeccionar_apk.py (sin claves de casa, sin fuentes, sin
SF Pro), las copia con el nombre que espera el actualizador (`BalooTV-<versión>-<arquitectura>.apk`) a
`%USERPROFILE%/.balootv/publicaciones/<versión>/`, junto a sus SHA-256 y un borrador de notas, y escribe la orden
`gh release create` para subirlas cuando exista el repositorio público. La firma es la de siempre (la clave vive
fuera del repositorio): sin CI que la necesite en GitHub.

Uso: python -I scripts/balootv/publicar.py --claves-del-proyecto [--sin-compilar]
`--claves-del-proyecto` confirma que local.properties lleva las claves de TMDB y Trakt del proyecto, no las de
casa: una APK pública lleva dentro la de TMDB y el id de Trakt.
"""
import argparse
import glob
import hashlib
import os
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ABIS = ["armeabi-v7a", "arm64-v8a"]


def version():
    texto = open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8").read()
    m = re.search(r'versionName\s*=\s*"([^"]+)"', texto)
    return m.group(1) if m else sys.exit("No encuentro versionName en app/build.gradle.kts")


def sha256(ruta):
    h = hashlib.sha256()
    with open(ruta, "rb") as f:
        for trozo in iter(lambda: f.read(1 << 20), b""):
            h.update(trozo)
    return h.hexdigest()


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    p = argparse.ArgumentParser()
    p.add_argument("--claves-del-proyecto", action="store_true")
    p.add_argument("--sin-compilar", action="store_true")
    p.add_argument("--repo", default="", help="organizacion/repositorio para la orden de gh (por defecto, gradle.properties)")
    a = p.parse_args()
    if not a.claves_del_proyecto:
        sys.exit("Falta --claves-del-proyecto: confirma que local.properties lleva las claves de TMDB y Trakt del "
                 "proyecto. Con las de casa, la APK solo sirve para probar en este PC y no se reparte.")

    v = version()
    if not a.sin_compilar:
        gradlew = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
        print(f"Compilando BalooTV {v} (edición pública)…")
        r = subprocess.run([gradlew, ":app:assemblePublicaRelease", "-q"], cwd=ROOT)
        if r.returncode != 0:
            sys.exit("La compilación ha fallado.")

    salida = os.path.join(os.path.expanduser("~"), ".balootv", "publicaciones", v)
    os.makedirs(salida, exist_ok=True)
    sumas = []
    for abi in ABIS:
        apks = glob.glob(os.path.join(ROOT, "app", "build", "outputs", "apk", "publica", "release", f"*-{abi}-release.apk"))
        if not apks:
            sys.exit(f"No hay APK pública de {abi}.")
        apk = max(apks, key=os.path.getmtime)
        r = subprocess.run([sys.executable, "-I", os.path.join(HERE, "inspeccionar_apk.py"), apk, "--arquitecturas", abi])
        if r.returncode != 0:
            sys.exit(f"La APK de {abi} no se puede repartir.")
        destino = os.path.join(salida, f"BalooTV-{v}-{abi}.apk")
        shutil.copy2(apk, destino)
        sumas.append(f"{sha256(destino)}  {os.path.basename(destino)}")
        print(f"  {os.path.basename(destino)} ({os.path.getsize(destino) / 2**20:.1f} MB)")
    # La web enlaza releases/latest/download/BalooTV.apk: la de 32 bits, que se instala en cualquier tele ARM.
    fija = os.path.join(salida, "BalooTV.apk")
    shutil.copy2(os.path.join(salida, f"BalooTV-{v}-armeabi-v7a.apk"), fija)
    sumas.append(f"{sha256(fija)}  BalooTV.apk")
    open(os.path.join(salida, "SHA256SUMS.txt"), "w", encoding="utf-8", newline="\n").write("\n".join(sumas) + "\n")
    notas = os.path.join(salida, "NOTAS.md")
    if not os.path.exists(notas):
        open(notas, "w", encoding="utf-8", newline="\n").write(
            f"## BalooTV {v}\n\n- (Qué cambia, en una línea por cosa.)\n\n"
            "**Qué APK bajar:** `arm64-v8a` para Google TV, Chromecast con Google TV, Fire TV recientes y Shield; "
            "`armeabi-v7a` para el resto. Si no sabes, prueba primero `arm64-v8a`: si no se instala, la otra.\n"
        )
    repo = a.repo
    if not repo:
        m = re.search(r"^balootvRepoPublico=(.*)$", open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8").read(), re.M)
        repo = (m.group(1).strip() if m else "") or "<organizacion>/<repositorio>"
    print(f"Listo en {salida}. Revisa NOTAS.md y súbela con:")
    print(f'  gh release create v{v} --repo {repo} --title "BalooTV {v}" --notes-file "{notas}" '
          + " ".join(f'"{os.path.join(salida, f"BalooTV-{v}-{abi}.apk")}"' for abi in ABIS)
          + f' "{fija}" "{os.path.join(salida, "SHA256SUMS.txt")}"')


if __name__ == "__main__":
    main()
