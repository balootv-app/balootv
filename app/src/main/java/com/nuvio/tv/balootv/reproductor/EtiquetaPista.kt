package com.nuvio.tv.balootv.reproductor

import com.nuvio.tv.balootv.fuentes.AudioCastellano
import com.nuvio.tv.ui.screens.player.TrackInfo
import java.util.Locale

/** "Castellano · DTS 5.1": cómo se nombra una pista de audio en el panel del reproductor. */
object EtiquetaPista {

    private val ESPANA = Locale("es", "ES")
    private val LATINO = Regex("latin|\\blat\\b|mexic|419", RegexOption.IGNORE_CASE)
    private val CANALES = Regex("""\b[1-7]\.[01]\b""")

    /** [textoFuente]: nombre de la fuente o del fichero, que decide las pistas sin idioma ("und"). */
    fun audio(t: TrackInfo, textoFuente: String? = null): String {
        val idioma = idioma(t.language, t.name)
            ?: if (AudioCastellano.esCastellano(t.language, t.name, textoFuente)) "Castellano" else return t.name
        val nombre = t.name.lowercase()
        val formato = listOfNotNull(
            codec(t.codec) ?: codec(nombre),
            canales(t.channelCount) ?: CANALES.find(nombre)?.value?.let { if (it == "2.0") "estéreo" else it },
        ).joinToString(" ")
        return if (formato.isEmpty()) idioma else "$idioma · $formato"
    }

    private fun idioma(codigo: String?, nombre: String): String? {
        val c = codigo?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "und" } ?: return null
        if (c.startsWith("es") || c == "spa") return if (LATINO.containsMatchIn(nombre) || c.length > 3 && c != "es-es") "Latino" else "Castellano"
        val nombreIdioma = Locale.forLanguageTag(c).getDisplayLanguage(ESPANA)
        if (nombreIdioma.isBlank() || nombreIdioma.equals(c, ignoreCase = true)) return null
        return nombreIdioma.replaceFirstChar { it.titlecase(ESPANA) }
    }

    private fun codec(mime: String?): String? {
        val m = mime?.lowercase() ?: return null
        return when {
            "eac3" in m || "e-ac-3" in m -> "E-AC-3"
            "ac3" in m || "ac-3" in m -> "AC-3"
            "true-hd" in m || "truehd" in m -> "TrueHD"
            "dts-hd" in m || "dts.hd" in m -> "DTS-HD"
            "dts" in m -> "DTS"
            "mp4a" in m || "aac" in m -> "AAC"
            "opus" in m -> "Opus"
            "flac" in m -> "FLAC"
            else -> null
        }
    }

    private fun canales(n: Int?): String? = when (n) {
        null, 0 -> null
        1 -> "mono"
        2 -> "estéreo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$n canales"
    }
}
