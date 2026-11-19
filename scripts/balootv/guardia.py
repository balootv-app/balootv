"""Guardia de datos personales: falla si en el árbol que se publica aparece algo que no debe salir de casa.

Mira los ficheros que sigue Git (o los de una carpeta, con --carpeta, para revisar una exportación), menos los que
`scripts/balootv/exportacion-fuera.txt` deja fuera de la exportación, y busca:
  - correos (salvo los de la lista de permitidos), IPs de red privada (salvo las de ejemplo permitidas) y rutas de
    usuario de Windows (C:\\Users\\…);
  - claves con forma conocida (TorBox en una dirección, clave de API de TMDB, tokens de GitHub, claves privadas);
  - los valores reales de las claves de local.properties, los sitios de las fuentes de casa
    (%USERPROFILE%/.balootv/fuentes-casa.txt), el paquete de la edición casa (%USERPROFILE%/.balootv/casa.properties)
    y los datos de %USERPROFILE%/.balootv/guardia-personal.txt (correo, usuario, nombre, modelo de la tele…), si están
    en este PC. Nunca los imprime: dice fichero y línea;
  - nombres de addons y webs de torrents fuera de las tres reglas de clasificación que los necesitan.
Uso: python -I scripts/balootv/guardia.py [--carpeta <exportación>]
"""
import argparse
import codecs
import fnmatch
import os
import re
import subprocess
import sys
from urllib.parse import urlsplit

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FUERA = os.path.join(ROOT, "scripts", "balootv", "exportacion-fuera.txt")
CLAVES_LOCALES = ["TMDB_API_KEY", "TORBOX_API_KEY", "TRAKT_CLIENT_ID", "TRAKT_CLIENT_SECRET",
                  "BALOOTV_RELEASE_STORE_PASSWORD", "BALOOTV_RELEASE_KEY_PASSWORD"]

# Ejemplos que sí pueden aparecer (documentación y pruebas).
CORREOS_PERMITIDOS = re.compile(
    r"(noreply@anthropic\.com|infobalootv@gmail\.com|[\w.+-]+@(example\.(com|org|net)|ejemplo\.(com|es))|[\w.+-]+@users\.noreply\.github\.com"
    r"|git@github\.com|[\w.+-]+@[\w-]+\.(png|jpg|webp|svg|xml|kt|gradle)|[\w.+-]+@\d+x)$",
    re.I,
)
IPS_PERMITIDAS = {"192.168.1.20", "10.0.0.5", "192.168.1.1", "10.0.2.2", "192.168.0.1", "192.168.1.100"}

PATRONES = [
    # Un correo de verdad: no las etiquetas de Kotlin (this@Clase.x) ni los grupos de las publicaciones (x@RFX).
    ("correo", re.compile(r"(?<![\w@])(?!this@|return@|super@)[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*"
                          r"\.(?:com|es|net|org|io|me|dev|app|eu|tv|info)\b(?!\()")),
    # Octetos sin ceros delante: «10.01.09.16» es una versión, no una IP.
    ("IP privada", re.compile(r"(?<![\d.])(?:192\.168|10\.(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)|172\.(?:1[6-9]|2\d|3[01]))"
                              r"(?:\.(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){2}(?![\d.]*\d)")),
    ("ruta de usuario", re.compile(r"(?i)\b[A-Z]:[\\/]+Users[\\/]+(?!<|%|\{|\$|usuario|tu_usuario|you)\w|/c/Users/(?!<)\w|(?<![\w.])/home/(?!<|usuario)\w")),
    ("clave de TorBox", re.compile(r"(?i)torbox=(?!\{torbox\}|CLAVE|k1|k|clave|abc|vieja|nueva|secreto|\$)[0-9a-f]{8}-[0-9a-f]{4}-")),
    ("clave de TMDB", re.compile(r"(?i)api_key=[0-9a-f]{32}\b")),
    ("token de GitHub", re.compile(r"\b(?:ghp|gho|ghu|ghs|github_pat)_[A-Za-z0-9_]{20,}")),
    ("clave privada", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")),
]

# Nombres de addons y webs de torrents: el árbol publicable no enseña de dónde salen los enlaces. Van en ROT13 para que
# este fichero no los nombre en claro (un `git grep` de la exportación solo los encuentra en los permitidos). Los
# cortos se buscan como palabra suelta; los largos, en cualquier sitio (también pegados a otra palabra).
NOMBRES_FUENTES_LARGOS = ("gbeeragvb crresyvk zrqvnshfvba wnpxrggvb xavtugpenjyre ehgenpxre zrwbegbeerag jbysznk "
                          "qbagbeerag ryvgrgbeerag cpgbeerag cpgsravk ngbzbuq ngbzvkud qvikgbgny 1337k eneot rmgi "
                          "cvengronl alnn gbeeragtnynkl gbeerag9 zveperj oyhqi pbznaqbgbeerag pvarpnyvqnq arjcpg")
NOMBRES_FUENTES_CORTOS = "gbem pbzrg lgf ehgbe"
# Las reglas de clasificación que reconocen esos nombres en el texto de un enlace: quitarlas empeora la detección.
PERMITIDOS_FUENTES = {
    "app/src/main/java/com/nuvio/tv/balootv/fuentes/ClasificadorIdioma.kt",
    "app/src/main/java/com/nuvio/tv/balootv/fuentes/Preferencias.kt",
    "app/src/main/java/com/nuvio/tv/balootv/reproductor/SubtitulosCastellano.kt",
}


def _patron_fuentes():
    largos = [codecs.decode(n, "rot13") for n in NOMBRES_FUENTES_LARGOS.split()]
    cortos = [codecs.decode(n, "rot13") for n in NOMBRES_FUENTES_CORTOS.split()]
    return re.compile("|".join([re.escape(n) for n in largos]
                               + [rf"(?<![a-z0-9]){re.escape(n)}(?![a-z0-9])" for n in cortos]), re.I)


NOMBRE_FUENTE = _patron_fuentes()

BINARIOS = (".png", ".jpg", ".jpeg", ".webp", ".gif", ".ttf", ".otf", ".aar", ".jar", ".a", ".so", ".jks",
            ".keystore", ".apk", ".zip", ".mp4", ".pyc", ".bin", ".pb")


def leer_fuera():
    if not os.path.exists(FUERA):
        return []
    return [l.strip() for l in open(FUERA, encoding="utf-8") if l.strip() and not l.startswith("#")]


def excluido(ruta, fuera):
    ruta = ruta.replace("\\", "/")
    for patron in fuera:
        p = patron.rstrip("/")
        if patron.endswith("/") and (ruta == p or ruta.startswith(p + "/")):
            return True
        if fnmatch.fnmatch(ruta, patron):
            return True
    return False


def ficheros(carpeta):
    if carpeta:
        for base, _, nombres in os.walk(carpeta):
            for n in nombres:
                yield os.path.relpath(os.path.join(base, n), carpeta).replace("\\", "/"), os.path.join(base, n)
    else:
        salida = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=True).stdout
        for r in salida.decode("utf-8").split("\0"):
            if r:
                yield r, os.path.join(ROOT, r)


def secretos_locales():
    """Valores reales que no pueden aparecer nunca (solo en este PC; nunca se imprimen)."""
    valores = {}
    lp = os.path.join(ROOT, "local.properties")
    if os.path.exists(lp):
        for linea in open(lp, encoding="utf-8"):
            if "=" in linea and not linea.startswith("#"):
                k, v = linea.strip().split("=", 1)
                if k in CLAVES_LOCALES and len(v) >= 8:
                    valores[f"valor de {k}"] = v.replace("\\:", ":")
    casa = os.path.join(os.path.expanduser("~"), ".balootv", "fuentes-casa.txt")
    if os.path.exists(casa):
        for i, linea in enumerate(open(casa, encoding="utf-8")):
            linea = linea.strip()
            if linea and not linea.startswith("#"):
                host = urlsplit(linea).hostname
                if host:
                    valores[f"sitio de una fuente de casa (línea {i + 1} de fuentes-casa.txt)"] = host
    casa_props = os.path.join(os.path.expanduser("~"), ".balootv", "casa.properties")
    if os.path.exists(casa_props):
        for linea in open(casa_props, encoding="utf-8"):
            if linea.strip().startswith("paquete=") and len(linea.split("=", 1)[1].strip()) >= 4:
                valores["paquete de la edición casa (casa.properties)"] = linea.split("=", 1)[1].strip()
    personal = os.path.join(os.path.expanduser("~"), ".balootv", "guardia-personal.txt")
    if os.path.exists(personal):
        for i, linea in enumerate(open(personal, encoding="utf-8")):
            linea = linea.strip()
            if linea and not linea.startswith("#"):
                valores[f"dato personal (línea {i + 1} de guardia-personal.txt)"] = linea
    return valores


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    p = argparse.ArgumentParser()
    p.add_argument("--carpeta")
    a = p.parse_args()
    fuera = [] if a.carpeta else leer_fuera()
    secretos = secretos_locales()
    hallazgos = []
    revisados = 0
    for rel, ruta in ficheros(a.carpeta):
        if excluido(rel, fuera) or rel == "scripts/balootv/guardia.py":
            continue
        if rel.lower().endswith(BINARIOS) or not os.path.isfile(ruta):
            continue
        try:
            texto = open(ruta, encoding="utf-8").read()
        except (UnicodeDecodeError, OSError):
            continue
        revisados += 1
        for n, linea in enumerate(texto.splitlines(), 1):
            minus = linea.lower()
            for que, valor in secretos.items():
                if valor.lower() in minus:
                    hallazgos.append(f"{rel}:{n}: {que}")
            for que, patron in PATRONES:
                for m in patron.finditer(linea):
                    t = m.group(0)
                    if que == "correo" and CORREOS_PERMITIDOS.search(t):
                        continue
                    if que == "IP privada" and t in IPS_PERMITIDAS:
                        continue
                    hallazgos.append(f"{rel}:{n}: {que}" + (f" ({t})" if que in ("IP privada",) else ""))
            if rel not in PERMITIDOS_FUENTES and NOMBRE_FUENTE.search(linea):
                hallazgos.append(f"{rel}:{n}: nombre de una fuente (addon o web de torrents)")
    print(f"Guardia: {revisados} ficheros revisados, {len(secretos)} datos de casa buscados.")
    if hallazgos:
        print(f"DATOS PERSONALES ({len(hallazgos)}):")
        for h in hallazgos[:200]:
            print("  " + h)
        if len(hallazgos) > 200:
            print(f"  … y {len(hallazgos) - 200} más")
        sys.exit(1)
    print("Limpio.")


if __name__ == "__main__":
    main()
