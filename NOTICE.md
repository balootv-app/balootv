# Créditos y licencias

BalooTV se distribuye bajo la **GPL-3.0** (ver [`LICENSE`](LICENSE)). Si repartes una versión modificada, tiene que
seguir siendo GPL-3.0 y con su código disponible.

## De dónde viene

BalooTV es un fork de [NuvioTV](https://github.com/NuvioMedia/NuvioTV) 1.1.0-beta.7 (commit en `UPSTREAM_COMMIT`),
GPL-3.0. Conserva su reproductor y buena parte de su base; el resto se ha rehecho. Gracias a quienes lo hacen.

## Componentes de terceros

| Componente | Para qué | Licencia |
|---|---|---|
| [AndroidX Media3 / ExoPlayer](https://github.com/androidx/media) (incluidas las AAR de `app/libs/`) | Reproductor | Apache-2.0 |
| [FFmpeg](https://ffmpeg.org) (extensión de Media3 y `nextlib-mediainfo`) | Decodificar audio y leer pistas | LGPL-2.1 o posterior |
| `ffmpeg-decoder-downmix` | Mezcla a estéreo; adapta código de [Kodi](https://github.com/xbmc/xbmc) (ver su `NOTICE.md`) | Apache-2.0 y GPL-2.0 o posterior |
| [libdovi](https://github.com/quietvoid/dovi_tool) (`DV7/libdovi`) | Convertir Dolby Vision perfil 7 a 8.1 | MIT |
| [mpv-android-lib](https://github.com/abdallahmehiz/mpv-android-lib) / [mpv](https://mpv.io) | Reproductor alternativo | LGPL-2.1 o posterior |
| [libass](https://github.com/libass/libass) | Subtítulos ASS/SSA | ISC |
| [OkHttp](https://square.github.io/okhttp/), [Moshi](https://github.com/square/moshi), [Retrofit](https://square.github.io/retrofit/) | Red y JSON | Apache-2.0 |
| [Coil](https://coil-kt.github.io/coil/) | Imágenes | Apache-2.0 |
| Jetpack Compose, Compose for TV, Hilt, DataStore y demás bibliotecas de AndroidX | Interfaz y base de la app | Apache-2.0 |
| [Inter](https://rsms.me/inter/) (`app/src/main/res/font/inter_variable.ttf`) | Tipografía | SIL Open Font License 1.1 |
| [ZXing](https://github.com/zxing/zxing) | Códigos QR | Apache-2.0 |

La lista completa de dependencias está en `gradle/libs.versions.toml` y `app/build.gradle.kts`.

## Datos y servicios

- Este producto usa la API de TMDB, pero TMDB no lo respalda ni lo certifica.
- Trakt y TorBox son servicios de terceros; BalooTV solo se conecta a ellos con la cuenta de cada usuario.
- BalooTV no trae contenido ni fuentes: cada usuario añade las suyas y es responsable de lo que reproduce.
