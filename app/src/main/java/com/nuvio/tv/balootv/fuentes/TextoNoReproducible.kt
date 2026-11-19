package com.nuvio.tv.balootv.fuentes

import com.nuvio.tv.domain.model.Addon

/**
 * Por qué ninguna fuente admite un título (Reproducir de la ficha o de «Seguir viendo»). Lo normal en un estreno muy
 * reciente: aún no tiene ficha en IMDb, el título va con su número de TMDB y las fuentes solo buscan por IMDb.
 */
object TextoNoReproducible {

    const val SIN_IMDB = "Este título aún no tiene ficha en IMDb, que es por donde buscan tus fuentes. " +
        "Suele aparecer a los pocos días del estreno: prueba más adelante."
    const val NINGUNA = "Ninguna de tus fuentes admite este título."

    /** Al elegir en «Cambiar fuente» un enlace de torrent que nada puede servir (fuente sin la parte de TorBox). */
    const val ENLACE_TORRENT = "Este enlace es un torrent y su fuente no está preparada para TorBox (le falta {torbox} " +
        "en la dirección). Elige otro o cambia la fuente en Ajustes."

    /** Mientras se prepara el enlace elegido en «Cambiar fuente» del reproductor. */
    const val PREPARANDO = "Preparando la fuente…"

    fun de(videoId: String, addons: List<Addon>): String {
        val conEnlaces = addons.any { it.enabled && it.resources.any { r -> r.name == "stream" } }
        return if (conEnlaces && !videoId.startsWith("tt")) SIN_IMDB else NINGUNA
    }
}
