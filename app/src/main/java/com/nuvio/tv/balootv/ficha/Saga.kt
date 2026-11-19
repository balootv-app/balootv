package com.nuvio.tv.balootv.ficha

import com.nuvio.tv.data.remote.api.TmdbCollectionPart
import java.time.LocalDate

/**
 * La saga de la ficha de película (contrato C5, igual en el móvil; casos en compartido/sagas.json): las partes de la
 * colección de TMDB ya estrenadas (la de hoy cuenta), por fecha de estreno y, si empatan, por id. La película abierta
 * va en su sitio y las partes sin cartel se quedan. Con menos de dos no hay fila.
 */
object Saga {
    private const val SIN_NOMBRE = "La saga"

    fun partes(partes: List<TmdbCollectionPart>, hoy: LocalDate): List<TmdbCollectionPart> {
        val estrenadas = partes
            .mapNotNull { p -> estreno(p.releaseDate)?.takeIf { !it.isAfter(hoy) }?.let { p to it } }
            .sortedWith(compareBy({ it.second }, { it.first.id }))
            .map { it.first }
        return if (estrenadas.size >= 2) estrenadas else emptyList()
    }

    /** Vista en Trakt o marcada y pendiente de subir: el estado de MetaDetailsViewModel ("tmdb:603|movie"). */
    fun vista(id: String, estado: Map<String, Boolean>): Boolean = estado["$id|movie"] == true

    /** El nombre de la colección tal cual llega de TMDB. */
    fun titulo(nombre: String?): String = nombre?.takeIf { it.isNotBlank() } ?: SIN_NOMBRE

    private fun estreno(fecha: String?): LocalDate? =
        fecha?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
