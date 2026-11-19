"""Mide en la tele, sin sonido, cuánto tarda Reproducir en crear el reproductor desde la ficha.

Abre la ficha por enlace (arranque en frío), espera, pulsa OK y cronometra hasta la línea "Init" de
ExoPlayerImpl; en ese instante cierra la app, antes de que el reproductor tenga datos para sonar.
Uso: python -I medir_arranque.py <repeticiones> [--captura fichero.png]
"""
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from casa import paquete_casa, tele as _tele  # noqa: E402

_HOST, KEY = _tele()
TV = _HOST + ":5555"
PAQUETE = paquete_casa()  # la tele de casa lleva la edición casa
ENLACE = "nuvio://movie/tt0088763"
ENV = dict(os.environ, ADB_VENDOR_KEYS=KEY)
ESPERA_ARRANQUE_S = 12
ESPERA_FICHA_S = 3
LIMITE_S = 45


def adb(*args):
    return subprocess.run(["adb", "-s", TV, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", env=ENV, timeout=60).stdout


def parar():
    adb("shell", "am", "force-stop", PAQUETE)


def una(captura=None):
    parar()
    time.sleep(2)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", ENLACE, PAQUETE)
    time.sleep(ESPERA_ARRANQUE_S + ESPERA_FICHA_S)
    if captura:
        adb("shell", "screencap", "-p", "/sdcard/balootv_medida.png")
        adb("pull", "/sdcard/balootv_medida.png", captura)
        parar()
        return None
    log = subprocess.Popen(["adb", "-s", TV, "logcat", "-T", "1", "-v", "time"] + ([] if os.environ.get("TODO_EL_LOG") else ["ExoPlayerImpl:I", "*:S"]),
                           stdout=subprocess.PIPE, text=True, encoding="utf-8", errors="replace", env=ENV)
    try:
        log.stdout.readline()  # -T 1 repite la última línea anterior
        adb("shell", "input", "keyevent", "KEYCODE_DPAD_CENTER")
        t0 = time.monotonic()
        while time.monotonic() - t0 < LIMITE_S:
            linea = log.stdout.readline()
            if os.environ.get("TODO_EL_LOG"):
                open("log_medida.txt", "a", encoding="utf-8").write(linea)
            if "ExoPlayerImpl" in linea and "Init" in linea:
                t = time.monotonic() - t0
                parar()
                print("  ", linea.strip()[:110])
                return t
        return None
    finally:
        parar()
        log.kill()


if __name__ == "__main__":
    n = int(sys.argv[1])
    captura = sys.argv[3] if len(sys.argv) > 3 and sys.argv[2] == "--captura" else None
    if captura:
        una(captura)
        print("captura en", captura)
        sys.exit(0)
    tiempos = [una() for _ in range(n)]
    print("segundos hasta crear el reproductor:", ["—" if t is None else f"{t:.1f}" for t in tiempos])
