package com.nuvio.tv.balootv.inicio

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Un título visto y la última vez que se vio (`last_watched_at` de `sync/watched`). */
data class VistoFechado(val titulo: Titulo, val vistoEn: Instant)

/**
 * Contrato C1 (D36 del móvil): las semillas de «Porque viste» del día, iguales en el móvil y en la tele. Copia de
 * `Semillas.kt` del móvil (commit 3e1d288); lo prueban los casos de `compartido/semillas.json`.
 */
object Semillas {

    const val VENTANA_DIAS = 42
    const val RESERVA = 5

    private val MADRID: ZoneId = ZoneId.of("Europe/Madrid")

    /**
     * Una película y una serie de lo visto en los últimos [VENTANA_DIAS] días de Madrid contando [hoy] (o, si no hay,
     * de los [RESERVA] más recientes), elegidas por la fecha: cambian cada día y no repiten la de ayer si hay otra.
     * Primero la vista más recientemente; sin candidatos de una clase, solo la otra.
     */
    fun elegir(peliculas: List<VistoFechado>, series: List<VistoFechado>, hoy: LocalDate): List<Titulo> =
        listOfNotNull(elegirDe(peliculas, Tipo.PELICULA, hoy), elegirDe(series, Tipo.SERIE, hoy))
            .sortedByDescending { it.vistoEn }
            .map { it.titulo }

    private fun elegirDe(vistos: List<VistoFechado>, tipo: Tipo, hoy: LocalDate): VistoFechado? {
        val candidatos = vistos.filter { it.titulo.tipo == tipo && it.titulo.ids.trakt != null }
        val desde = hoy.minusDays(VENTANA_DIAS - 1L)
        val ventana = candidatos.filter { it.vistoEn.atZone(MADRID).toLocalDate() in desde..hoy }
            .ifEmpty { candidatos.sortedByDescending { it.vistoEn }.take(RESERVA) }
        if (ventana.isEmpty()) return null
        val ordenados = ventana.sortedBy { it.titulo.ids.trakt }
        val n = ordenados.size
        var i = indice(hoy, tipo, n)
        if (n > 1 && i == indice(hoy.minusDays(1), tipo, n)) i = (i + 1) % n
        return ordenados[i]
    }

    private fun indice(dia: LocalDate, tipo: Tipo, n: Int) = Math.floorMod("$dia|${tipo.name}".hashCode(), n)
}
