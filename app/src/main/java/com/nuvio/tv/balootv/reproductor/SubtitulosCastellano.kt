package com.nuvio.tv.balootv.reproductor

import com.nuvio.tv.domain.model.Subtitle
import com.nuvio.tv.ui.screens.player.TrackInfo
import java.text.Normalizer

/** Lo que ofrece el panel de subtítulos: solo castellano de España (spec F4 §4). */
data class OpcionesSubtitulos(val forzados: TrackInfo?, val completoInterno: TrackInfo?, val completoExterno: Subtitle?) {
    val hayCastellano: Boolean get() = forzados != null || completoInterno != null || completoExterno != null
}

enum class EleccionSubtitulos { FORZADOS, CASTELLANO, DESACTIVADOS }

/**
 * Subtítulos en castellano y nada más. Muchas pistas latinas van como "spa"/"es" a secas: decide el nombre
 * ("Latino", "LAT", "Latinoamérica", "México") o la región (es-419, es-MX…). Solo es castellano un código
 * español sin región o con región ES.
 */
object SubtitulosCastellano {

    private val CODIGOS = setOf("es", "spa", "spn", "esp", "es-es", "spa-es", "castellano", "spanish")
    // "Latin American", "Spanish (Latin America)": así llaman a la pista latina los discos y las plataformas (encargo 002).
    private val LATINO = Regex("""\blat(?:ino|inoamerica(?:no)?|am|am\.|in)?\b|latinoamerica|mexic|\b419\b""")

    fun esCastellano(idioma: String?, nombre: String?): Boolean {
        val codigo = idioma?.trim()?.lowercase()?.replace('_', '-') ?: return false
        if (codigo !in CODIGOS) return false
        return !LATINO.containsMatchIn(normalizar(nombre.orEmpty()))
    }

    fun opciones(internas: List<TrackInfo>, externos: List<Subtitle>): OpcionesSubtitulos {
        val castellanas = internas.filter { esCastellano(it.language, it.name) }
        val sinMarca = castellanas.filterNot(::esForzada)
        // Muchas copias (Wolfmax4k, MejorTorrent) traen dos pistas en castellano sin nombre ni marca: la primera, la
        // que se pone sola, son los forzados, y la otra los completos. Visto en el móvil el 18-11-2026 (Resident
        // Evil, 2026): «Castellano» ponía la de forzados y no salía nada. Regla del móvil (balootv-movil 1d8b0f7).
        // Solo si no se distinguen por el nombre: «Castellano» y «Castellano SDH» (o «… PGS») son dos completos.
        val forzadosSinMarca = sinMarca
            .takeIf { castellanas.none(::esForzada) && it.size >= 2 && it.map { t -> normalizar(t.name) }.distinct().size == 1 }
            ?.let { varias -> varias.firstOrNull { it.isDefault } ?: varias.first() }
            ?.takeIf { !COMPLETOS.containsMatchIn(normalizar(it.name)) }
        val completas = sinMarca.filter { it != forzadosSinMarca }
        // Si el nombre no las distingue, la completa suele ser la última: en La llegada de MejorTorrent (18-11-2026)
        // la segunda de tres no tenía ni una línea en los diálogos. Regla del móvil (balootv-movil e3a037e).
        val indistinguibles = completas.size >= 2 && completas.map { normalizar(it.name) }.distinct().size == 1
        return OpcionesSubtitulos(
            forzados = castellanas.firstOrNull(::esForzada) ?: forzadosSinMarca,
            completoInterno = if (indistinguibles) completas.last() else completas.firstOrNull(),
            completoExterno = externos.firstOrNull { esCastellano(it.lang, it.addonName + " " + it.id) },
        )
    }

    /** Lo que está puesto ahora, en los términos del panel: [seleccionada] es el índice de la pista interna (−1, ninguna). */
    fun eleccion(opciones: OpcionesSubtitulos, internas: List<TrackInfo>, seleccionada: Int, conAddon: Boolean): EleccionSubtitulos = when {
        conAddon -> EleccionSubtitulos.CASTELLANO
        seleccionada < 0 -> EleccionSubtitulos.DESACTIVADOS
        internas.firstOrNull { it.index == seleccionada } == opciones.forzados -> EleccionSubtitulos.FORZADOS
        else -> EleccionSubtitulos.CASTELLANO
    }

    private val FORZADOS = Regex("forzad|forced")
    private val COMPLETOS = Regex("""complet|full|sdh|sordos|\bcc\b""")

    private fun esForzada(t: TrackInfo): Boolean = t.isForced || FORZADOS.containsMatchIn(normalizar(t.name))

    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
