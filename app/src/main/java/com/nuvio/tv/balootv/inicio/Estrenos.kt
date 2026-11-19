package com.nuvio.tv.balootv.inicio

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Una serie vista y las fechas de estreno de sus temporadas. [estrenos]: número de temporada de TMDB → fecha del
 * episodio 1, solo las que la tienen. [ultimaTemporadaVista]: la mayor con algún episodio visto; 0 si ninguna.
 */
@Serializable
data class SerieEstrenos(
    val titulo: Titulo,
    val ultimaTemporadaVista: Int,
    val estrenos: Map<Int, @Serializable(with = FechaIso::class) LocalDate>,
)

/** El estreno de la temporada [temporada] (numeración de TMDB) de una serie. */
@Serializable
data class Estreno(val titulo: Titulo, val temporada: Int, @Serializable(with = FechaIso::class) val fecha: LocalDate)

/**
 * Contrato C3 (D36 del móvil): la fila «Ya está aquí la temporada nueva», igual en el móvil y en la tele. Solo la
 * temporada siguiente a la última vista, estrenada en los últimos [Semillas.VENTANA_DIAS] días contando hoy; lo más
 * reciente primero. Copia de `Estrenos.kt` del móvil (commit 3e1d288), sin los próximos estrenos, que la tele no
 * enseña. Casos en `compartido/temporadas.json`.
 */
object TemporadaNueva {
    fun calcular(series: List<SerieEstrenos>, hoy: LocalDate): List<Estreno> = series.mapNotNull { serie ->
        if (serie.ultimaTemporadaVista <= 0) return@mapNotNull null
        val siguiente = serie.ultimaTemporadaVista + 1
        serie.estrenos[siguiente]?.let { Estreno(serie.titulo, siguiente, it) }
    }.filter { it.fecha in hoy.minusDays(Semillas.VENTANA_DIAS - 1L)..hoy }.sortedByDescending { it.fecha }
}
