package com.nuvio.tv.balootv.reproductor

import com.nuvio.tv.balootv.fuentes.Candidato
import com.nuvio.tv.balootv.fuentes.Eleccion
import com.nuvio.tv.balootv.fuentes.Peticion
import com.nuvio.tv.balootv.fuentes.Puntuado
import com.nuvio.tv.balootv.fuentes.SelectorBalooTV
import com.nuvio.tv.balootv.fuentes.comoPalabras

/**
 * Si un enlace falla al abrir o se corta al principio, se pasa solo al siguiente bueno en vez de devolver a la
 * lista (spec F4 §3). "Al principio" son los primeros minutos de esta sesión; más tarde se reintenta como
 * siempre. Como mucho tres saltos seguidos, para no encadenar fuentes rotas sin fin.
 */
object SaltoFuente {
    const val MAX_SALTOS = 3
    const val PRINCIPIO_MS = 180_000L
    const val SIN_IMAGEN_MS = 20_000L

    fun debeSaltar(reproducidoEnSesionMs: Long, saltosHechos: Int, modoBalooTV: Boolean): Boolean =
        modoBalooTV && saltosHechos < MAX_SALTOS && reproducidoEnSesionMs < PRINCIPIO_MS

    /**
     * La mejor elegible (castellano, ≥ mínimo, en caché) que no esté entre las URLs que ya han fallado ni sea el
     * mismo fichero servido por otro addon: si un .mkv no da imagen, tampoco la dará por otro camino.
     */
    fun siguiente(candidatos: List<Candidato>, p: Peticion, fallidas: Set<String>): Puntuado? {
        val ficherosFallidos = candidatos.filter { it.stream?.url in fallidas }.mapNotNull(::fichero).toSet()
        val quedan = candidatos.filter { it.stream?.url !in fallidas && fichero(it)?.let { f -> f in ficherosFallidos } != true }
        return (SelectorBalooTV.elegir(quedan, p) as? Eleccion.Elegido)?.mejor
    }

    private val EXTENSION_VIDEO = Regex("""\.(mkv|mp4|avi|m2ts|ts)$""", RegexOption.IGNORE_CASE)

    /** Nombre del fichero de vídeo, del addon o del final de la URL, para reconocerlo venga de donde venga. */
    private fun fichero(c: Candidato): String? {
        val stream = c.stream ?: return null
        val nombre = stream.behaviorHints?.filename
            ?: stream.url?.substringBefore('?')?.substringAfterLast('/')
                ?.let { runCatching { java.net.URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrNull() }
                ?.takeIf { EXTENSION_VIDEO.containsMatchIn(it) }
        return nombre?.let(::comoPalabras)?.takeIf { it.length >= 8 }
    }
}
