package com.nuvio.tv.balootv.inicio

import com.nuvio.tv.domain.model.WatchProgress

/**
 * Contrato C8 (`compartido/siguiente.json`): de qué episodio visto sale el siguiente episodio de una serie en «Seguir
 * viendo». Con «desde el episodio más avanzado» (el ajuste de Nuvio, que viene puesto), lo más avanzado de lo visto en
 * los días del Inicio, sin especiales; así un repaso reciente no lo tapa lo que se vio hace meses. Si en esos días no
 * se ha visto nada, lo más avanzado de todo, y el corte del Inicio ya deja la serie fuera.
 */
object SemillaSiguiente {

    data class Visto(val temporada: Int, val episodio: Int, val vistoEnMs: Long)

    fun elegir(vistos: List<Visto>, masAvanzado: Boolean, desdeMs: Long?): Visto? = vistos
        .filter { it.temporada > 0 && it.episodio > 0 }
        .maxWithOrNull(if (masAvanzado) orden(desdeMs) else compareBy({ it.vistoEnMs }, { it.temporada }, { it.episodio }))

    /** Con «desde el episodio más avanzado»: si [candidato] sustituye a la semilla [existente] de la misma serie. */
    fun prefiere(candidato: Visto, existente: Visto, desdeMs: Long?): Boolean =
        orden(desdeMs).compare(candidato, existente) >= 0

    fun prefiere(candidato: WatchProgress, existente: WatchProgress, desdeMs: Long?): Boolean =
        prefiere(candidato.comoVisto(), existente.comoVisto(), desdeMs)

    private fun WatchProgress.comoVisto() = Visto(season ?: -1, episode ?: -1, lastWatched)

    // Primero lo que está dentro de los días (sin corte, todo), luego lo más avanzado y, a igualdad, lo más reciente.
    private fun orden(desdeMs: Long?): Comparator<Visto> =
        compareBy({ desdeMs == null || it.vistoEnMs >= desdeMs }, { it.temporada }, { it.episodio }, { it.vistoEnMs })
}
