package com.nuvio.tv.balootv.ui.movimiento

/**
 * Cuándo actualizar lo que acompaña al foco (título, sinopsis): al momento si se navega despacio, y tras
 * [ESPERA_MS] sin cambios si se va en ráfaga, como el Apple TV, que no reescribe la cabecera por cada cartel por el
 * que se pasa. Así no se maqueta texto nuevo en cada paso de la tecla mantenida.
 */
class AmortiguadorFoco(private val reloj: () -> Long = { System.nanoTime() / 1_000_000 }) {

    private var ultimo = Long.MIN_VALUE / 2

    /** ms que esperar antes de mostrar el nuevo título enfocado. */
    fun espera(): Long {
        val ahora = reloj()
        val rapido = ahora - ultimo < RAPIDO_MS
        ultimo = ahora
        return if (rapido) ESPERA_MS else 0L
    }

    companion object {
        const val RAPIDO_MS = 180L
        const val ESPERA_MS = 160L
    }
}
