package com.nuvio.tv.balootv.reproductor

import com.nuvio.tv.core.tmdb.TmdbEpisodeEnrichment
import com.nuvio.tv.domain.model.Video

/**
 * Textos de las acciones contextuales del reproductor (saltar intro, siguiente episodio), como las de tvOS:
 * aparecen solas en su momento, se ejecutan con OK y hablan como el resto de BalooTV.
 */
object AccionesContextuales {

    /** Segundos que la tarjeta de siguiente episodio deja para reaccionar antes de empezar sola. */
    const val CUENTA_ATRAS_S = 10

    /** "T1 · E2 · El camino real", como en las fichas. */
    fun episodio(temporada: Int, episodio: Int, titulo: String?): String =
        listOfNotNull("T$temporada", "E$episodio", titulo?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · ")

    /** Texto del botón según el tramo que salta (tipos de IntroDB). */
    fun etiquetaSalto(tipo: String?, aPoscreditos: Boolean): String = when (tipo?.trim()?.lowercase()) {
        "op", "opening", "mixed-op", "intro" -> "Saltar introducción"
        "recap" -> "Saltar resumen"
        "ed", "ending", "mixed-ed", "outro", "credits", "movie-credits" ->
            if (aPoscreditos) "Ir a la escena poscréditos" else "Saltar créditos"
        else -> "Saltar"
    }

    /** Línea de estado de la tarjeta de siguiente episodio, o null si no hay nada que contar. */
    fun estado(buscando: Boolean, cuentaAtras: Int?): String? = when {
        buscando -> "Buscando en castellano…"
        cuentaAtras != null -> "Empieza en $cuentaAtras s"
        else -> null
    }

    /** Lo que queda de cuenta atrás, de 1 a 0, para la barra de la tarjeta. */
    fun fraccionCuentaAtras(restante: Int): Float = (restante.toFloat() / CUENTA_ATRAS_S).coerceIn(0f, 1f)
}

/** Episodios del addon con el título, la sinopsis y la imagen de TMDB en castellano cuando los hay. */
object EpisodiosTmdb {

    fun localizar(videos: List<Video>, tmdb: Map<Pair<Int, Int>, TmdbEpisodeEnrichment>): List<Video> {
        if (tmdb.isEmpty()) return videos
        return videos.map { v ->
            val e = tmdb[(v.season ?: return@map v) to (v.episode ?: return@map v)] ?: return@map v
            v.copy(
                title = e.title?.trim()?.takeIf { it.isNotEmpty() } ?: v.title,
                overview = e.overview?.trim()?.takeIf { it.isNotEmpty() } ?: v.overview,
                thumbnail = v.thumbnail ?: e.thumbnail,
            )
        }
    }
}
