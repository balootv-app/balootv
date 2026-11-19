package com.nuvio.tv.balootv.ficha

import com.nuvio.tv.balootv.reproductor.PublicidadSubtitulos

/**
 * Algunas sinopsis de TMDB llegan con un anuncio delante: «web-de-pelis.net – Películas y series HD, Gratis  Una
 * peligrosa banda…». Se quita ese arranque solo cuando es claramente un anuncio: empieza por una web, sigue un
 * separador y un eslogan con palabras de anuncio, y después queda texto de verdad. Ante la duda, se deja tal cual
 * (mejor un anuncio que comerse una sinopsis buena, como «Amazon.com nació en un garaje…»).
 */
object SinopsisSinAnuncio {

    private val ANUNCIO by lazy {
        Regex(
            """^\s*(?:https?://)?(?:www\.)?[\p{L}0-9-]{2,}\.(?:${PublicidadSubtitulos.DOMINIOS}|es)\b""" +
                """\s*[-–—|:·]\s*(.{0,90}?)(?:\s{2,}|\s*[.!]\s+|\s*\n\s*|\s*[|·]\s+)""",
            RegexOption.IGNORE_CASE,
        )
    }
    private val PALABRAS_DE_ANUNCIO by lazy {
        Regex("""\b(?:gratis|hd|online|ver|descargar?|pel[ií]culas|series|estrenos|completas?|castellano|latino|torrent)\b""", RegexOption.IGNORE_CASE)
    }

    /**
     * Caracteres que no se ven y que algunas webs meten en su nombre para que no se pueda buscar (se vio en TMDB:
     * «v·e·r…», con un U+200C entre letra y letra). Se quitan antes de buscar el anuncio.
     */
    private val INVISIBLES by lazy { Regex("[\u00AD\u200B-\u200F\u2060\uFEFF]") }

    /** La sinopsis sin el anuncio del principio; si no lo hay (o no queda texto suficiente), la misma. */
    fun limpiar(original: String): String {
        val texto = runCatching { INVISIBLES.replace(original, "") }.getOrDefault(original)
        val m = runCatching { ANUNCIO.find(texto) }.getOrNull() ?: return original
        if (!PALABRAS_DE_ANUNCIO.containsMatchIn(m.groupValues[1])) return original
        val resto = texto.substring(m.range.last + 1).trim()
        return if (resto.length >= MINIMO) resto else original
    }

    /** Lo que tiene que quedar para darla por buena: si no, era todo anuncio o algo raro y se deja como estaba. */
    private const val MINIMO = 30
}
