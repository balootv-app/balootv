package com.nuvio.tv.balootv.trailer

/**
 * Los tráileres se abren en SmartTube, el cliente de YouTube de la tele (la app oficial está desactivada).
 * Si no está instalado, el enlace se abre sin forzar app y decide el sistema.
 */
object TrailerSmartTube {

    /** Paquetes de SmartTube, por orden de preferencia (los antiguos con prefijo liskovsoft incluidos). */
    val PAQUETES = listOf(
        "org.smarttube.stable",
        "org.smarttube.beta",
        "com.liskovsoft.smarttubetv",
        "com.liskovsoft.smarttubetv.beta",
    )

    fun paquete(instalado: (String) -> Boolean): String? = PAQUETES.firstOrNull(instalado)

    fun enlace(youtubeId: String): String = "https://www.youtube.com/watch?v=$youtubeId"
}
