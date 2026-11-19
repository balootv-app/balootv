package com.nuvio.tv.balootv.fuentes

import com.nuvio.tv.domain.model.Stream

enum class EtiquetaFuente { REPRODUCIENDO, LISTA, DUDOSA, PREPARAR }

/**
 * Una fila de "Cambiar fuente": lo que se ve y se oye arriba ([formato]; [principal] es lo mismo en texto), abajo el
 * [tipo] de copia en su insignia y de dónde sale ([detalle]), y su estado.
 */
data class LineaFuente(
    val stream: Stream?,
    val principal: String,
    val detalle: String,
    val etiqueta: EtiquetaFuente,
    val formato: Formato,
    val tipo: String? = null,
)

object LineasFuente {

    fun de(p: Puntuado, actual: Boolean = false): LineaFuente {
        val q = p.calidad
        val detalle = listOfNotNull(q.bytes?.let(SelectorBalooTV::tamano), SelectorBalooTV.origen(p.candidato)).joinToString(" · ")
        val etiqueta = when {
            actual -> EtiquetaFuente.REPRODUCIENDO
            p.idioma == Idioma.DUDOSO -> EtiquetaFuente.DUDOSA
            !p.candidato.enCache -> EtiquetaFuente.PREPARAR
            else -> EtiquetaFuente.LISTA
        }
        val formato = SelectorBalooTV.formato(p.idioma, q)
        return LineaFuente(p.candidato.stream, formato.texto, detalle, etiqueta, formato, tipo(q))
    }

    /**
     * La lista del motor (o la versión original si no hay castellano), marcando la que suena. La misma publicación
     * llega a veces de dos addons (o dos configuraciones del mismo): sale una vez, en el sitio de la primera.
     */
    fun de(e: Eleccion, urlActual: String?): List<LineaFuente> {
        val mostrados = e.lista.ifEmpty { (e as? Eleccion.SinEleccion)?.vose.orEmpty() }
        return mostrados
            .groupBy { p -> p.candidato.infoHash?.lowercase()?.let { "$it#${p.candidato.stream?.fileIdx}" } ?: de(p).let { "${it.principal}|${it.tipo}|${it.detalle}" } }
            .values
            .map { grupo ->
                val suena = grupo.firstOrNull { urlActual != null && it.candidato.stream?.url == urlActual }
                de(suena ?: grupo.first(), actual = suena != null)
            }
    }

    /**
     * Lo que se ve y se oye de la fuente que suena, a partir de su texto (para la capa del reproductor). El idioma, el
     * que el motor anotó para esa URL si lo hay; si no, el clasificador.
     */
    fun formatoDe(texto: String?, url: String?): Formato? {
        if (texto.isNullOrBlank()) return null
        val c = Candidato(texto = texto, addon = "", enCache = true, bytes = null, infoHash = null, bingeGroup = null, stream = null)
        val idioma = RegistroIdioma.idioma(url) ?: ClasificadorIdioma.clasificar(c)
        return SelectorBalooTV.formato(idioma, ClasificadorCalidad.clasificar(c))
    }

    /** El tipo de copia, corto: la resolución ya va en su insignia, así que UHDmicro y MicroHD son los dos «Micro». */
    private fun tipo(q: Calidad): String? = when (q.tipo) {
        Tipo.MICRO -> "Micro"
        Tipo.REMUX -> "Remux"
        Tipo.WEBDL -> "WEB"
        Tipo.BLURAY -> "Blu-ray"
        Tipo.RIP -> "Rip"
        Tipo.HDTV -> "HDTV"
        Tipo.DESCONOCIDO -> null
    }
}
