package com.nuvio.tv.balootv.ui.movimiento

/**
 * El ritmo al que se navega con las flechas, como el motor de foco de tvOS: decide si una pulsación mueve el foco y si
 * se va en ráfaga (pulsaciones muy seguidas o la tecla mantenida).
 *
 * - Las pulsaciones sueltas pasan siempre, por rápidas que sean: quien pulsa espera moverse.
 * - La tecla mantenida repite cada 50 ms (20 pasos por segundo, más de lo que la vista sigue y de lo que la GPU dibuja).
 *   Se deja pasar una repetición cada [PASO_MS]: 10 pasos por segundo, que recorren una fila en unos 2 s.
 */
class RitmoNavegacion {

    var rafaga = false
        private set

    private var ultimaTecla = Long.MIN_VALUE / 2
    private var ultimaAceptada = Long.MIN_VALUE / 2

    /** true si la pulsación debe mover el foco; false si hay que descartarla. */
    fun pulsar(ahoraMs: Long, repeticion: Int): Boolean {
        val seguida = ahoraMs - ultimaTecla < SEGUIDA_MS
        ultimaTecla = ahoraMs
        rafaga = repeticion > 0 || seguida
        if (repeticion > 0 && ahoraMs - ultimaAceptada < PASO_MS) return false
        ultimaAceptada = ahoraMs
        return true
    }

    /** Sin teclas desde hace [REPOSO_MS]: lo aplazado (fondos, cabecera, animaciones completas) puede volver. */
    fun enReposo(ahoraMs: Long): Boolean = ahoraMs - ultimaTecla >= REPOSO_MS

    companion object {
        const val SEGUIDA_MS = 160L
        const val PASO_MS = 100L
        const val REPOSO_MS = 220L
    }
}
