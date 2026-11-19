package com.nuvio.tv.balootv.ui.acciones

/** Lo que se puede hacer con un título dejando pulsado sobre él. */
enum class AccionTitulo {
    IR_A_FICHA,
    QUITAR,
    MARCAR_VISTA,
    DESMARCAR_VISTA,
    ANADIR_A_LISTA,
    QUITAR_DE_LISTA,
}

/**
 * En qué estado está el título. [vistoEntero] es «no queda nada por marcar»: la película ya vista o la serie sin
 * ningún episodio por ver. [algoVisto] es «hay algo que desmarcar»: la película vista o algún episodio visto.
 */
data class SituacionTitulo(
    val enSeguirViendo: Boolean,
    val vistoEntero: Boolean,
    val algoVisto: Boolean,
    val enMiLista: Boolean,
)

object OpcionesTitulo {

    /**
     * Ir a la ficha y quitar solo dentro de Seguir viendo (allí OK reproduce); marcar si queda algo y desmarcar si hay
     * algo visto; la lista, según esté.
     */
    fun de(situacion: SituacionTitulo): List<AccionTitulo> = buildList {
        if (situacion.enSeguirViendo) {
            add(AccionTitulo.IR_A_FICHA)
            add(AccionTitulo.QUITAR)
        }
        if (!situacion.vistoEntero) add(AccionTitulo.MARCAR_VISTA)
        if (situacion.algoVisto) add(AccionTitulo.DESMARCAR_VISTA)
        add(if (situacion.enMiLista) AccionTitulo.QUITAR_DE_LISTA else AccionTitulo.ANADIR_A_LISTA)
    }

    /** El texto de cada opción; en una serie, con el episodio que marca o desmarca. */
    fun texto(accion: AccionTitulo, esSerie: Boolean, marcar: Pair<Int, Int>?, desmarcar: Pair<Int, Int>?): String = when (accion) {
        AccionTitulo.IR_A_FICHA -> if (esSerie) "Ir a la ficha de la serie" else "Ir a la ficha de la película"
        AccionTitulo.QUITAR -> "Quitar de Seguir viendo"
        AccionTitulo.MARCAR_VISTA ->
            if (esSerie && marcar != null) "Marcar T${marcar.first} · E${marcar.second} como visto" else "Marcar como vista"
        AccionTitulo.DESMARCAR_VISTA ->
            if (esSerie && desmarcar != null) "Marcar T${desmarcar.first} · E${desmarcar.second} como no visto" else "Marcar como no vista"
        AccionTitulo.ANADIR_A_LISTA -> "Añadir a Mi lista"
        AccionTitulo.QUITAR_DE_LISTA -> "Quitar de Mi lista"
    }
}

object EpisodioQueToca {

    /**
     * El episodio que marca «Marcar como vista» en una carátula: el primero sin ver, sin contar los especiales
     * (temporada 0) salvo que sean lo único que hay. Sin progreso, el 1×1. `null` si ya está todo visto.
     */
    fun de(episodios: List<Pair<Int, Int>>, vistos: Set<Pair<Int, Int>>): Pair<Int, Int>? {
        val ordenados = episodios.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        val reglares = ordenados.filter { it.first > 0 }.ifEmpty { ordenados }
        return reglares.firstOrNull { it !in vistos }
    }

    /**
     * El episodio que desmarca «Marcar como no visto»: el último visto, sin contar los especiales salvo que sean lo
     * único visto. `null` si no hay nada visto.
     */
    fun ultimoVisto(vistos: Set<Pair<Int, Int>>): Pair<Int, Int>? {
        val reglares = vistos.filter { it.first > 0 }.ifEmpty { vistos }
        return reglares.maxWithOrNull(compareBy({ it.first }, { it.second }))
    }
}
