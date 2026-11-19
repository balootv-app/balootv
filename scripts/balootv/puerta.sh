#!/usr/bin/env bash
# Puerta de pruebas: compila y pasa las pruebas unitarias, y falla si hay algún fallo que no esté en fallos-base.txt
# (pruebas heredadas que ya fallaban en este PC antes de BalooTV). Uso: bash scripts/balootv/puerta.sh
cd "$(git rev-parse --show-toplevel)" || exit 2
B=scripts/balootv
W=app/build/puerta
mkdir -p $W
# Antes de gastar minutos de Gradle: ningún dato personal en el árbol que se publica.
python -I scripts/balootv/guardia.py || exit 1
rm -rf app/build/test-results/testCasaDebugUnitTest app/build/test-results/testPublicaDebugUnitTest
# Todas las pruebas con la edicion casa; las de BalooTV, tambien con la publica (que no lleve nada de casa).
./gradlew.bat :app:testCasaDebugUnitTest --continue > $W/ultimas-pruebas.log 2>&1
./gradlew.bat :app:testPublicaDebugUnitTest --tests 'com.nuvio.tv.balootv.*' --continue >> $W/ultimas-pruebas.log 2>&1
if grep -qE "compile[A-Za-z]*(UnitTest)?[A-Za-z]* FAILED|Script compilation error" $W/ultimas-pruebas.log; then echo "NO COMPILA"; grep -E "^e: " $W/ultimas-pruebas.log | head -20; exit 1; fi
python -I - <<'PY' || exit 1
import glob, xml.etree.ElementTree as ET, sys
t = f = 0
# Las dos ediciones tienen que haber pasado sus pruebas (una carpeta vieja no cuenta: se borra antes).
dirs = ["app/build/test-results/testCasaDebugUnitTest", "app/build/test-results/testPublicaDebugUnitTest"]
import os
if not all(os.path.isdir(d) for d in dirs): print("Falta el resultado de alguna edicion"); sys.exit(1)
for p in [x for d in dirs for x in glob.glob(d + "/*.xml")]:
    r = ET.parse(p).getroot(); t += int(r.get("tests")); f += int(r.get("failures")) + int(r.get("errors"))
print(f"Pruebas ejecutadas: {t}, fallos: {f}")
sys.exit(0 if t > 1000 else 1)
PY
grep " FAILED$" $W/ultimas-pruebas.log | grep -v "^> Task" | sed 's/ FAILED$//' | sort -u > $W/fallos-ahora.txt
nuevos=$(comm -13 $B/fallos-base.txt $W/fallos-ahora.txt)
if [ -n "$nuevos" ]; then echo "FALLOS NUEVOS:"; echo "$nuevos"; exit 1; fi
echo "Sin fallos nuevos"
