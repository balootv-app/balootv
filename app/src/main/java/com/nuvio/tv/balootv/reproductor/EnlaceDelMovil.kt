package com.nuvio.tv.balootv.reproductor

import kotlinx.coroutines.flow.MutableSharedFlow

/** Orden de cierre del reproductor cuando llega un «reproduce esto» del móvil con otro título en marcha. */
object EnlaceDelMovil {
    /** El reproductor lo atiende como si se pulsara Atrás: guarda el punto, avisa a Trakt y suelta el decodificador. */
    val cerrarReproductor = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
