package com.nuvio.tv.balootv.ui.ficha

import com.nuvio.tv.domain.model.Video

/**
 * Lo que hace la ficha con el episodio que pide el móvil (`balootv://reproducir/tmdb/series/<id>/<T>/<E>`, en la
 * numeración de la ficha, la de Cinemeta): reproducir [video] si existe; si no, abrir sin reproducir en [temporada]
 * (la pedida si la hay; si no, la que toca, `null`).
 */
data class EpisodioDelEnlace(val video: Video?, val temporada: Int?) {
    companion object {
        fun de(videos: List<Video>, temporada: Int, episodio: Int): EpisodioDelEnlace {
            val disponibles = videos.filter { it.available != false }
            val video = disponibles.firstOrNull { it.season == temporada && it.episode == episodio }
            if (video != null) return EpisodioDelEnlace(video, temporada)
            return EpisodioDelEnlace(null, temporada.takeIf { t -> disponibles.any { it.season == t } })
        }
    }
}
