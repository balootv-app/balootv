package com.nuvio.tv.balootv.ajustes

import com.nuvio.tv.domain.model.Addon

/**
 * Una fila de la pantalla Fuentes: nombre legible, para qué se usa, la explicación de la columna izquierda y la
 * dirección instalada (para quitarla; nunca se enseña, porque puede llevar la clave de TorBox).
 */
data class FilaAddon(val nombre: String, val papel: String, val explicacion: String, val baseUrl: String = "")

/**
 * Qué addons tiene BalooTV y para qué, en el orden en que el motor los consulta: primero las fuentes (dan enlaces),
 * en el orden en que se añadieron, y después fichas y subtítulos. Sirve igual para cualquier conjunto de fuentes.
 */
object ResumenAddons {

    fun filas(addons: List<Addon>): List<FilaAddon> = addons
        .withIndex()
        .sortedWith(compareBy({ (_, a) -> rango(a) }, { it.index }))
        .map { (_, a) ->
            val papel = if (!responde(a)) "Sin respuesta" else papelGenerico(a)
            FilaAddon(
                nombre = a.displayName.ifBlank { a.name }.ifBlank { a.id },
                papel = if (a.enabled || !responde(a)) papel else "$papel · desactivado",
                explicacion = explicacion(a),
                baseUrl = a.baseUrl,
            )
        }

    /** Fuentes activas que han respondido y dan enlaces. */
    fun fuentesEnUso(addons: List<Addon>): Int = addons.count { it.enabled && "stream" in recursos(it) }

    private fun responde(a: Addon) = a.resources.isNotEmpty()

    private fun recursos(a: Addon) = a.resources.map { it.name }.toSet()

    /** Fuentes, fichas, subtítulos y, al final, lo que no responde. */
    private fun rango(a: Addon): Int {
        val r = recursos(a)
        return when {
            "stream" in r -> 1
            "meta" in r || "catalog" in r -> 2
            "subtitles" in r -> 3
            r.isEmpty() -> 5
            else -> 4
        }
    }

    private fun papelGenerico(a: Addon): String {
        val r = recursos(a)
        return listOfNotNull(
            "Fuente".takeIf { "stream" in r },
            "Fichas y catálogo".takeIf { "meta" in r || "catalog" in r },
            "Subtítulos".takeIf { "subtitles" in r },
        ).joinToString(" · ").ifEmpty { "Otros" }
    }

    private fun explicacion(a: Addon): String {
        if (!responde(a)) return "No ha contestado al arrancar; no se le pide nada hasta que responda. Si sigue así, quítala y vuelve a añadirla."
        val r = recursos(a)
        return when {
            "stream" in r -> "Aporta enlaces; el motor de BalooTV los clasifica igual que los demás y solo elige castellano. Pulsa para quitarla."
            "subtitles" in r -> "Subtítulos externos para cuando el fichero no trae los castellanos."
            else -> "Datos de las fichas cuando TMDB no los tiene."
        }
    }
}
