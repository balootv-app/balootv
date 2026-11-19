package com.nuvio.tv.balootv.fuentes

/**
 * Reproducir sin esperar a la fuente más lenta: solo si lo recibido ya incluye castellano seguro en 4K,
 * que nada de lo que falte puede mejorar en la práctica. Con cualquier otra cosa se espera a todas.
 */
object InicioAnticipado {
    fun puedeEmpezar(e: Eleccion): Boolean =
        e is Eleccion.Elegido && e.mejor.idioma == Idioma.CASTELLANO_SEGURO && e.mejor.calidad.alto == 2160
}
