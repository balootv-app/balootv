# BalooTV

App de pelis y series para la tele. Pulsas **Reproducir** y empieza a verse, sin elegir enlaces: ficha → Reproducir →
imagen. Siempre que existe, la versión en castellano de España va primero, con mínimo 1080p, y la interfaz sigue el
estilo del Apple TV.

Es un fork de [NuvioTV](https://github.com/NuvioMedia/NuvioTV) y se reparte con la misma licencia, GPL-3.0. Créditos y
licencias de lo que lleva dentro, en [`NOTICE.md`](NOTICE.md).

## Qué necesitas

- Una tele o aparato con **Android TV, Google TV o Fire TV** (Android 7 o superior).
- **Tu propio servicio.** Hoy la app funciona con TorBox, un servicio aparte que contratas tú. BalooTV no incluye
  ninguna cuenta.
- **Tus propias fuentes** (addons de Stremio). BalooTV no trae ninguna: las añades tú en el primer arranque.
- Opcional: una cuenta de **Trakt**, para recordar lo que has visto y seguir donde lo dejaste.

## Instalar

La app se instala a mano, bajando el APK desde la [página de versiones](https://github.com/balootv-app/balootv/releases/latest) (`BalooTV.apk`).

### Qué APK elegir

| Fichero | Para |
|---|---|
| `BalooTV-<versión>-arm64-v8a.apk` | Aparatos de 64 bits (la mayoría de los Google TV y Android TV recientes) |
| `BalooTV-<versión>-armeabi-v7a.apk` | Aparatos de 32 bits |
| `BalooTV.apk` | La de 32 bits con otro nombre: vale para todos los aparatos. Si dudas, esta |

### Android TV y Google TV

1. Instala **Downloader** desde la tienda de la tele, o **Send files to TV** si prefieres mandar el fichero desde el
   móvil.
2. Abre el APK desde la página de versiones (en Downloader, escribiendo su dirección).
3. Si Android avisa de que no puede instalar apps de esa fuente, ve a Ajustes → Apps → Seguridad y restricciones →
   Fuentes desconocidas y activa Downloader (o Send files to TV). Vuelve atrás y repite la instalación.
4. Confirma la instalación.

### Fire TV

1. Instala **Downloader** desde la tienda de Amazon.
2. En Configuración → Mi Fire TV → Opciones para desarrolladores, activa «Instalar apps desconocidas» para Downloader.
   Si no ves «Opciones para desarrolladores», entra en Acerca de y pulsa siete veces sobre el nombre del aparato.
3. Abre el APK desde Downloader y confirma la instalación.

## Primer arranque

La primera vez, BalooTV te guía en cuatro pasos: **Bienvenida → TorBox → Trakt → Fuentes**. Trakt se puede saltar.
Puedes repetirlos cuando quieras desde Ajustes → Acerca de → «Repetir los primeros pasos».

## Añadir y quitar fuentes

Ajustes → Fuentes. Para añadir una, tienes dos caminos:

- **Desde el móvil**: la tele enseña un QR; lo escaneas y se abre una página que sirve la propia tele en tu red local.
  Pegas ahí la dirección de la fuente. Móvil y tele tienen que estar en la misma red.
- **Con el mando**: escribes la dirección en la tele.

BalooTV comprueba cada fuente al añadirla. Para quitar una, entra en Ajustes → Fuentes, elígela y quítala.

## Actualizar

BalooTV se actualiza sola. Cuando hay una versión nueva, el Inicio avisa con «Actualizar» y «Más tarde». Si eliges
«Actualizar», baja de la página de versiones el APK de la arquitectura de tu aparato y Android te pide confirmar la
instalación.

## Preguntas frecuentes

**En la ficha pone «Añadir fuentes», o no sale ningún enlace.**
No tienes fuentes, o ninguna ha dado resultado. Pulsa «Añadir fuentes» (o ve a Ajustes → Fuentes) y añade al menos
una. Comprueba también que tu cuenta de TorBox está activa.

**No suena el sonido envolvente.**
Revisa los ajustes de sonido de BalooTV y la salida de audio de tu tele o barra. Por HDMI ARC muchas barras solo aceptan
AC-3 y DTS: prueba con otra pista de audio de la ficha.

**Me pide permiso para instalar.**
La primera vez hay que dar a BalooTV el permiso de instalar apps desconocidas. En Google TV y Android TV: Ajustes →
Apps → Seguridad y restricciones → Fuentes desconocidas → BalooTV. En Fire TV: Configuración → Mi Fire TV → Opciones
para desarrolladores → Instalar apps desconocidas → BalooTV. Solo se hace una vez.

**¿Es gratis?**
Sí, la app es gratuita y de código abierto. TorBox es un servicio aparte y tiene su propio precio.

**¿Hay versión para móvil?**
Por ahora no.

## Lo que BalooTV no es

- **No es un servicio de vídeo ni un catálogo.** Es un reproductor y un organizador. Reproduce lo que le llega de las
  fuentes y del servicio que conectas tú, y está pensado para contenido que es tuyo o que tienes derecho a ver. **No
  aloja, no guarda y no distribuye contenido.**
- **No trae fuentes ni las recomienda.** Recién instalada está vacía. Las fuentes son de terceros y cada usuario
  responde de las que añade y de cumplir la ley de su país.
- **No tiene relación con nadie más.** Es un proyecto independiente: no está afiliada a Nuvio, Trakt, TMDB ni TorBox,
  ni a ningún addon, fuente o proveedor de contenido.
- **No guarda nada tuyo.** Sin anuncios, sin analítica y sin cuenta de BalooTV.

Este producto usa la API de TMDB, pero no está respaldado ni certificado por TMDB.

BalooTV usa Trakt para el historial y las listas, pero no está avalada ni certificada por Trakt.

*BalooTV uses Trakt but is not endorsed or certified by Trakt.*

Aviso legal completo, privacidad, contacto y cómo pedir una retirada: <https://balootv-app.github.io/legal.html>.

## Para desarrolladores

Para colaborar y compilar la app, mira [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Licencia

[GNU General Public License v3.0](LICENSE).
