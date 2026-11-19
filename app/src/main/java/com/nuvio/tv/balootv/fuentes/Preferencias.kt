package com.nuvio.tv.balootv.fuentes

/**
 * Reglas y pesos del selector. Los valores por defecto son los de la tele de casa (Dolby Vision 5/8, ARC con AC-3 y
 * DTS, su Wi-Fi) y la regla de no bajar de 1080p; la edición pública usa [PreferenciasAparato.neutras].
 */
data class Preferencias(
    val altoMinimo: Int = 1080,
    /**
     * Lo que el aparato decodifica (D13): por encima de [altoMaximo], o en Dolby Vision sin [dolbyVision], el enlace
     * no se elige solo mientras haya otro en castellano que sí pueda; si no lo hay, se intenta igual.
     */
    val altoMaximo: Int = 2160,
    val dolbyVision: Boolean = true,
    /** Techo de bitrate medio (tamaño ÷ duración) para el Wi-Fi de la tele; por encima no se elige solo. */
    val mbpsMaximo: Double = 60.0,
    val origenesConfianza: Set<String> = setOf("mejortorrent", "wolfmax4k", "dontorrent"),
    val puntosAlto: Map<Int, Int> = mapOf(2160 to 400, 1080 to 200),
    val puntosHdr: Map<Hdr, Int> = mapOf(Hdr.DV to 60, Hdr.HDR10_PLUS to 55, Hdr.HDR10 to 50, Hdr.SDR to 0),
    val puntosAudio: Map<Audio?, Int> = mapOf(
        Audio.AC3 to 40, Audio.DTS to 40, Audio.EAC3 to 25, Audio.TRUEHD to 10, Audio.DTSHD to 10,
        Audio.AAC to 5, Audio.OTRO to 5, null to 15,
    ),
    val puntosTipo: Map<Tipo, Int> = mapOf(
        Tipo.WEBDL to 25, Tipo.REMUX to 20, Tipo.BLURAY to 20, Tipo.MICRO to 10, Tipo.RIP to 5,
        Tipo.HDTV to 0, Tipo.DESCONOCIDO to 5,
    ),
    // Mayor que cualquier diferencia de HDR + audio + tipo (máx. 115) y menor que la de resolución (200):
    // a igual resolución manda la certeza del castellano; un 4K probable sigue ganando a un 1080p seguro.
    val puntosCastellanoSeguro: Int = 150,
    val puntosOrigenConfianza: Int = 15,
)

/**
 * Las preferencias del aparato en el que corre la app. Por defecto, las de la tele (4K y Dolby Vision); al
 * arrancar, `CapacidadesAparato` las ajusta a lo que decodifica de verdad.
 */
object PreferenciasAparato {
    /**
     * Sonido sin la barra de casa: el AC-3 y el DTS no ganan por el ARC, solo el multicanal a lo estéreo, y lo más
     * pesado (TrueHD, DTS-HD) algo menos, porque no todos los aparatos lo pasan. Techo de bitrate algo más alto que
     * el del Wi-Fi de casa para 4K.
     */
    val neutras = Preferencias(
        mbpsMaximo = 80.0,
        puntosAudio = mapOf(
            Audio.AC3 to 30, Audio.DTS to 30, Audio.EAC3 to 30, Audio.TRUEHD to 20, Audio.DTSHD to 20,
            Audio.AAC to 10, Audio.OTRO to 5, null to 15,
        ),
    )

    // Después de [neutras]: se inicializa en orden y [para] la usa.
    @Volatile
    var actual: Preferencias = para(altoMaximo = 2160, dolbyVision = true)
        private set

    /**
     * Lo que corresponde a un aparato que decodifica hasta [altoMaximo] (con o sin Dolby Vision). En casa, las de la
     * tele; en la edición pública, las [neutras]. Un aparato de 1080p, como un Fire TV Stick HD, va con un techo de
     * bitrate más bajo: su Wi-Fi da menos.
     */
    fun para(altoMaximo: Int, dolbyVision: Boolean, casa: Boolean = com.nuvio.tv.balootv.edicion.Edicion.esCasa): Preferencias {
        val base = if (casa) Preferencias() else neutras
        return if (altoMaximo >= 2160 && dolbyVision) base else base.copy(
            altoMaximo = altoMaximo, dolbyVision = dolbyVision,
            mbpsMaximo = if (altoMaximo >= 2160) base.mbpsMaximo else 30.0,
        )
    }

    fun fijar(p: Preferencias) {
        actual = p
    }
}
