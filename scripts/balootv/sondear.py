"""Reproduce títulos en la tele y anota qué llega a la pantalla y a la barra (F5). SUENA: solo con el dueño delante.

Para cada título abre la ficha por enlace, pulsa OK (BalooTV elige solo), deja sonar unos segundos y
apunta el fichero elegido, la pista de vídeo, el decodificador, la decisión de Dolby Vision y el formato
que el audio entrega a la salida (AC-3/DTS directos o PCM decodificado).
Uso: python -I sondear.py <segundos> tt0088763 [series/tt0903747 ...]
"""
import os
import re
import subprocess
import sys
import time
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from casa import paquete_casa, tele as _tele  # noqa: E402

_HOST, KEY = _tele()
TV = _HOST + ":5555"
PAQUETE = paquete_casa()  # la tele de casa lleva la edición casa
ENV = dict(os.environ, ADB_VENDOR_KEYS=KEY)
ESPERA_FICHA_S = 14
LIMITE_S = 60


def adb(*args):
    return subprocess.run(["adb", "-s", TV, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", env=ENV, timeout=90).stdout


def salida_audio():
    """Formato de los hilos de salida activos (no en reposo) de audioflinger."""
    texto = adb("shell", "dumpsys", "media.audio_flinger")
    formatos = []
    # Solo hilos vivos: los cerrados aparecen en el historial con cada línea empezando por "- ".
    for bloque in re.split(r"(?m)^Output thread ", texto)[1:]:
        cabecera, _, resto = bloque.partition("\n")
        bloque = cabecera + "\n" + re.split(r"(?m)^\S", resto, maxsplit=1)[0]
        if re.search(r"Standby: no", bloque):
            tipo = re.search(r"type \d+ \((\w+)\)", bloque)
            fmt = re.search(r"HAL format: \S+ \((\w+)\)", bloque)
            canales = re.search(r"Channel count: (\d+)", bloque)
            formatos.append(f"{tipo.group(1) if tipo else '?'}:{fmt.group(1) if fmt else '?'}"
                            f"/{canales.group(1) if canales else '?'}ch")
    return ", ".join(formatos) or "ninguno activo"


def una(enlace, segundos):
    adb("shell", "am", "force-stop", PAQUETE)
    time.sleep(1)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", f"nuvio://{enlace}", PAQUETE)
    time.sleep(ESPERA_FICHA_S)
    adb("shell", "input", "keyevent", "KEYCODE_DPAD_CENTER")
    t0 = time.monotonic()
    while time.monotonic() - t0 < LIMITE_S:
        time.sleep(3)
        if "VIDEO_DECODER" in adb("logcat", "-d", "-s", "PlayerViewModel:I"):
            break
    time.sleep(segundos)
    audio = salida_audio()
    log = adb("logcat", "-d", "-v", "brief")
    adb("shell", "am", "force-stop", PAQUETE)

    def buscar(patron):
        m = re.findall(patron, log)
        return m[-1].strip() if m else "—"

    url = buscar(r"Resolved stream mimeType=\S+ for url=(\S+)")
    fichero = urllib.parse.unquote(url.rsplit("/", 1)[-1]) if url != "—" else "—"
    return {
        "enlace": enlace,
        "fichero": fichero,
        "video": buscar(r"VIDEO_TRACK: (mime=\S+ codecs=\S+ size=\S+)"),
        "decodificador": buscar(r"VIDEO_DECODER: (name=\S+ software=\S+)"),
        "dv": buscar(r"DV7_AUTO: (decision=\S+ effectiveMode=\S+)"),
        "audio_salida": audio,
        "reproduce": "sí" if "isPlaying=true" in log else "NO",
        "errores": len(re.findall(r"E/(?:ExoPlayerImplInternal|PlayerViewModel)", log)),
    }


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    segundos = int(sys.argv[1])
    for enlace in sys.argv[2:]:
        if "/" not in enlace:
            enlace = f"movie/{enlace}"
        r = una(enlace, segundos)
        print("\n".join(f"  {k}: {v}" for k, v in r.items()), flush=True)
        print(flush=True)
