package com.nuvio.tv.balootv.fuentes

/** Idioma del audio en castellano, de más a menos fiable. Solo los dos primeros entran en la elección automática. */
/** JAPONES: solo lo pone el selector en anime (audio japonés en versión original). */
enum class Idioma { CASTELLANO_SEGURO, CASTELLANO_PROBABLE, DUDOSO, LATINO, SIN_ESPANOL, JAPONES }

/**
 * Clasifica el idioma de un enlace con las señales de docs/proyecto/investigacion-fuentes.md:
 * marcas de latino (ganan salvo que también haya marca castellana), título hispanoamericano de TMDB,
 * origen español, marcas de castellano, remux multiidioma con "Spa" y "Dual" sin contexto.
 */
object ClasificadorIdioma {

    private val LATINO = Regex("""\b(lat|latino|latam|latinoamerica|es-419)\b|🇲🇽""")
    // Palabras que solo usan las publicaciones de España. "Dream Cast" es un estudio de doblaje ruso.
    private val CASTELLANO = Regex("""castellano|(?<!dream )\bcast\b|\[esp]|\besp\b""")
    // "Spanish"/"Spa": en discos europeos suele ser castellano; en ripeos de plataformas y anime, a menudo latino.
    private val ESPANOL = Regex("""\bspanish\b|\bespanol\b|\bspa\b""")
    private val CASTELLANO_EXPLICITO = Regex("""castellano|\bcast\b""")
    private val LENGUAS_EN_INGLES = Regex("""\b(english|french|german|italian|portuguese|russian|japanese|hindi|polish|czech|hungarian|turkish|ukrainian)\b""")
    private val OTRAS_LENGUAS = Regex("""\b(english|eng|french|fre|fra|german|ger|deu|italian|ita|portuguese|por|russian|rus|japanese|jpn|hindi|multi|dubs?)\b""")
    private val PLATAFORMA = Regex("""\bweb\b|web-?dl|webrip|webmux|dlmux|\b(amzn|nf|dsnp|hmax|atvp|cr)\b""")
    // Listas de subtítulos ("Sub (Rus Eng Esp)", "💬 🇪🇸 🇬🇧"): sus idiomas no son los del audio.
    // También "[Multi Sub] [EN|FR|ESP]": la lista puede ir en otro corchete justo después.
    private val SUBTITULOS = Regex("""\b(multi ?)?(subs?|subtitles|subtitulos|subtitulado)\b[)\]]?\s*[(\[][^)\]]*[)\]]|💬[^/\n|]*|$SUB_SIN_CORCHETES""")
    // "Sub Rus Eng Spa Fra" (Rutracker), sin paréntesis: la lista de lenguas que sigue a "Sub" es de subtítulos.
    private const val SUB_SIN_CORCHETES =
        """\bsubs?\b(?:[ ,/+]+(?:rus|eng|spa|esp|fra|fre|ger|deu|ita|por|ukr|pol|chi|jpn|kor|ara|heb|tur|dut|nld|cze|hun|swe|nor|dan|fin|gre|rum|bul|hrv|srp|tha|vie|ind|hin)\b)+"""
    private val DUAL = Regex("""\bdual\b""")
    // Torz lista las pistas de audio reales tras 🎙 (sin el selector de variación, que quita la normalización).
    private val AUDIO_TORZ = Regex("""🎙([^/
💬]*)""")
    private const val BANDERA_ESPANA = "🇪🇸"
    private val ORIGEN_ES = Regex("""mejortorrent|wolfmax4k|dontorrent|elitetorrent|pctorrent|pctfenix|atomohd|atomixhq|divxtotal""")
    // La etiqueta de proveedor de Torrentio y Peerflix ("⚙️ Wolfmax4k", "🌐 Wolfmax4k") no basta: ese proveedor también
    // indexa publicaciones internacionales sin castellano ("Obsession…WEBDL Ita Eng", muestreo 002). Solo cuenta como
    // origen español si el nombre lleva además alguna marca de las publicaciones de España.
    // Sin el selector de variación: la normalización lo quita.
    private val ETIQUETA_PROVEEDOR = Regex("""(?:⚙|🌐)\x{FE0F}?\s*(?:mejortorrent|wolfmax4k|dontorrent)""")
    private val MARCA_ESPANA = Regex("""castellano|\bcast\b|\besp\b|\bspa\b|spanish|espanol|\bdual\b|\bcap\.?\s?\d{3,4}\b""")

    // Cualquier pista de español (o de latino, para que el latino se clasifique como tal). Sin ninguna, el
    // resultado es SIN_ESPANOL y se puede saltar todo lo demás: la mayoría de enlaces globales están en inglés.
    private val PISTA = Regex("""castellano|\bcast\b|\besp|\bspa|espanol|\bdual\b|\blat|es-419|🇲🇽|mejortorrent|wolfmax4k|dontorrent|elitetorrent|pctorrent|pctfenix|atomohd|atomixhq|divxtotal""")

    fun puedeSerEspanol(c: Candidato): Boolean = PISTA.containsMatchIn(c.normalizado)

    fun clasificar(c: Candidato, titulos: TitulosTmdb = TitulosTmdb()): Idioma {
        val t = c.normalizado.replace(SUBTITULOS, " ")
        val castellano = CASTELLANO.containsMatchIn(t)
        val spa = ESPANOL.containsMatchIn(t)

        if (LATINO.containsMatchIn(t)) {
            // "Castellano-Ingles-Español Latinoamericano", "Lat-Cast-Ing": trae las dos pistas y el reproductor pone la
            // castellana (encargo 002). Con solo "Spa" o "Esp" junto al latino no se sabe.
            if (CASTELLANO_EXPLICITO.containsMatchIn(t)) return Idioma.CASTELLANO_PROBABLE
            return if (castellano || spa) Idioma.DUDOSO else Idioma.LATINO
        }
        val palabras = comoPalabras(c.texto)
        val (tituloEspana, tituloMexico) = titulosDistintos(titulos)
        if (tituloMexico != null && contiene(palabras, tituloMexico) && (tituloEspana == null || !contiene(palabras, tituloEspana))) {
            return Idioma.LATINO
        }
        if (origenEspanol(t)) return Idioma.CASTELLANO_SEGURO
        // Si Torz dice qué pistas lleva y ninguna es de España, el "Esp" del nombre son subtítulos.
        AUDIO_TORZ.find(t)?.let { if (BANDERA_ESPANA !in it.groupValues[1]) return Idioma.SIN_ESPANOL }
        if (castellano) {
            return if (tituloEspana != null && contiene(palabras, tituloEspana)) Idioma.CASTELLANO_SEGURO else Idioma.CASTELLANO_PROBABLE
        }
        if (spa) {
            if (OTRAS_LENGUAS.containsMatchIn(t) && PLATAFORMA.containsMatchIn(t)) return Idioma.DUDOSO
            // "English French German Italian Spanish" (QxR, r00t): el "Spanish" de los discos de fuera suele ser latino o
            // no estar en el fichero (encargo 002: «La momia de Lee Cronin» solo traía inglés).
            if (LENGUAS_EN_INGLES.findAll(t).map { it.value }.toSet().size >= 2) return Idioma.DUDOSO
            return if (tituloEspana != null && contiene(palabras, tituloEspana)) Idioma.CASTELLANO_SEGURO else Idioma.CASTELLANO_PROBABLE
        }
        if (DUAL.containsMatchIn(t)) return Idioma.DUDOSO
        return Idioma.SIN_ESPANOL
    }

    /**
     * Títulos de España y México en palabras, solo si son distintos entre sí (si coinciden no distinguen nada).
     * Se comparan sin palabras de una letra, para que "SPY x FAMILY" y "SPY×FAMILY" cuenten como el mismo.
     */
    private fun titulosDistintos(titulos: TitulosTmdb): Pair<String?, String?> {
        // Sin traducción de España, TMDB usa el título original (cine español: «Ocho apellidos vascos»); si no se
        // tomara, el título mexicano igual al original delataría un falso latino (encargo 002).
        val es = (titulos.es ?: titulos.original)?.let(::comoPalabras)?.takeIf { it.isNotBlank() }
        val mx = titulos.mx?.let(::comoPalabras)?.takeIf { it.isNotBlank() }
        val sinLetrasSueltas = { s: String -> s.split(' ').filter { it.length > 1 }.joinToString(" ") }
        return if (es != null && mx != null && sinLetrasSueltas(es) == sinLetrasSueltas(mx)) null to null else es to mx
    }

    /** Origen español fiable: el dominio o el grupo en el nombre, o la etiqueta del proveedor con una marca de España. */
    private fun origenEspanol(t: String): Boolean {
        if (!ORIGEN_ES.containsMatchIn(t)) return false
        if (ORIGEN_ES.containsMatchIn(t.replace(ETIQUETA_PROVEEDOR, " "))) return true
        return MARCA_ESPANA.containsMatchIn(t)
    }

    private fun contiene(palabras: String, titulo: String): Boolean = " $palabras ".contains(" $titulo ")
}
