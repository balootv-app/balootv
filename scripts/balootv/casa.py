"""Datos de la casa que no van en Git: la tele (host y clave ADB) y las fuentes de la edición casa.

- `%USERPROFILE%/.balootv/casa.properties`: `paquete=<applicationId de la edición casa>` (el de la app instalada en
  la tele de casa; `BALOOTV_PAQUETE_CASA` del entorno va por encima). Sin él, el neutro `app.balootv.tv.casa`, igual
  que en `app/build.gradle.kts`.
- `%USERPROFILE%/.balootv/tele.properties`: `host=<IP de la tele>` y `adbkey=<ruta de la clave ADB que la tele ya
  acepta>`. `TV_HOST` y `BALOOTV_ADB_KEY` del entorno van por encima.
- `%USERPROFILE%/.balootv/fuentes-casa.txt`: una plantilla por línea (`{torbox}` se cambia por la clave de TorBox),
  `#` para comentarios. Para dar nombre a una fuente (carpetas de datos de prueba) se pone justo antes de su
  línea un comentario `# nombre: mifuente`; sin él, la fuente se llama `fuente<posición>`.
"""
import base64
import os
import re
import sys

BALOOTV = os.path.join(os.path.expanduser("~"), ".balootv")
TELE_PROPERTIES = os.path.join(BALOOTV, "tele.properties")
FUENTES_CASA = os.path.join(BALOOTV, "fuentes-casa.txt")
CASA_PROPERTIES = os.path.join(BALOOTV, "casa.properties")
PAQUETE_PUBLICA = "app.balootv.tv"


def _propiedades(ruta):
    valores = {}
    if os.path.isfile(ruta):
        with open(ruta, encoding="utf-8") as f:
            for linea in f:
                linea = linea.strip()
                if linea and not linea.startswith("#") and "=" in linea:
                    k, v = linea.split("=", 1)
                    valores[k.strip()] = v.strip()
    return valores


def paquete_casa():
    """applicationId de la edición casa, con el mismo criterio que app/build.gradle.kts."""
    return (os.environ.get("BALOOTV_PAQUETE_CASA") or _propiedades(CASA_PROPERTIES).get("paquete")
            or PAQUETE_PUBLICA + ".casa").strip()


def tele():
    """Devuelve (host, ruta de la clave ADB); sale con un mensaje claro si falta algo."""
    props = _propiedades(TELE_PROPERTIES)
    host = os.environ.get("TV_HOST") or props.get("host")
    clave = os.environ.get("BALOOTV_ADB_KEY") or props.get("adbkey")
    if not host or not clave:
        sys.exit(f"Falta la configuración de la tele. Crea {TELE_PROPERTIES} con dos líneas:\n"
                 "  host=<IP de la tele>\n  adbkey=<ruta de la clave ADB que la tele ya acepta>\n"
                 "(o define TV_HOST y BALOOTV_ADB_KEY en el entorno).")
    return host, clave


def _b64_sin_relleno(texto):
    return base64.urlsafe_b64encode(texto.encode()).decode().rstrip("=")


def _sustituir(plantilla, clave):
    """Cambia {torbox} por la clave; si está dentro de un tramo base64 (JSON), decodifica, cambia y recodifica."""
    def tramo(m):
        trozo = m.group(0)
        try:
            crudo = base64.urlsafe_b64decode(trozo + "=" * (-len(trozo) % 4)).decode()
        except Exception:  # noqa: BLE001 - no era base64
            return trozo
        if "{torbox}" not in crudo:
            return trozo
        return _b64_sin_relleno(crudo.replace("{torbox}", clave))
    # Primero los tramos base64 que contienen {torbox} al decodificar; después el texto plano.
    resultado = re.sub(r"[A-Za-z0-9_-]{16,}", tramo, plantilla)
    return resultado.replace("{torbox}", clave)


def fuentes_casa(clave):
    """Devuelve {nombre: URL base sin /manifest.json} con la clave de TorBox ya puesta."""
    if not os.path.isfile(FUENTES_CASA):
        sys.exit(f"Falta {FUENTES_CASA} (una plantilla de fuente por línea, con {{torbox}} donde va la clave).")
    fuentes, nombre = {}, None
    with open(FUENTES_CASA, encoding="utf-8") as f:
        for linea in f:
            linea = linea.strip()
            if not linea:
                continue
            if linea.startswith("#"):
                m = re.match(r"#\s*nombre:\s*(\S+)", linea)
                if m:
                    nombre = m.group(1)
                continue
            url = _sustituir(linea, clave)
            if url.endswith("/manifest.json"):
                url = url[: -len("/manifest.json")]
            fuentes[nombre or f"fuente{len(fuentes) + 1}"] = url
            nombre = None
    return fuentes
