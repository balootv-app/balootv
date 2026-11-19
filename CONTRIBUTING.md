# Cómo colaborar

Gracias por echar una mano. BalooTV se diseña para la tele: mando, 1080p y poca memoria.

## Qué se acepta

- Fallos, con pasos para reproducirlos.
- Mejoras pequeñas y acotadas.
- Para cambios grandes o de diseño, abre antes una propuesta y lo hablamos.

No se aceptan fuentes ni enlaces a contenido.

## Compilar

1. Copia `local.example.properties` a `local.properties`.
2. Rellena tu propia clave de TMDB (`TMDB_API_KEY`) y tu `TRAKT_CLIENT_ID`. En Trakt crea una aplicación con redirect
   `urn:ietf:wg:oauth:2.0:oob`.
3. Compila la edición pública:

```
gradlew :app:assemblePublicaDebug
```

Para el emulador de Android TV, añade `-Pabis=x86_64`. La firma (`BALOOTV_RELEASE_*`) solo hace falta para versiones
release.

La edición pública se instala como `app.balootv.tv` (`app.balootv.tv.debug` la de depuración). La edición casa toma
su paquete de `%USERPROFILE%/.balootv/casa.properties` (`paquete=...`, fuera de Git); sin ese fichero usa
`app.balootv.tv.casa`, que se instala al lado de la pública.

## Pruebas

```
bash scripts/balootv/puerta.sh
```

Falla si hay pruebas rotas que no estén ya en `scripts/balootv/fallos-base.txt`.

## Estilo

- Código nuevo, comentarios y textos en castellano de España.
- Commits cortos y en castellano.
- Nada de claves, correos, IPs ni rutas personales en el repositorio: `python -I scripts/balootv/guardia.py` lo
  comprueba.

## Licencia

Al enviar una contribución aceptas que se publique bajo la [GPL-3.0](LICENSE), como el resto del proyecto.
