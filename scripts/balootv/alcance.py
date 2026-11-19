"""Código Kotlin sin usar: ficheros que no se alcanzan desde las raíces de la app.

Uso: python -I scripts/balootv/alcance.py [app/src/main]   (deja la lista en app/build/alcance/sin_alcanzar.txt)

Raíces: MainActivity, NuvioApplication, componentes del AndroidManifest y módulos de Hilt (@Module). Un fichero
alcanza a otro si usa como palabra alguno de los nombres de nivel superior que el otro declara. Es una
sobreestimación de lo alcanzable (nombres repetidos cuentan), así que lo que sale sin alcanzar es casi seguro
código muerto; lo alcanzable puede seguir teniendo partes muertas.
"""
import os
import re
import sys
from collections import defaultdict

RAIZ = sys.argv[1] if len(sys.argv) > 1 else "app/src/main"
JAVA = os.path.join(RAIZ, "java")
ficheros = []
for d, _, fs in os.walk(JAVA):
    for f in fs:
        if f.endswith(".kt") or f.endswith(".java"):
            ficheros.append(os.path.join(d, f))

DECL = re.compile(r"^(?:(?:public|internal|private|protected|abstract|open|sealed|data|enum|inline|value|annotation|"
                  r"fun\s+interface|const|lateinit|suspend|operator|infix|tailrec|external|override|actual|expect)\s+)*"
                  r"(?:class|object|interface|fun|val|var|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(`?\w+`?)", re.M)
texto = {}
nombres = defaultdict(set)  # nombre -> ficheros que lo declaran
for p in ficheros:
    t = open(p, encoding="utf-8", errors="replace").read()
    texto[p] = t
    for m in DECL.finditer(t):
        n = m.group(1).strip("`")
        if len(n) > 2:
            nombres[n].add(p)
    # La clase con el nombre del fichero también cuenta.
    nombres[os.path.splitext(os.path.basename(p))[0]].add(p)

PALABRA = re.compile(r"\b[A-Za-z_]\w*\b")
usa = {}
for p, t in texto.items():
    sin_comentarios = t  # sin quitar comentarios: «video/*» en un texto se comía código
    ids = set(PALABRA.findall(sin_comentarios))
    destino = set()
    for i in ids:
        for q in nombres.get(i, ()):
            if q != p:
                destino.add(q)
    usa[p] = destino

manifiesto = open(os.path.join(RAIZ, "AndroidManifest.xml"), encoding="utf-8").read()
clases_manifiesto = {c.split(".")[-1] for c in re.findall(r'android:name="([\w.$]+)"', manifiesto)}
raices = set()
for p, t in texto.items():
    base = os.path.splitext(os.path.basename(p))[0]
    if base in ("MainActivity", "NuvioApplication") or base in clases_manifiesto or re.search(r"^@Module", t, re.M):
        raices.add(p)
# También el fichero que declara una clase del manifiesto con otro nombre (AppIconActivities.kt: AppIconDefault…).
for c in clases_manifiesto:
    raices |= nombres.get(c, set())
# Recursos XML que nombran clases (p. ej. vistas personalizadas o proveedores).
for d, _, fs in os.walk(os.path.join(RAIZ, "res")):
    for f in fs:
        if f.endswith(".xml"):
            x = open(os.path.join(d, f), encoding="utf-8", errors="replace").read()
            for c in re.findall(r"com\.nuvio\.tv\.[\w.]+", x):
                for q in nombres.get(c.split(".")[-1], ()):
                    raices.add(q)

visto, pila = set(raices), list(raices)
while pila:
    p = pila.pop()
    for q in usa[p]:
        if q not in visto:
            visto.add(q)
            pila.append(q)

muertos = sorted(set(ficheros) - visto)
lineas = lambda p: texto[p].count("\n") + 1
total = sum(lineas(p) for p in ficheros)
print(f"Ficheros: {len(ficheros)} ({total} líneas) · raíces: {len(raices)} · sin alcanzar: {len(muertos)} "
      f"({sum(lineas(p) for p in muertos)} líneas)")
por_carpeta = defaultdict(lambda: [0, 0])
for p in muertos:
    rel = os.path.relpath(p, os.path.join(JAVA, "com", "nuvio", "tv")).replace("\\", "/")
    carpeta = "/".join(rel.split("/")[:3]) if rel.count("/") >= 2 else rel
    por_carpeta[carpeta][0] += 1
    por_carpeta[carpeta][1] += lineas(p)
for c, (n, l) in sorted(por_carpeta.items(), key=lambda x: -x[1][1])[:45]:
    print(f"{l:7d} líneas  {n:3d} ficheros  {c}")
salida = os.path.join(os.path.dirname(RAIZ.rstrip("/\\")), "..", "build", "alcance")
os.makedirs(salida, exist_ok=True)
with open(os.path.join(salida, "sin_alcanzar.txt"), "w", encoding="utf-8") as f:
    for p in muertos:
        f.write(f"{lineas(p)}\t{os.path.relpath(p, RAIZ)}\n")
