package com.nuvio.tv.balootv.fuentes

enum class Hdr { DV, HDR10_PLUS, HDR10, SDR }

/** Formato de audio. La barra (ARC) solo recibe directos AC3 y DTS; el resto se transcodifica a AC-3. */
enum class Audio { AC3, DTS, EAC3, TRUEHD, DTSHD, AAC, OTRO }

enum class Tipo { REMUX, WEBDL, BLURAY, RIP, MICRO, HDTV, DESCONOCIDO }

data class Calidad(
    val alto: Int?,
    val hdr: Hdr,
    val codec: String?,
    val audio: Audio?,
    val tipo: Tipo,
    val bytes: Long?,
    val camara: Boolean,
)

/** Lee del texto del enlace la resolución, el HDR, el códec, el audio de la pista castellana, el tipo y el tamaño. */
object ClasificadorCalidad {

    private val ALTOS = listOf(
        2160 to Regex("""\b(2160p|4k|uhd)"""),
        1080 to Regex("""\b1080[pi]\b"""),
        720 to Regex("""\b720p\b"""),
        576 to Regex("""\b576p\b"""),
        480 to Regex("""\b480p\b"""),
    )
    private val HDRS = listOf(
        Hdr.DV to Regex("""\b(dv|dovi|dolby ?vision)\b"""),
        Hdr.HDR10_PLUS to Regex("""hdr10(\+|plus)"""),
        Hdr.HDR10 to Regex("""\bhdr(10)?\b"""),
    )
    private val CODECS = listOf(
        "HEVC" to Regex("""\b(x265|h\.?265|hevc)\b"""),
        "AV1" to Regex("""\bav1\b"""),
        "AVC" to Regex("""\b(x264|h\.?264|avc)\b"""),
    )
    // El orden importa cuando dos coinciden en la misma posición: DTS-HD antes que DTS.
    private val AUDIOS = listOf(
        Audio.DTSHD to Regex("""dts[- ]?(hd|x)|dts[- ]?ma\b"""),
        Audio.TRUEHD to Regex("""truehd|atmos"""),
        Audio.EAC3 to Regex("""e-?ac-?3|\bddp|\bdd\+"""),
        Audio.AC3 to Regex("""(?<![a-z-])ac-?3|\bdd5|\bdd 5"""),
        Audio.DTS to Regex("""\bdts\b"""),
        Audio.AAC to Regex("""\baac\b"""),
    )
    private val TIPOS = listOf(
        Tipo.REMUX to Regex("""remux"""),
        Tipo.MICRO to Regex("""micro"""),
        Tipo.WEBDL to Regex("""web[- ]?dl"""),
        Tipo.HDTV to Regex("""\bhdtv\b"""),
        Tipo.RIP to Regex("""rip\b"""),
        Tipo.BLURAY to Regex("""blu-?ray"""),
    )
    private val CAMARA = Regex("""\b(cam|hdcam|ts|telesync|hdts|tc|telecine)\b""")
    private val TAMANO = Regex("""(\d+(?:[.,]\d+)?)\s*(tb|gb|mb)\b""")

    fun clasificar(c: Candidato): Calidad {
        val t = c.normalizado
        return Calidad(
            alto = ALTOS.firstOrNull { it.second.containsMatchIn(t) }?.first,
            hdr = HDRS.firstOrNull { it.second.containsMatchIn(t) }?.first ?: Hdr.SDR,
            codec = CODECS.firstOrNull { it.second.containsMatchIn(t) }?.first,
            audio = audioCastellano(t),
            tipo = TIPOS.firstOrNull { it.second.containsMatchIn(t) }?.first ?: Tipo.DESCONOCIDO,
            bytes = c.bytes ?: tamanoDelTexto(t),
            camara = CAMARA.containsMatchIn(t),
        )
    }

    /** El primer formato citado tras "castellano"; si no se nombra la pista, el primero del texto. */
    private fun audioCastellano(t: String): Audio? {
        val i = t.indexOf("castellano")
        val ventana = if (i >= 0) t.substring(i, minOf(t.length, i + 30)) else t
        return primerAudio(ventana) ?: if (i >= 0) primerAudio(t) else null
    }

    private fun primerAudio(texto: String): Audio? =
        AUDIOS.mapNotNull { (audio, rx) -> rx.find(texto)?.let { it.range.first to audio } }
            .minByOrNull { it.first }?.second

    private fun tamanoDelTexto(t: String): Long? {
        val m = TAMANO.findAll(t).lastOrNull() ?: return null
        val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val factor = when (m.groupValues[2]) { "tb" -> 1e12; "gb" -> 1e9; else -> 1e6 }
        return Math.round(n * factor)
    }
}
