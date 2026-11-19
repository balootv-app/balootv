package com.nuvio.tv.balootv.fuentes

/** Qué audio trae una publicación de anime, a efectos de verlo en japonés. */
enum class AudioAnime { JAPONES, DUAL, SOLO_DOBLAJE, SIN_DATOS }

/**
 * Anime en versión original (spec F4 §5): vale lo que trae japonés (sola o junto a un doblaje) y lo que no dice
 * nada (las publicaciones "raw" o de fansubs no lo ponen); nunca un doblaje sin japonés. Las marcas de los
 * addons que listan pistas (🎙 audio / 💬 subtítulos) mandan sobre las palabras del nombre.
 */
object ClasificadorAnime {

    private val AUDIO_MARCADO = Regex("🎙([^/\n💬]*)")
    private val SUBS_MARCADOS = Regex("💬([^\n]*)")
    private val ORIGEN_MARCADO = Regex("🌐([^/\n💬⚙]*)")
    private const val JAPON = "🇯🇵"
    private const val ESPANA = "🇪🇸"

    private val DUAL = Regex("""\b(dual|multi audio|multi|dublado)\b""")
    private val SUBTITULADO = Regex("""\b(multiple subtitles?|multi subs?|multisubs?|vose|vos|subtitulado|subbed|sub|subs)\b""")
    private val JAPONES = Regex("""\b(japanese|japones|jap|jpn|japonais|raw|raws)\b""")
    private val FANSUBS = Regex("""\b(subsplease|erai raws|horriblesubs|judas|ember|asw|yameii|varyg|tsundere|anime time|smallsizedanimations)\b""")
    private val DOBLAJE = Regex("""\b(castellano|espanol|spanish|latino|lat|esp|spa|cast)\b""")
    private val SUBS_ESPANOL = Regex("""\b(vose|subtitulado|subs? esp(anol)?|sub spa|subs? castellano)\b""")
    private val VARIOS_SUBS = Regex("""\b(multiple subtitles?|multi subs?|multisubs?)\b""")
    private val LATINA = Regex("""\b(lat|latino|latam)\b""")
    private val ESPANOL = Regex("""\b(spa|esp|spanish|espanol|castellano)\b""")

    fun audio(c: Candidato): AudioAnime {
        AUDIO_MARCADO.find(c.texto)?.groupValues?.get(1)?.let { banderas ->
            val japones = JAPON in banderas
            val otro = banderas(banderas).any { it != JAPON }
            return when {
                japones && otro -> AudioAnime.DUAL
                japones -> AudioAnime.JAPONES
                otro -> AudioAnime.SOLO_DOBLAJE
                else -> AudioAnime.SIN_DATOS
            }
        }
        // Sin 🎙, algunos addons ponen tras 🌐 el idioma de la publicación: si no es japonés, es un doblaje («Pokemon The First
        // Movie … BHDStudio», 🌐 🇬🇧, solo inglés en la cabecera; muestreo 002).
        ORIGEN_MARCADO.find(c.texto)?.groupValues?.get(1)?.let { banderas ->
            val todas = banderas(banderas)
            if (todas.isNotEmpty() && JAPON !in todas) return AudioAnime.SOLO_DOBLAJE
        }
        val t = comoPalabras(c.texto)
        return when {
            DUAL.containsMatchIn(t) -> AudioAnime.DUAL
            SUBTITULADO.containsMatchIn(t) || JAPONES.containsMatchIn(t) || FANSUBS.containsMatchIn(t) -> AudioAnime.JAPONES
            DOBLAJE.containsMatchIn(t) -> AudioAnime.SOLO_DOBLAJE
            else -> AudioAnime.SIN_DATOS
        }
    }

    // Rangos y no \p{IsHan}…: el ICU de Android 9 (Fire TV Stick HD) no acepta el prefijo Is.
    private val HAN = Regex("""[\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF]""")
    private val KANA = Regex("""[\u3040-\u30FF\u31F0-\u31FF\uFF66-\uFF9F]""")
    private val GRUPOS_CHINOS = Regex("""\b(colortv|bphdtv|4bt0|ddhdtv)\b""")
    private val ORIGEN_CHINO = Regex("""🌐\s*🇨🇳""")

    /**
     * Publicación china: título en chino (ideogramas sin kana), doblaje chino (中配), origen 🇨🇳 en la marca 🌐 o grupos
     * chinos conocidos. Suelen llevar los subtítulos chinos grabados en la imagen: nunca valen para anime.
     */
    fun china(c: Candidato): Boolean =
        "中配" in c.texto ||
            ORIGEN_CHINO.containsMatchIn(c.texto) ||
            GRUPOS_CHINOS.containsMatchIn(comoPalabras(c.texto)) ||
            (HAN.containsMatchIn(c.texto) && !KANA.containsMatchIn(c.texto))

    /** Las banderas (pares de letras de indicador regional, U+1F1E6–U+1F1FF) de un texto. */
    private fun banderas(texto: String): List<String> = texto.codePoints().toArray()
        .filter { it in 0x1F1E6..0x1F1FF }
        .chunked(2)
        .filter { it.size == 2 }
        .map { par -> String(par.toIntArray(), 0, 2) }

    /** ¿Trae subtítulos en español (de España) dentro? Para preferirla a igual calidad. */
    fun subtitulosEspanol(c: Candidato): Boolean {
        // En una publicación latina ("Dual-Lat"), el 🇪🇸 de los subtítulos suele ser español latinoamericano.
        if (LATINA.containsMatchIn(comoPalabras(c.texto))) return false
        SUBS_MARCADOS.find(c.texto)?.groupValues?.get(1)?.let { return ESPANA in it }
        val t = comoPalabras(c.texto)
        return SUBS_ESPANOL.containsMatchIn(t) || (VARIOS_SUBS.containsMatchIn(t) && ESPANOL.containsMatchIn(t))
    }
}
