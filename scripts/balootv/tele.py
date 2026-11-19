"""Herramienta de BalooTV para la tele de casa por ADB Wi-Fi.

Usa el adb de platform-tools con la clave que la tele ya tiene aceptada, así que no aparece el aviso
"¿Permitir depuración?". El host y la ruta de la clave salen de %USERPROFILE%/.balootv/tele.properties
(ver casa.py). Uso:

    python -I scripts/balootv/tele.py conectar
    python -I scripts/balootv/tele.py instalar [ruta.apk]       # por defecto, el último release; luego la compila
    python -I scripts/balootv/tele.py compilar                  # compila la app instalada con su perfil
    python -I scripts/balootv/tele.py abrir [paquete]
    python -I scripts/balootv/tele.py captura <salida.png>
    python -I scripts/balootv/tele.py medir [paquete] [--salida informe.md]
    python -I scripts/balootv/tele.py shell <orden...>
    python -I scripts/balootv/tele.py fallos [--todo]           # registro local de fallos de BalooTV
    python -I scripts/balootv/tele.py copia                     # baja las preferencias a %USERPROFILE%/.balootv/copias
    python -I scripts/balootv/tele.py restaurar [copia.json]    # por defecto, la última copia; se aplica al abrir la app

`medir` compila la app con su perfil (speed-profile) y repite el guion de la medición de partida
(docs/proyecto/medidas/): 3 arranques en frío y un recorrido fijo por el Inicio. Da tiempos de arranque,
fotogramas lentos, percentiles y memoria.
"""
import argparse
import glob
import os
import re
import statistics
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from casa import paquete_casa, tele as _tele  # noqa: E402

_HOST, KEY = _tele()
TV = _HOST + ":5555"
PAQUETE = paquete_casa()  # la tele de casa lleva la edición casa
# Actividad de arranque de NuvioTV; el código sigue en el espacio de nombres com.nuvio.tv.
ACTIVIDAD = "com.nuvio.tv.launcher.AppIconDefault"


def adb(*args, check=True, timeout=600):
    env = dict(os.environ, ADB_VENDOR_KEYS=KEY)
    res = subprocess.run(["adb", "-s", TV, *args], capture_output=True, text=True, encoding="utf-8",
                         errors="replace", env=env, timeout=timeout)
    if check and res.returncode != 0:
        sys.exit(f"adb {' '.join(args[:2])} falló:\n{res.stdout}{res.stderr}")
    return res.stdout


def conectar():
    env = dict(os.environ, ADB_VENDOR_KEYS=KEY)
    out = subprocess.run(["adb", "connect", TV], capture_output=True, text=True, env=env).stdout
    if "connected" not in out:
        sys.exit(f"No conecta con la tele en {TV}: {out.strip()}\n¿Está encendida y con la depuración ADB activada?")
    return out.strip()


def ultimo_apk():
    # La tele de casa lleva siempre la edición casa (la pública no trae sus fuentes ni su clave).
    apks = glob.glob(os.path.join(ROOT, "app", "build", "outputs", "apk", "casa", "release", "*armeabi-v7a*.apk"))
    if not apks:
        sys.exit("No hay APK compilado. Compila con: gradlew.bat :app:assembleCasaRelease")
    return max(apks, key=os.path.getmtime)


def instalar(apk):
    conectar()
    apk = apk or ultimo_apk()
    if "publica" in os.path.basename(apk).lower():
        sys.exit("Esa es la edición pública: a la tele de casa solo va la edición casa.")
    print(f"Instalando {os.path.basename(apk)} ({os.path.getsize(apk) / 2**20:.1f} MB)…")
    out = adb("install", "-r", apk, check=False)
    if "Success" not in out:
        sys.exit(f"La instalación falló:\n{out}")
    version = re.search(r"versionName=(\S+)", adb("shell", "dumpsys", "package", PAQUETE))
    print(f"Instalado {PAQUETE} {version.group(1) if version else ''}")
    compilar()


def compilar():
    """Sin esto, la versión recién instalada corre con el JIT hasta la compilación nocturna de Android (una vez al
    día, con la tele en reposo): el 24-10-2026 el Inicio dio 32–45 % de fotogramas lentos sin compilar y 12–16 %
    compilado, con el JIT gastando 18 s de CPU en 43 s. Se abre la app para que ProfileInstaller deje el perfil
    de referencia y Android apunte lo que usa el arranque, y se compila con ese perfil."""
    print("Compilando con su perfil (abre la app ~15 s; no pulses nada)…")
    abrir(PAQUETE)
    time.sleep(15)
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(3)
    adb("shell", "am", "force-stop", PAQUETE)
    adb("shell", "cmd", "package", "compile", "-m", "speed-profile", "-f", PAQUETE, timeout=900)
    estado = re.search(r"\[" + re.escape(PAQUETE) + r"\][^\[]*\[status=([^\]]+)\]",
                       adb("shell", "dumpsys", "package", "dexopt"))
    print(f"Compilado: {estado.group(1) if estado else '?'}")


def abrir(paquete):
    conectar()
    actividad = ACTIVIDAD if paquete == PAQUETE else adb("shell", "cmd", "package", "resolve-activity", "--brief",
                                                           "-c", "android.intent.category.LEANBACK_LAUNCHER",
                                                           paquete).strip().splitlines()[-1].split("/")[-1]
    if actividad.startswith("."):
        actividad = paquete.replace(".debug", "") + actividad
    out = adb("shell", "am", "start", "-W", "-n", f"{paquete}/{actividad}")
    t = re.search(r"TotalTime: (\d+)", out)
    return int(t.group(1)) if t else None


def captura(salida):
    conectar()
    os.makedirs(os.path.dirname(os.path.abspath(salida)), exist_ok=True)
    adb("shell", "screencap", "-p", "/data/local/tmp/balootv-shot.png")
    adb("pull", "/data/local/tmp/balootv-shot.png", salida)
    adb("shell", "rm", "-f", "/data/local/tmp/balootv-shot.png")
    print(f"Captura en {salida}")


# Recorrido fijo, idéntico al de la medición de partida de Nuvio 1.0.0 (21-10-2026).
GUION = (["DPAD_RIGHT"] * 8 + (["DPAD_DOWN"] + ["DPAD_RIGHT"] * 6) * 4 + ["DPAD_UP"] * 4)


def comprobar_accesibilidad():
    """Sin AccesibilidadCompose activa la medición no vale (~35 % más de trabajo en el hilo principal)."""
    log = adb("shell", "logcat", "-d", "-s", "BalooTV/Accesibilidad")
    m = re.findall(r"desactivada en (\d+) vista", log)
    if not m or int(m[-1]) < 1:
        sys.exit("AccesibilidadCompose no se aplicó (0 vistas Compose): medición cancelada.\n" + log[-400:])
    print(f"AccesibilidadCompose activa en {m[-1]} vista(s)")


def medir(paquete, salida):
    conectar()
    # Mismas condiciones en cada medición: código compilado con su perfil, como tras unos días de uso.
    adb("shell", "cmd", "package", "compile", "-m", "speed-profile", "-f", paquete, timeout=900)
    arranques = []
    for _ in range(3):
        adb("shell", "am", "force-stop", paquete)
        time.sleep(2)
        if paquete == PAQUETE:
            adb("shell", "logcat", "-c")
        arranques.append(abrir(paquete))
    time.sleep(10)  # deja que cargue el contenido del Inicio
    if paquete == PAQUETE:
        comprobar_accesibilidad()
    adb("shell", "dumpsys", "gfxinfo", paquete, "reset")
    pasos = []
    for tecla in GUION:
        pasos.append(f"input keyevent KEYCODE_{tecla}; sleep {0.8 if tecla == 'DPAD_DOWN' else 0.4}")
    adb("shell", "; ".join(pasos), timeout=300)
    gfx = adb("shell", "dumpsys", "gfxinfo", paquete)
    mem = adb("shell", "dumpsys", "meminfo", paquete)

    def num(patron, texto, grupo=1):
        m = re.search(patron, texto)
        return m.group(grupo) if m else "?"

    filas = [
        ("Arranque en frío (3 intentos)", " · ".join(f"{a / 1000:.2f} s" for a in arranques if a)),
        ("Mediana de arranque", f"{statistics.median([a for a in arranques if a]) / 1000:.2f} s" if any(arranques) else "?"),
        ("Fotogramas", num(r"Total frames rendered: (\d+)", gfx)),
        ("Fotogramas lentos", f"{num(r'Janky frames: (\d+)', gfx)} ({num(r'Janky frames: \d+ \(([\d.]+)%\)', gfx)} %)"),
        ("p50 / p90 / p95 / p99", " · ".join(num(fr"{p}th percentile: (\d+)ms", gfx) + " ms" for p in (50, 90, 95, 99))),
        ("Memoria PSS total", f"{int(num(r'TOTAL(?: PSS)?:\s+(\d+)', mem)) // 1024} MB" if num(r'TOTAL(?: PSS)?:\s+(\d+)', mem) != "?" else "?"),
        ("Gráficos / nativa / Java", " · ".join(
            f"{int(num(fr'{k}:\s+(\d+)', mem)) // 1024} MB" if num(fr'{k}:\s+(\d+)', mem) != "?" else "?"
            for k in ("Graphics", "Native Heap", "Java Heap"))),
    ]
    informe = [f"## {paquete} · {time.strftime('%Y-%m-%d %H:%M')}", "", "| Métrica | Valor |", "|---|---|"]
    informe += [f"| {k} | {v} |" for k, v in filas]
    texto = "\n".join(informe) + "\n"
    print(texto)
    if salida:
        with open(salida, "a", encoding="utf-8") as f:
            f.write("\n" + texto)
        print(f"Añadido a {salida}")


REGISTRO = f"/sdcard/Android/data/{PAQUETE}/files/registro"


COPIA = f"/sdcard/Android/data/{PAQUETE}/files/copia"
COPIAS_PC = os.path.join(os.path.expanduser("~"), ".balootv", "copias")


def copia():
    """Baja preferencias.json de la tele a %USERPROFILE%/.balootv/copias con la fecha en el nombre."""
    conectar()
    os.makedirs(COPIAS_PC, exist_ok=True)
    destino = os.path.join(COPIAS_PC, time.strftime("preferencias-%Y%m%d-%H%M%S.json"))
    adb("pull", f"{COPIA}/preferencias.json", destino)
    print(f"Copia guardada en {destino}")


def restaurar(origen):
    """Sube una copia como restaurar.json y reabre BalooTV para que la aplique."""
    origen = origen or max(glob.glob(os.path.join(COPIAS_PC, "preferencias-*.json")), default=None)
    if not origen:
        sys.exit(f"No hay copias en {COPIAS_PC}")
    conectar()
    adb("shell", "mkdir", "-p", COPIA)
    adb("push", origen, f"{COPIA}/restaurar.json")
    adb("shell", "am", "force-stop", PAQUETE)
    abrir(PAQUETE)
    print(f"Restaurada {os.path.basename(origen)}: BalooTV la aplica al arrancar.")


def fallos(todo):
    """Imprime el registro de fallos de la tele (el anterior rotado también con --todo)."""
    conectar()
    nombres = ["fallos.1.txt", "fallos.txt"] if todo else ["fallos.txt"]
    texto = "".join(adb("shell", "cat", f"{REGISTRO}/{n}", check=False) for n in nombres)
    print(texto.strip() or "Sin fallos registrados.")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="orden", required=True)
    sub.add_parser("conectar")
    s = sub.add_parser("instalar"); s.add_argument("apk", nargs="?")
    sub.add_parser("compilar")
    s = sub.add_parser("abrir"); s.add_argument("paquete", nargs="?", default=PAQUETE)
    s = sub.add_parser("captura"); s.add_argument("salida")
    s = sub.add_parser("medir"); s.add_argument("paquete", nargs="?", default=PAQUETE); s.add_argument("--salida")
    s = sub.add_parser("shell"); s.add_argument("resto", nargs=argparse.REMAINDER)
    s = sub.add_parser("fallos"); s.add_argument("--todo", action="store_true")
    sub.add_parser("copia")
    s = sub.add_parser("restaurar"); s.add_argument("origen", nargs="?")
    a = p.parse_args()
    if a.orden == "conectar":
        print(conectar())
    elif a.orden == "instalar":
        instalar(a.apk)
    elif a.orden == "compilar":
        conectar()
        compilar()
    elif a.orden == "abrir":
        print(f"Arranque: {abrir(a.paquete)} ms")
    elif a.orden == "captura":
        captura(a.salida)
    elif a.orden == "medir":
        medir(a.paquete, a.salida)
    elif a.orden == "copia":
        copia()
    elif a.orden == "restaurar":
        restaurar(a.origen)
    elif a.orden == "fallos":
        fallos(a.todo)
    elif a.orden == "shell":
        conectar()
        print(adb("shell", *a.resto, check=False))


if __name__ == "__main__":
    main()
