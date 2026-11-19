"""Desglose por fases de los fotogramas de una app en la tele (dumpsys gfxinfo framestats).

Recorre el Inicio con el mismo guion que `tele.py medir` y reparte el tiempo de cada fotograma en:
entrada+animación, medida/disposición/dibujo (hilo principal), sincronización, envío de órdenes, intercambio
y GPU. Dice qué fase manda en los fotogramas lentos.
Uso: python -I scripts/balootv/fases.py [paquete]
"""
import os
import statistics
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tele  # noqa: E402

COLS = ["Flags", "IntendedVsync", "Vsync", "OldestInputEvent", "NewestInputEvent", "HandleInputStart",
        "AnimationStart", "PerformTraversalsStart", "DrawStart", "SyncQueued", "SyncStart",
        "IssueDrawCommandsStart", "SwapBuffers", "FrameCompleted", "DequeueBufferDuration",
        "QueueBufferDuration", "GpuCompleted"]
FASES = [
    ("retraso hasta empezar", "IntendedVsync", "HandleInputStart"),
    ("entrada+recomposición", "HandleInputStart", "PerformTraversalsStart"),
    ("medir+disponer", "PerformTraversalsStart", "DrawStart"),
    ("dibujar (grabar)", "DrawStart", "SyncQueued"),
    ("espera+sincronizar", "SyncQueued", "IssueDrawCommandsStart"),
    ("enviar órdenes", "IssueDrawCommandsStart", "SwapBuffers"),
    ("intercambio", "SwapBuffers", "FrameCompleted"),
    ("GPU tras intercambio", "FrameCompleted", "GpuCompleted"),
]


def main():
    paquete = sys.argv[1] if len(sys.argv) > 1 else tele.PAQUETE
    tele.conectar()
    tele.adb("shell", "am", "force-stop", paquete)
    tele.abrir(paquete)
    import time
    time.sleep(10)
    tele.adb("shell", "dumpsys", "gfxinfo", paquete, "reset")
    pasos = "; ".join(f"input keyevent KEYCODE_{k}; sleep {0.8 if k == 'DPAD_DOWN' else 0.4}" for k in tele.GUION)
    tele.adb("shell", pasos, timeout=300)
    out = tele.adb("shell", "dumpsys", "gfxinfo", paquete, "framestats")
    filas = []
    for linea in out.splitlines():
        partes = linea.strip().rstrip(",").split(",")
        if len(partes) >= 14 and partes[0].isdigit() and partes[0] == "0":  # Flags 0 = fotograma válido
            filas.append(dict(zip(COLS, map(int, partes[:len(COLS)]))))
    if not filas:
        sys.exit("Sin datos de framestats")
    total = [(f["FrameCompleted"] - f["IntendedVsync"]) / 1e6 for f in filas]
    lentos = [f for f, t in zip(filas, total) if t > 16.7]
    print(f"{paquete}: {len(filas)} fotogramas, {len(lentos)} por encima de 16,7 ms "
          f"({100 * len(lentos) / len(filas):.0f} %), mediana total {statistics.median(total):.1f} ms")
    print(f"{'fase':<22}{'mediana':>9}{'p90':>8}{'mediana en lentos':>20}")
    for nombre, a, b in FASES:
        if b not in COLS or any(f.get(b, 0) == 0 for f in filas[:5]):
            continue
        v = sorted((f[b] - f[a]) / 1e6 for f in filas)
        vl = sorted((f[b] - f[a]) / 1e6 for f in lentos) or [0]
        print(f"{nombre:<22}{statistics.median(v):>8.1f}ms{v[int(len(v) * .9)]:>6.1f}ms{statistics.median(vl):>18.1f}ms")


if __name__ == "__main__":
    main()
