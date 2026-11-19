"""Mide el Inicio con la tecla mantenida (lo que no puede hacer `input keyevent`): baja a la primera fila de carteles
y mantiene la flecha derecha unos segundos, como un dedo en el mando. Inyecta las repeticiones con `monkey`, que sí
lleva el contador de repetición de Android (una cada 50 ms tras 400 ms, como el mando).

Uso: python -I scripts/balootv/rafaga.py [segundos=3] [veces=3]
"""
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from casa import paquete_casa, tele as _tele  # noqa: E402

_HOST, KEY = _tele()
TV = _HOST + ":5555"
PAQUETE = paquete_casa()  # la tele de casa lleva la edición casa
ACTIVIDAD = "com.nuvio.tv.launcher.AppIconDefault"
DERECHA, ABAJO = 22, 20


def adb(*args, timeout=120):
    env = dict(os.environ, ADB_VENDOR_KEYS=KEY, MSYS_NO_PATHCONV="1")
    return subprocess.run(["adb", "-s", TV, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace", env=env, timeout=timeout).stdout


def guion_mantener(codigo, segundos):
    t, lineas = 0, ["type= raw events", "count= 1", "speed= 1.0", "start data >>"]
    lineas.append(f"DispatchKey({t},{t},0,{codigo},0,0,-1,0)")
    lineas.append("UserWait(400)")
    rep, t = 1, 400
    while t < segundos * 1000:
        lineas.append(f"DispatchKey(0,{t},0,{codigo},{rep},0,-1,0)")
        lineas.append("UserWait(50)")
        rep, t = rep + 1, t + 50
    lineas.append(f"DispatchKey(0,{t},1,{codigo},0,0,-1,0)")
    return "\n".join(lineas) + "\n"


def una(segundos):
    adb("shell", "am", "force-stop", PAQUETE)
    time.sleep(1.5)
    adb("shell", "am", "start", "-n", f"{PAQUETE}/{ACTIVIDAD}")
    time.sleep(11)
    # Barra de pestañas -> cabecera -> Seguir viendo -> primera fila de carteles.
    adb("shell", "input keyevent KEYCODE_DPAD_DOWN; sleep 0.8; input keyevent KEYCODE_DPAD_DOWN; sleep 0.8; "
                 "input keyevent KEYCODE_DPAD_DOWN; sleep 1.5")
    with open("rafaga.txt", "w", newline="\n") as f:
        f.write(guion_mantener(DERECHA, segundos))
    adb("push", "rafaga.txt", "/data/local/tmp/rafaga.txt")
    os.remove("rafaga.txt")
    adb("shell", "dumpsys", "gfxinfo", PAQUETE, "reset")
    adb("shell", "monkey", "-f", "/data/local/tmp/rafaga.txt", "1", timeout=60)
    time.sleep(1.5)
    g = adb("shell", "dumpsys", "gfxinfo", PAQUETE)
    num = lambda p: (re.search(p, g) or [None, "?"])[1]
    return {"fotogramas": num(r"Total frames rendered: (\d+)"), "lentos": num(r"Janky frames: \d+ \(([\d.]+)%\)"),
            "p50": num(r"50th percentile: (\d+)ms"), "p95": num(r"95th percentile: (\d+)ms"), "p99": num(r"99th percentile: (\d+)ms")}


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    segundos = float(sys.argv[1]) if len(sys.argv) > 1 else 3
    veces = int(sys.argv[2]) if len(sys.argv) > 2 else 3
    adb("connect", TV)
    for _ in range(veces):
        r = una(segundos)
        print(f"fotogramas {r['fotogramas']} · lentos {r['lentos']} % · p50 {r['p50']} · p95 {r['p95']} · p99 {r['p99']} ms", flush=True)
