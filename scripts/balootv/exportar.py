"""Exporta el árbol limpio de BalooTV para el repositorio público: sin historial y con la identidad del proyecto.

Copia los ficheros que sigue Git menos los de `scripts/balootv/exportacion-fuera.txt`, pasa el guardia de datos
personales sobre la copia, avisa de imágenes y ficheros grandes y deja un repositorio Git nuevo con un solo commit
firmado por el proyecto (no por la persona). No sube nada: eso se hace a mano cuando exista el repositorio.

Uso:
    python -I scripts/balootv/exportar.py [--destino <carpeta>] [--nombre "BalooTV"] [--correo <correo del proyecto>]
Por defecto deja la exportación en la carpeta hermana `balootv-publico` (fuera de este repositorio).
"""
import argparse
import os
import shutil
import stat
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
import guardia  # noqa: E402  (mismo criterio de qué se queda fuera)

GRANDE = 5 * 1024 * 1024
IMAGENES = (".png", ".jpg", ".jpeg", ".webp", ".gif", ".mp4")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    p = argparse.ArgumentParser()
    p.add_argument("--destino", default=os.path.join(os.path.dirname(ROOT), "balootv-publico"))
    p.add_argument("--nombre", default="BalooTV")
    p.add_argument("--correo", default="infobalootv" + "@gmail.com")  # el correo del proyecto, no el de nadie
    a = p.parse_args()

    destino = os.path.abspath(a.destino)
    if destino.startswith(ROOT + os.sep) or destino == ROOT:
        sys.exit("El destino no puede estar dentro de este repositorio.")
    if os.path.exists(destino):
        marcas = [os.path.join(destino, ".exportacion-balootv"), os.path.join(destino, ".git", "exportacion-balootv")]
        if os.listdir(destino) and not any(os.path.exists(m) for m in marcas):
            sys.exit(f"{destino} ya existe y no es una exportación anterior: no lo toco.")
        def _quitar_solo_lectura(funcion, ruta, _error):
            # En Windows los objetos de Git son de solo lectura y `rmtree` no los borra sin esto.
            os.chmod(ruta, stat.S_IWRITE)
            funcion(ruta)
        # Se vacía sin borrar la carpeta: en Windows no se puede borrar si un terminal está dentro.
        for nombre in os.listdir(destino):
            ruta = os.path.join(destino, nombre)
            if os.name == "nt":
                # Rutas largas: una compilación de prueba dentro de la exportación deja ficheros de más de 260 caracteres.
                ruta = "\\\\?\\" + os.path.abspath(ruta)
            if os.path.isdir(ruta):
                shutil.rmtree(ruta, onexc=_quitar_solo_lectura)
            else:
                _quitar_solo_lectura(os.remove, ruta, None)
    os.makedirs(destino, exist_ok=True)
    open(os.path.join(destino, ".exportacion-balootv"), "w").close()

    fuera = guardia.leer_fuera()
    copiados = 0
    grandes, imagenes = [], []
    for rel, ruta in guardia.ficheros(None):
        if guardia.excluido(rel, fuera) or not os.path.isfile(ruta):
            continue
        dst = os.path.join(destino, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(ruta, dst)
        copiados += 1
        tam = os.path.getsize(ruta)
        if tam > GRANDE:
            grandes.append((rel, tam))
        if rel.lower().endswith(IMAGENES) and "/res/" not in rel:
            imagenes.append(rel)
    print(f"Copiados {copiados} ficheros a {destino}")

    os.remove(os.path.join(destino, ".exportacion-balootv"))
    r = subprocess.run([sys.executable, "-I", os.path.join(HERE, "guardia.py"), "--carpeta", destino])
    open(os.path.join(destino, ".exportacion-balootv"), "w").close()
    if r.returncode != 0:
        sys.exit("La exportación no está limpia: no se crea el repositorio. Arregla lo que dice el guardia y repite.")

    if grandes:
        print("Ficheros de más de 5 MB (revisar si deben ir):")
        for rel, tam in grandes:
            print(f"  {rel} ({tam / 2**20:.1f} MB)")
    if imagenes:
        print(f"Imágenes fuera de res/ ({len(imagenes)}):")
        for rel in imagenes:
            print(f"  {rel}")

    entorno = dict(os.environ, GIT_AUTHOR_NAME=a.nombre, GIT_AUTHOR_EMAIL=a.correo,
                   GIT_COMMITTER_NAME=a.nombre, GIT_COMMITTER_EMAIL=a.correo)
    git = lambda *args: subprocess.run(["git", *args], cwd=destino, env=entorno, check=True, capture_output=True)
    os.remove(os.path.join(destino, ".exportacion-balootv"))
    git("init", "-q", "-b", "main")
    open(os.path.join(destino, ".git", "exportacion-balootv"), "w").close()
    git("add", "-A")
    git("commit", "-q", "-m", "BalooTV: primera versión pública\n\nBasado en NuvioTV (GPL-3.0). Ver README.md y NOTICE.md.")
    print(f"Repositorio nuevo en {destino}: un commit de «{a.nombre} <{a.correo}>», sin remoto. Para subirlo:")
    print("  git -C <destino> remote add origin https://github.com/balootv-app/balootv.git")
    print("  git -C <destino> push -u origin main")


if __name__ == "__main__":
    main()
