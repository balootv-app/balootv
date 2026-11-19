package com.nuvio.tv.balootv.fuentes

import com.nuvio.tv.data.local.StreamAutoPlayMode

/** Cómo se ofrece en Ajustes la forma de elegir fuente: BalooTV siempre a mano y primero. */
object ModoSeleccion {

    /** Ajustes esenciales: un solo botón que alterna BalooTV ↔ manual (desde cualquier otro modo, vuelve a BalooTV). */
    fun siguienteEsencial(actual: StreamAutoPlayMode): StreamAutoPlayMode =
        if (actual == StreamAutoPlayMode.BALOOTV) StreamAutoPlayMode.MANUAL else StreamAutoPlayMode.BALOOTV

    /** Ajustes completos: todas las opciones, con BalooTV la primera. */
    val opcionesCompletas: List<StreamAutoPlayMode> =
        listOf(StreamAutoPlayMode.BALOOTV, StreamAutoPlayMode.MANUAL, StreamAutoPlayMode.FIRST_STREAM, StreamAutoPlayMode.REGEX_MATCH)

    /** BalooTV solo distingue automático (BalooTV) y manual: los modos heredados de Nuvio pasan a BalooTV. */
    fun normalizar(actual: StreamAutoPlayMode): StreamAutoPlayMode =
        if (actual == StreamAutoPlayMode.MANUAL) StreamAutoPlayMode.MANUAL else StreamAutoPlayMode.BALOOTV
}
