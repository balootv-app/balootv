package com.nuvio.tv.balootv.fuentes

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Addon

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

    /** El texto para la tele y la página del móvil. */
    fun texto(c: Comprobacion): String = when (c) {
        is Comprobacion.Valida -> "Añadida: ${c.nombre}"
        Comprobacion.NoEsDireccion -> "No es una dirección web (tiene que empezar por https://)"
        Comprobacion.Repetida -> "Ya la tenías"
        Comprobacion.NecesitaTorbox -> "Lleva {torbox}: vincula antes tu cuenta de TorBox"
        Comprobacion.NoResponde -> "No responde: comprueba la dirección o prueba más tarde"
        Comprobacion.NoEsAddon -> "No es un addon de Stremio (no tiene manifiesto)"
        is Comprobacion.SinEnlaces -> "${c.nombre} no da enlaces para reproducir"
    }

    /** La dirección para enseñarla sin la clave: solo el sitio. */
    fun sitio(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?')
}
