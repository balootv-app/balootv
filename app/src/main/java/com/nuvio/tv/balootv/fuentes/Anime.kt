package com.nuvio.tv.balootv.fuentes

/**
 * ¿Es anime? Animación (género 16 de TMDB) con idioma original japonés (spec F4 §5). Los casos raros se
 * corrigen en las listas: ids de IMDb que siempre o nunca cuentan como anime.
 */
object Anime {
    const val GENERO_ANIMACION = 16

    val SIEMPRE: Set<String> = emptySet()
    val NUNCA: Set<String> = emptySet()

    fun es(
        generos: Set<Int>,
        idiomaOriginal: String?,
        imdbId: String,
        siempre: Set<String> = SIEMPRE,
        nunca: Set<String> = NUNCA,
    ): Boolean = when {
        imdbId in nunca -> false
        imdbId in siempre -> true
        else -> GENERO_ANIMACION in generos && idiomaOriginal.equals("ja", ignoreCase = true)
    }
}
