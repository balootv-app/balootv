package com.nuvio.tv.balootv.catalogo

/**
 * El criterio de títulos de BalooTV (catálogo, «Más como esta» y la app del móvil): el de TMDB en es-ES. El anime
 * sin traducción española llega con el título japonés; entonces, el inglés.
 */
object TituloEspanol {
    // Rangos y no \p{IsHiragana}…: el ICU de Android 9 (Fire TV Stick HD) no acepta el prefijo Is y la clase no carga.
    private val JAPONES = Regex("""[\u3040-\u30FF\u31F0-\u31FF\uFF66-\uFF9F\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF]""")

    fun esJapones(nombre: String): Boolean = JAPONES.containsMatchIn(nombre)

    /** [espanol], salvo que esté en japonés y haya un [ingles] que no lo esté. */
    fun elegir(espanol: String, ingles: String?): String =
        if (esJapones(espanol) && ingles != null && !esJapones(ingles)) ingles else espanol
}
