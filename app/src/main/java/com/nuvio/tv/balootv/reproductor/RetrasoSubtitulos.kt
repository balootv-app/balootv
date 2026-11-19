package com.nuvio.tv.balootv.reproductor

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * El retraso de los subtítulos que se ajusta en el panel «Audio y subtítulos»: a la derecha, salen más tarde; a la
 * izquierda, antes. Se guarda por vídeo con lo que ya había (TrackPreferenceDataStore).
 */
object RetrasoSubtitulos {

    const val PASO_MS = 100

    /** Más que esto ya no es un desfase de subtítulos, es otro montaje de la película. */
    const val MAX_MS = 10_000

    /** El retraso tras pulsar una vez hacia [direccion] (+1 derecha, −1 izquierda) desde [actualMs]. */
    fun paso(actualMs: Int, direccion: Int): Int {
        val redondeado = (actualMs / PASO_MS.toFloat()).roundToInt() * PASO_MS
        return (redondeado + direccion.coerceIn(-1, 1) * PASO_MS).coerceIn(-MAX_MS, MAX_MS)
    }

    /** «0,0 s», «+0,5 s», «−1,2 s». */
    fun texto(ms: Int): String {
        val decimas = (ms / 100f).roundToInt()
        val signo = when {
            decimas > 0 -> "+"
            decimas < 0 -> "−"
            else -> ""
        }
        val absoluto = abs(decimas)
        return "$signo${absoluto / 10},${absoluto % 10} s"
    }
}
