package com.nuvio.tv.domain.deeplink

/** BalooTV: abrir la ficha de un título, o reproducirlo (enlace que manda la app del móvil). */
sealed interface AppDeepLink {
    data class Meta(
        val type: String,
        val id: String
    ) : AppDeepLink

    /** Sin [season] ni [episode]: la película, o el siguiente episodio sin ver de la serie (1×1 si no hay progreso). */
    data class Reproducir(
        val type: String,
        val id: String,
        val season: Int? = null,
        val episode: Int? = null
    ) : AppDeepLink
}
