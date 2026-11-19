package com.nuvio.tv.balootv.ui.ficha

/** Si de una serie ya está visto todo lo que hay: «Reproducir» entonces abre la ficha en vez de empezar de nuevo. */
object SerieVista {

    /** Los especiales (temporada 0) no cuentan salvo que sean lo único que hay, como en «siguiente episodio». */
    fun entera(episodios: List<Pair<Int, Int>>, vistos: Set<Pair<Int, Int>>): Boolean {
        val reglares = episodios.filter { it.first > 0 }.ifEmpty { episodios }
        return reglares.isNotEmpty() && reglares.all { it in vistos }
    }
}
