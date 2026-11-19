package com.nuvio.tv.balootv.fuentes

import java.util.Locale

data class Aviso(val cabecera: String, val titulo: String, val texto: String, val botonVO: String, val detalleVO: String)

/** Textos del aviso "Aún no hay versión en castellano" (tablero 22). */
object TextoSinCastellano {

    private val ESPANA = Locale("es", "ES")

    fun de(cabecera: String, idiomaOriginal: String?, motivo: Motivo? = null): Aviso {
        val idioma = idiomaOriginal?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?.let { Locale.forLanguageTag(it).getDisplayLanguage(ESPANA) }
            ?.takeIf { it.isNotBlank() && !it.equals(idiomaOriginal.trim(), ignoreCase = true) }
            ?.replaceFirstChar { it.titlecase(ESPANA) }
        return Aviso(
            cabecera = cabecera,
            titulo = if (motivo == Motivo.SOLO_LATINO) "Solo hay versión en latino" else "Aún no hay versión en castellano",
            texto = if (motivo == Motivo.SOLO_LATINO) {
                "He buscado en todas tus fuentes y solo hay doblaje latino, no castellano de España."
            } else {
                "He buscado en todas tus fuentes y no hay audio en castellano de España a 1080p o más. " +
                    "Suele llegar unas semanas después del estreno."
            },
            botonVO = "Ver en versión original",
            detalleVO = if (idioma != null) "$idioma · subtítulos en castellano" else "Subtítulos en castellano",
        )
    }
}
