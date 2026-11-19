package com.nuvio.tv.balootv.reproductor

import androidx.media3.common.text.Cue

/**
 * Las líneas de subtítulos que no son de la película: la firma de quien ripeó o sincronizó y las webs de descargas
 * («Ripeado y Subido por Toni32,para ejemplo.com»). Algunas copias las meten en los forzados, que se ponen solos.
 * Copiado del móvil (balootv-movil 1d8b0f7, `cerebro/reproduccion/PublicidadSubtitulos.kt`).
 */
object PublicidadSubtitulos {

    // La llave de cierre va escapada: en el JVM del PC da igual, pero el motor de Android (ICU) no compila una
    // «}» suelta y la app del móvil se cerraba al pintar el primer subtítulo (0.12.1, 18-11-2026).
    // Perezosas: se compilan dentro del runCatching de esPublicidad, así un fallo de ICU no cierra la app.
    private val ETIQUETAS by lazy { Regex("""<[^>]+>|\{[^}]*\}""") }
    private val FIRMA by lazy { Regex("""ripead|ripped by|\bsubs? ?by\b|\bsync\b|\bresync|corrections by|descargad[oa] de""") }
    private val WEB by lazy {
        Regex("""\bwww\.|\b[a-z0-9-]{2,}\.(?:com|net|org|wtf|to|tv|info|cc|ws|biz|me|io|xyz|club|site|nu|li|lol|mx|ar)\b""")
    }

    fun es(texto: String): Boolean {
        val t = ETIQUETAS.replace(texto, "").lowercase()
        return FIRMA.containsMatchIn(t) || WEB.containsMatchIn(t)
    }

    /**
     * [cues] sin las de propaganda; devuelve la misma lista si no hay ninguna. Un fallo del filtro nunca puede
     * cerrar la app ni dejar la película sin subtítulos: ante la duda, se pinta.
     */
    fun quitar(cues: List<Cue>): List<Cue> {
        if (cues.none(::esPublicidad)) return cues
        return cues.filterNot(::esPublicidad)
    }

    private fun esPublicidad(c: Cue): Boolean =
        runCatching { c.text?.let { es(it.toString()) } ?: false }.getOrDefault(false)
}
