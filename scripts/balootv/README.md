# Herramientas de BalooTV

Todas se lanzan desde la raíz del repositorio con `python -I scripts/balootv/<script>` (o `bash` para `puerta.sh`).
Las que hablan con la tele usan ADB por Wi-Fi (host y clave en `%USERPROFILE%\.balootv\tele.properties`; `TV_HOST` del entorno va por encima) con la clave que la tele ya
acepta; ver `CLAUDE.md`.

## Día a día

| Herramienta | Para qué |
|---|---|
| `local_properties.py` | Genera `local.properties` desde `%USERPROFILE%\.balootv\secrets.properties` (claves, firma, addons de serie) |
| `tele.py` | `instalar` (y compilar con su perfil), `compilar`, `abrir`, `captura`, `medir`, `fallos`, `copia`, `restaurar`, `shell` |
| `puerta.sh` | Pruebas unitarias; falla si aparece un fallo que no esté en `fallos-base.txt` (pruebas heredadas que ya fallaban o fallan a ratos en este PC) |
| `fuentes_apple.py` | Regenera la SF Pro recortada fuera de Git (`%USERPROFILE%\.balootv\assets\fuentes`) |

## Medir y analizar

| Herramienta | Para qué |
|---|---|
| `tele.py medir` | Fluidez, memoria y arranque con el guion fijo de la medición de partida |
| `rafaga.py` | Fluidez con la tecla mantenida (inyecta repeticiones con `monkey`) |
| `fases.py` | Reparte el tiempo de cada fotograma por fases (`gfxinfo framestats`) |
| `medir_arranque.py` | De pulsar Reproducir a crear el reproductor, sin sonido |
| `sondear.py` | Reproduce títulos y apunta fichero, decodificador y salida de audio. **Suena**: solo con alguien delante y la barra silenciada |
| `alcance.py` | Ficheros Kotlin a los que no llega nada (código muerto seguro) |
| `video/` | Fotogramas, mosaicos y seguimiento vertical de un `screenrecord` (rebotes, saltos) |

## Banco de fuentes

| Herramienta | Para qué |
|---|---|
| `banco/titulos.py` | Resuelve con TMDB los 30 títulos del banco (`banco.json`) |
| `banco/recoger.py` | Guarda las respuestas reales de cada fuente como datos de prueba (`app/src/test/resources/balootv/fuentes/`) |
| `banco/analizar.py` | Primer análisis del banco (prototipo del clasificador de la F2) |
