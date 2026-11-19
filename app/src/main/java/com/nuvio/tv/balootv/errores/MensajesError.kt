package com.nuvio.tv.balootv.errores

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Qué ha pasado y qué hacer, en dos frases cortas: lo que se enseña cuando algo falla (F6). */
data class MensajeError(val titulo: String, val texto: String)

/**
 * Textos de error de BalooTV en castellano y siempre con una salida. Se eligen con lo que se sabe en ese
 * momento: si hay red y qué fuentes han contestado. Las pantallas no inventan sus propios textos.
 */
object MensajesError {

    val SIN_RED = MensajeError(
        "Sin conexión a internet",
        "La tele no tiene internet. Comprueba el Wi-Fi y vuelve a intentarlo.",
    )

    /** Lista de fuentes sin ninguna línea que enseñar. `conError` y `total` cuentan las fuentes que han terminado. */
    fun sinFuentes(hayRed: Boolean, conError: Int, total: Int): MensajeError = when {
        !hayRed -> SIN_RED
        total > 0 && conError >= total -> MensajeError(
            "Las fuentes no responden",
            "Ninguna fuente ha contestado. Suele ser pasajero: vuelve atrás y pulsa Reproducir dentro de un momento.",
        )
        else -> MensajeError(
            "No hay fuentes en castellano",
            "No hay castellano a 1080p o más para este título. Suele llegar unas semanas después del estreno.",
        )
    }

    /** Nota bajo la lista cuando algunas fuentes no han contestado: puede que falten opciones. */
    fun fuentesCaidas(nombres: List<String>): String? = when (nombres.size) {
        0 -> null
        1 -> "${nombres[0]} no ha respondido; puede que falte alguna fuente."
        else -> "${nombres.dropLast(1).joinToString(", ")} y ${nombres.last()} no han respondido; puede que falten fuentes."
    }

    fun ficha(hayRed: Boolean): MensajeError =
        if (!hayRed) SIN_RED else MensajeError("No se ha podido cargar la ficha", "Pulsa Atrás y vuelve a abrirla dentro de un momento.")

    fun inicio(hayRed: Boolean): MensajeError =
        if (!hayRed) SIN_RED else MensajeError("No se ha podido cargar el catálogo", "TMDB no ha contestado. Pulsa OK para volver a intentarlo.")
}

/** ¿Tiene la tele una red con internet ahora mismo? */
object Red {
    fun hayConexion(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
