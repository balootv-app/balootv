package com.nuvio.tv.balootv.fuentes

/**
 * ¿La pista de audio que suena es castellano? Sirve al reproductor para poner solo subtítulos forzados
 * cuando el audio ya está en castellano. Muchas publicaciones españolas no etiquetan el idioma de la pista
 * ("und"): entonces decide el nombre del fichero, con el mismo clasificador que eligió la fuente.
 */
object AudioCastellano {

    private val LATINO = Regex("""\b(lat|latino|latam|latinoamerica|mexico)\b""")
    private val CASTELLANO = Regex("""castellano|espana|\bespanol\b|\bspanish\b""")
    private val SIN_IDIOMA = setOf("", "und", "unknown", "mul", "zxx")

    /** `idiomaFuente`: lo que el motor decidió de este enlace (RegistroIdioma), si lo sabe; manda sobre el nombre. */
    fun esCastellano(idiomaPista: String?, nombrePista: String?, textoFuente: String?, idiomaFuente: Idioma? = null): Boolean {
        val idioma = idiomaPista?.trim()?.lowercase().orEmpty()
        val nombre = nombrePista?.let(::normalizar).orEmpty()
        if (LATINO.containsMatchIn(nombre) || idioma.endsWith("419") || idioma.endsWith("-mx")) return false
        if (idioma == "es" || idioma == "spa" || idioma == "es-es") return true
        if (CASTELLANO.containsMatchIn(nombre)) return true
        if (idioma !in SIN_IDIOMA) return false
        if (idiomaFuente != null) return idiomaFuente == Idioma.CASTELLANO_SEGURO || idiomaFuente == Idioma.CASTELLANO_PROBABLE
        if (textoFuente.isNullOrBlank()) return false
        val fuente = Candidato(textoFuente, "", true, null, null, null, null)
        return ClasificadorIdioma.clasificar(fuente) in setOf(Idioma.CASTELLANO_SEGURO, Idioma.CASTELLANO_PROBABLE)
    }
}
