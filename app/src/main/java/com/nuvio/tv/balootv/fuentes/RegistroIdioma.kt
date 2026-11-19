package com.nuvio.tv.balootv.fuentes

/**
 * Idioma que el motor asignó a cada enlace, por URL y por nombre de fichero, para que el reproductor lo
 * consulte: el nombre del fichero no siempre dice "Castellano" (lo decía el título del enlace).
 */
object RegistroIdioma {

    private val idiomas = object : LinkedHashMap<String, Idioma>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Idioma>?) = size > 4000
    }

    fun recordar(claves: List<String?>, idioma: Idioma) = synchronized(idiomas) {
        claves.filterNotNull().filter { it.isNotBlank() }.forEach { idiomas[it.trim()] = idioma }
    }

    fun idioma(vararg claves: String?): Idioma? = synchronized(idiomas) {
        claves.filterNotNull().firstNotNullOfOrNull { idiomas[it.trim()] }
    }
}
