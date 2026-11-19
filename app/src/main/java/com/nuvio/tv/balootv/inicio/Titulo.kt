package com.nuvio.tv.balootv.inicio

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

/** Película o serie, con el nombre que le da cada servicio en sus rutas. */
@Serializable
enum class Tipo(val tmdb: String, val trakt: String) {
    PELICULA("movie", "movies"),
    SERIE("tv", "shows"),
}

/** Los ids de un título. `tmdb` puede faltar en lo que llega de Trakt: entonces la clave sale del de Trakt. */
@Serializable
data class Ids(val tmdb: Int?, val imdb: String? = null, val trakt: Int? = null) {
    val clave: String get() = tmdb?.let { "tmdb:$it" } ?: "trakt:$trakt"
}

/**
 * Un título del Inicio, en castellano (TMDB `es-ES`; sin ficha de TMDB, el nombre de Trakt y sin imágenes). `cartel` y
 * `fondo` son URLs completas (w342 y w1280; el móvil usa w780). Es el `Titulo` del móvil (commit 3e1d288) con lo que
 * usa la tele, y además el `logo` del título (w500) y sus `generos` de TMDB, para el salvapantallas.
 */
@Serializable
data class Titulo(
    val tipo: Tipo,
    val ids: Ids,
    val titulo: String,
    val anio: Int? = null,
    val cartel: String? = null,
    val fondo: String? = null,
    val sinopsis: String? = null,
    val logo: String? = null,
    val generos: List<String> = emptyList(),
) {
    val clave: String get() = "${tipo.name}:${ids.clave}"

    companion object {
        fun minimo(tipo: Tipo, ids: Ids, titulo: String, anio: Int?): Titulo = Titulo(tipo, ids, titulo, anio = anio)
    }
}

object FechaIso : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("Fecha", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
