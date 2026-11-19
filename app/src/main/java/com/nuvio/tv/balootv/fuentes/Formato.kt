package com.nuvio.tv.balootv.fuentes

import com.nuvio.tv.balootv.iconos.Pieza
import com.nuvio.tv.balootv.iconos.Simbolo
import com.nuvio.tv.balootv.iconos.conSeparadores

/**
 * Lo que se ve y se oye de una fuente, por partes, para dibujarlo con insignias (propuesta de iconos del 31-10-2026):
 * [resolucion] «4K» o «1080p», [hdr] el que tenga, [idioma] siempre en palabra y [audio] el códec, que solo se enseña en
 * «Cambiar fuente», donde sirve para elegir.
 */
data class Formato(val resolucion: String?, val hdr: Hdr, val idioma: String, val audio: String?) {

    /** En texto, para los avisos: «4K Dolby Vision · Castellano TrueHD». */
    val texto: String
        get() = listOfNotNull(
            listOfNotNull(resolucion, hdrLargo).joinToString(" ").ifEmpty { null },
            listOfNotNull(idioma, audio).joinToString(" "),
        ).joinToString(" · ")

    /** [4K] [HDR10]. */
    val insignias: List<Pieza> get() = listOfNotNull(resolucion, hdrCorto).map(Pieza::Insignia)

    /** 🔊 Castellano y, con [conCodec], el códec en su insignia. */
    fun sonido(conCodec: Boolean): List<Pieza> =
        listOfNotNull(Pieza.Icono(Simbolo.AUDIO, "Audio"), Pieza.Texto(idioma), audio?.takeIf { conCodec }?.let(Pieza::Insignia))

    /** Como en «Cambiar fuente» y el reproductor: [4K] [HDR10] 🔊 Castellano, todo seguido. */
    fun piezas(conCodec: Boolean): List<Pieza> = insignias + sonido(conCodec)

    /** Como bajo «Reproducir», entre otros datos: [4K] [HDR10] · 🔊 Castellano, sin códec. */
    val piezasFicha: List<Pieza> get() = conSeparadores(insignias, sonido(conCodec = false))

    private val hdrLargo: String?
        get() = when (hdr) { Hdr.DV -> "Dolby Vision"; Hdr.HDR10_PLUS -> "HDR10+"; Hdr.HDR10 -> "HDR10"; Hdr.SDR -> null }

    // El logo de Dolby es marca: insignia de texto, como en el inventario de la tele.
    private val hdrCorto: String?
        get() = if (hdr == Hdr.DV) "DV" else hdrLargo
}
