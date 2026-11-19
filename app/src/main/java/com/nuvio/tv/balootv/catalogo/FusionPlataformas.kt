package com.nuvio.tv.balootv.catalogo

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Un título tal como lo da TMDB en `discover` o `search` (rutas de imagen sin tamaño). */
data class Titulo(
    val tmdbId: Int,
    val tipo: TipoFila,
    val nombre: String,
    val poster: String?,
    val fondo: String?,
    val sinopsis: String?,
    val fecha: String?,
    val nota: Double?,
    val votos: Int?,
    val popularidad: Double?,
    val generos: List<Int> = emptyList(),
    val idiomaOriginal: String? = null,
)

data class TituloEnPlataforma(val titulo: Titulo, val plataforma: Plataforma)

/**
 * Mezcla las respuestas de una fila pedida plataforma a plataforma. Así cada título sabe dónde está sin
 * pedir sus proveedores uno a uno; si está en varias, se queda con la primera de la lista de preferencia.
 */
object FusionPlataformas {

    fun fusionar(porPlataforma: List<Pair<Plataforma, List<Titulo>>>, orden: Orden, maximo: Int = 40): List<TituloEnPlataforma> {
        val vistos = LinkedHashMap<Int, TituloEnPlataforma>()
        porPlataforma.forEach { (plataforma, titulos) ->
            titulos.forEach { t -> vistos.putIfAbsent(t.tmdbId, TituloEnPlataforma(t, plataforma)) }
        }
        val criterio: Comparator<TituloEnPlataforma> = when (orden) {
            Orden.POPULARIDAD -> compareByDescending { it.titulo.popularidad ?: 0.0 }
            Orden.FECHA -> compareByDescending { it.titulo.fecha.orEmpty() }
            Orden.VALORACION -> compareByDescending { it.titulo.nota ?: 0.0 }
        }
        return vistos.values.sortedWith(criterio).take(maximo)
    }
}

/** Traduce las respuestas JSON de TMDB (`discover` y `search`) a [Titulo]. */
object TmdbCatalogoJson {

    fun titulos(cuerpo: String, tipo: TipoFila): List<Titulo> {
        val serie = tipo == TipoFila.SERIE
        return JsonParser.parseString(cuerpo).asJsonObject.getAsJsonArray("results").orEmpty()
            .mapNotNull { e ->
                val o = e.asJsonObject
                val id = o.get("id")?.asInt ?: return@mapNotNull null
                val nombre = o.texto(if (serie) "name" else "title") ?: return@mapNotNull null
                Titulo(
                    tmdbId = id,
                    tipo = tipo,
                    nombre = nombre,
                    poster = o.texto("poster_path"),
                    fondo = o.texto("backdrop_path"),
                    sinopsis = o.texto("overview")?.takeIf { it.isNotBlank() },
                    fecha = o.texto(if (serie) "first_air_date" else "release_date")?.takeIf { it.isNotBlank() },
                    nota = o.numero("vote_average"),
                    votos = o.numero("vote_count")?.toInt(),
                    popularidad = o.numero("popularity"),
                    generos = o.getAsJsonArray("genre_ids")?.mapNotNull { g -> g.takeIf { it.isJsonPrimitive }?.asInt }.orEmpty(),
                    idiomaOriginal = o.texto("original_language"),
                )
            }
    }

    private fun com.google.gson.JsonArray?.orEmpty(): List<com.google.gson.JsonElement> = this?.toList() ?: emptyList()

    private fun JsonObject.texto(campo: String): String? = get(campo)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.numero(campo: String): Double? = get(campo)?.takeIf { it.isJsonPrimitive }?.asDouble
}
