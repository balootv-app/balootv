package com.nuvio.tv.balootv.fuentes

import android.media.MediaCodecList
import android.util.Log

/**
 * Lo que el aparato decodifica, mirado una vez al arrancar (D13). Se pregunta a los decodificadores y no a la
 * pantalla: la tele Xiaomi dice que su pantalla es 1920 × 1080 aunque decodifica 4K y Dolby Vision (02-11-2026).
 * Con 4K y Dolby Vision las preferencias son las de siempre; en un aparato de 1080p (el Fire TV Stick HD) cambian.
 */
object CapacidadesAparato {

    private const val TAG = "BalooAparato"
    private val VIDEO = setOf("video/hevc", "video/avc", "video/av01", "video/x-vnd.on2.vp9")

    fun detectar() {
        runCatching {
            val decodificadores = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
            val cuatroK = decodificadores.any { info ->
                info.supportedTypes.any { tipo ->
                    tipo.lowercase() in VIDEO && runCatching {
                        info.getCapabilitiesForType(tipo).videoCapabilities?.isSizeSupported(3840, 2160) == true
                    }.getOrDefault(false)
                }
            }
            val dolbyVision = decodificadores.any { info -> info.supportedTypes.any { it.equals("video/dolby-vision", ignoreCase = true) } }
            val alto = if (cuatroK) 2160 else 1080
            PreferenciasAparato.fijar(PreferenciasAparato.para(alto, dolbyVision))
            Log.i(TAG, "Decodifica hasta ${alto}p; Dolby Vision: $dolbyVision")
        }.onFailure { Log.w(TAG, "No se pudo mirar el aparato; quedan las preferencias de la tele", it) }
    }
}
