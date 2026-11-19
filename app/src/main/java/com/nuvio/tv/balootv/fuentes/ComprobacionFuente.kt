package com.nuvio.tv.balootv.fuentes

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.streams.supportsStreamResource
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.Stream

/** Lo que pasa con cada dirección que el usuario intenta añadir como fuente. */
sealed interface Comprobacion {
    /** Responde y ofrece enlaces: se añade. */
    data class Valida(val nombre: String) : Comprobacion
    data object NoEsDireccion : Comprobacion
    data object Repetida : Comprobacion
    data object NecesitaTorbox : Comprobacion
    data object NoResponde : Comprobacion
    data object NoEsAddon : Comprobacion
    /** Es un addon, pero de catálogos o subtítulos: no da enlaces para reproducir. */
    data class SinEnlaces(val nombre: String) : Comprobacion
    /**
     * Da enlaces, pero solo de torrent: sin la parte de TorBox en la dirección la app no puede reproducirlos (no hay
     * motor de torrents). No se añade, para que nadie se quede con una app que «no reproduce» sin saber por qué.
     */
    data class SinTorbox(val nombre: String) : Comprobacion
}

/**
 * Las reglas para aceptar una fuente: que sea una dirección, que no esté ya, que responda y que sea un addon de
 * Stremio con enlaces (`stream` entre sus recursos). La parte de red la hace `AddonRepository.fetchAddon`.
 */
object ComprobacionFuente {

    /** Los errores de Moshi al leer algo que no es un manifiesto (una página web, un JSON de otra cosa). */
    private val NO_ES_JSON = Regex("""json|path \$|expected |required value""", RegexOption.IGNORE_CASE)

    /**
     * La dirección del manifiesto a partir de lo que se pega: admite `stremio://`, con o sin `/manifest.json` y con
     * espacios alrededor. Null si no es una dirección web.
     */
    fun normalizar(texto: String): String? {
        var t = texto.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        if (t.startsWith("stremio://", ignoreCase = true)) t = "https://" + t.substring("stremio://".length)
        if (!t.startsWith("https://", ignoreCase = true) && !t.startsWith("http://", ignoreCase = true)) return null
        val sinProtocolo = t.substringAfter("://")
        val host = sinProtocolo.substringBefore('/').substringBefore('?')
        if (host.isBlank() || '.' !in host && !host.startsWith("localhost")) return null
        val (camino, consulta) = t.indexOf('?').let { if (it < 0) t to "" else t.substring(0, it) to t.substring(it) }
        val base = camino.trimEnd('/').removeSuffix("/manifest.json")
        return "$base/manifest.json$consulta"
    }

    /** Antes de ir a la red: dirección, repetida o sin clave de TorBox para rellenarla. */
    fun antesDeLaRed(plantilla: String?, yaInstaladas: Set<String>, clave: String): Comprobacion? = when {
        plantilla == null -> Comprobacion.NoEsDireccion
        PlantillaFuente.lleva(plantilla) && clave.isBlank() -> Comprobacion.NecesitaTorbox
        FuentesDeSerie.base(PlantillaFuente.rellenar(plantilla, clave)) in yaInstaladas -> Comprobacion.Repetida
        else -> null
    }

    /** El veredicto con la respuesta del manifiesto. */
    fun juzgar(respuesta: NetworkResult<Addon>): Comprobacion = when (respuesta) {
        is NetworkResult.Success -> {
            val a = respuesta.data
            val nombre = a.displayName.ifBlank { a.name }.ifBlank { a.id }
            when {
                a.id.isBlank() || a.resources.isEmpty() -> Comprobacion.NoEsAddon
                a.resources.none { it.name == "stream" } -> Comprobacion.SinEnlaces(nombre)
                else -> Comprobacion.Valida(nombre)
            }
        }
        // Contesta, pero no con un manifiesto (una web normal, un 404) o con algo que no se entiende.
        is NetworkResult.Error -> if (respuesta.code != null && respuesta.code in 400..499) Comprobacion.NoEsAddon
            else if (respuesta.code == null && NO_ES_JSON.containsMatchIn(respuesta.message)) Comprobacion.NoEsAddon
            else Comprobacion.NoResponde
        NetworkResult.Loading -> Comprobacion.NoResponde
    }

    /** Títulos muy conocidos para preguntar a una fuente nueva qué clase de enlaces da: película y episodio. */
    private val SONDEOS = listOf("movie" to "tt0111161", "series" to "tt0903747:1:1")

    /**
     * Si hay que preguntar a la fuente qué enlaces da antes de aceptarla: solo cuando la dirección no lleva la clave
     * de TorBox (con `{torbox}` los enlaces ya pasan por TorBox). Devuelve el tipo y el identificador a preguntar, o
     * null si no hace falta o si la fuente no admite ninguno (entonces se acepta sin preguntar).
     */
    fun sondeo(plantilla: String, addon: Addon): Pair<String, String>? {
        if (PlantillaFuente.lleva(plantilla)) return null
        return SONDEOS.firstOrNull { (tipo, id) -> addon.supportsStreamResource(tipo, id) }
    }

    /**
     * Si los enlaces de una fuente son todos de torrent (sin dirección web que reproducir). Sin enlaces no se sabe:
     * cuenta como que sí sirve, mejor que rechazar una fuente buena.
     */
    fun soloTorrents(enlaces: List<Stream>): Boolean = enlaces.isNotEmpty() && enlaces.all { it.isTorrent() }

    /** El texto para la tele y la página del móvil. */
    fun texto(c: Comprobacion): String = when (c) {
        is Comprobacion.Valida -> "Añadida: ${c.nombre}"
        Comprobacion.NoEsDireccion -> "No es una dirección web (tiene que empezar por https://)"
        Comprobacion.Repetida -> "Ya la tenías"
        Comprobacion.NecesitaTorbox -> "Lleva {torbox}: vincula antes tu cuenta de TorBox"
        Comprobacion.NoResponde -> "No responde: comprueba la dirección o prueba más tarde"
        Comprobacion.NoEsAddon -> "No es un addon de Stremio (no tiene manifiesto)"
        is Comprobacion.SinEnlaces -> "${c.nombre} no da enlaces para reproducir"
        is Comprobacion.SinTorbox -> "${c.nombre}: esta fuente no está preparada para TorBox, le falta {torbox} en la dirección"
    }

    /** La dirección para enseñarla sin la clave: solo el sitio. */
    fun sitio(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?')
}
