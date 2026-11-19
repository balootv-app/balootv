package com.nuvio.tv.balootv.reproductor

/**
 * Detecta la reproducción atascada tras varias búsquedas seguidas: el reproductor dice que reproduce y tiene
 * margen cargado, pero la posición no se mueve y la imagen se queda en negro (el decodificador de vídeo deja de
 * entregar fotogramas). Medido en la tele: posición quieta más de 10 s con 50 s cargados. Primero se prueba una
 * búsqueda a la misma posición; si sigue igual, se recrea el reproductor allí mismo. Como mucho una acción cada
 * [ESPERA_MS]. Tras buscar puede dar un par de segundos de imagen y volver a quedarse quieta: solo se da por
 * recuperada si se aleja [RECUPERADO_MS] de donde se actuó.
 */
class VigilanteAtascos {

    enum class Accion { NADA, BUSCAR, REINICIAR }

    private var posReferencia = -1L
    private var posAccion = -1L
    private var quietoDesde = -1L
    private var ultimaAccion = Long.MIN_VALUE / 2
    private var intentos = 0

    fun observar(ahoraMs: Long, reproduciendo: Boolean, posMs: Long, cargadoDelanteMs: Long): Accion {
        val candidato = reproduciendo && cargadoDelanteMs >= MARGEN_MS
        // Tras cada búsqueda la posición baila unos milisegundos: solo cuenta como movimiento más de un segundo.
        val seMovio = posReferencia < 0 || kotlin.math.abs(posMs - posReferencia) >= TOLERANCIA_MS
        if (seMovio) {
            posReferencia = posMs
            if (posAccion < 0 || kotlin.math.abs(posMs - posAccion) >= RECUPERADO_MS) intentos = 0
        }
        if (!candidato || seMovio) {
            quietoDesde = if (candidato) ahoraMs else -1L
            return Accion.NADA
        }
        if (quietoDesde < 0) quietoDesde = ahoraMs
        if (ahoraMs - quietoDesde < QUIETO_MS || ahoraMs - ultimaAccion < ESPERA_MS) return Accion.NADA
        ultimaAccion = ahoraMs
        posAccion = posMs
        quietoDesde = ahoraMs
        intentos++
        return if (intentos == 1) Accion.BUSCAR else Accion.REINICIAR
    }

    private companion object {
        const val QUIETO_MS = 3_000L
        const val MARGEN_MS = 2_000L
        const val ESPERA_MS = 4_000L
        const val TOLERANCIA_MS = 1_000L
        const val RECUPERADO_MS = 15_000L
    }
}
