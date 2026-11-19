package com.nuvio.tv.balootv.ui.acciones

import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem

/** Un episodio concreto, el de una tarjeta de Seguir viendo o el que toca marcar en una carátula de serie. */
data class EpisodioObjetivo(val temporada: Int, val episodio: Int, val videoId: String, val titulo: String?)

/** El título sobre el que se ha dejado pulsado, venga de una carátula o de una tarjeta de Seguir viendo. */
data class ObjetivoAccion(
    val id: String,
    val esSerie: Boolean,
    val nombre: String,
    val poster: String?,
    val fondo: String?,
    val logo: String?,
    val releaseInfo: String?,
    /** El episodio de la tarjeta; en una carátula de serie, `null` hasta saber cuál toca. */
    val episodio: EpisodioObjetivo? = null,
    val enSeguirViendo: Boolean = false,
) {
    val tipo: String get() = if (esSerie) "series" else "movie"

    companion object {
        fun de(item: MetaPreview) = ObjetivoAccion(
            id = item.id, esSerie = item.apiType == "series" || item.apiType == "tv", nombre = item.name,
            poster = item.poster, fondo = item.backdropUrl, logo = item.logo, releaseInfo = item.releaseInfo,
        )

        fun de(item: ContinueWatchingItem): ObjetivoAccion = when (item) {
            is ContinueWatchingItem.InProgress -> {
                val p = item.progress
                val episodio = if (p.season != null && p.episode != null) EpisodioObjetivo(p.season, p.episode, p.videoId, p.episodeTitle) else null
                ObjetivoAccion(
                    id = p.contentId, esSerie = episodio != null || p.contentType != "movie", nombre = p.name,
                    poster = p.poster, fondo = p.backdrop, logo = p.logo, releaseInfo = null,
                    episodio = episodio, enSeguirViendo = true,
                )
            }
            is ContinueWatchingItem.NextUp -> {
                val i = item.info
                ObjetivoAccion(
                    id = i.contentId, esSerie = true, nombre = i.name, poster = i.poster, fondo = i.backdrop, logo = i.logo,
                    releaseInfo = i.releaseInfo, episodio = EpisodioObjetivo(i.season, i.episode, i.videoId, i.episodeTitle),
                    enSeguirViendo = true,
                )
            }
        }
    }
}
