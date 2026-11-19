package com.nuvio.tv.balootv.episodios

/**
 * Pasa la numeración de episodios de la ficha (la de Cinemeta) a la de TMDB, para pedirle a TMDB los textos en
 * castellano. Casi siempre es la misma; cuando no (DAN DA DAN: Cinemeta 12 + 12 + 1, TMDB una temporada de 24), se casa
 * por posición. Es la regla de la app del móvil (`episodios/NumeracionTele.kt`, commit a7ab65c).
 */
object NumeracionTmdb {

    /**
     * Cada episodio de [episodios] (temporada, número) con el suyo de TMDB, a partir de cuántos episodios tiene cada
     * temporada en TMDB ([recuentoTmdb], sin la 0). Los especiales (temporada 0) y lo que TMDB no tiene se quedan fuera.
     */
    fun traducir(episodios: List<Pair<Int, Int>>, recuentoTmdb: Map<Int, Int>): Map<Pair<Int, Int>, Pair<Int, Int>> {
        val normales = episodios.filter { it.first > 0 }.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        val recuento = recuentoTmdb.filterKeys { it > 0 }.toSortedMap()
        val propio = normales.groupingBy { it.first }.eachCount()
        if (propio == recuento) return normales.associateWith { it }
        val temporadas = recuento.entries.toList()
        return buildMap {
            normales.forEachIndexed { indice, episodio ->
                var resto = indice
                for ((temporada, cuantos) in temporadas) {
                    if (resto < cuantos) {
                        put(episodio, temporada to resto + 1)
                        break
                    }
                    resto -= cuantos
                }
            }
        }
    }

    /** Al revés que [traducir]: el episodio de [episodios] que en TMDB es [deTmdb]; null si la ficha no lo tiene. */
    fun deLaFicha(episodios: List<Pair<Int, Int>>, recuentoTmdb: Map<Int, Int>, deTmdb: Pair<Int, Int>): Pair<Int, Int>? =
        traducir(episodios, recuentoTmdb).entries.firstOrNull { it.value == deTmdb }?.key
}
