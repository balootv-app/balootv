package com.nuvio.tv.balootv.inicio

import android.content.Context
import com.nuvio.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

/**
 * La ficha de TMDB (`es-ES`) de un título del Inicio, como la lee el móvil (`TmdbDtos.aTitulo` y `aEmision`): nombre,
 * cartel w342, fondo w1280 (el del catálogo; el móvil usa w780) y sinopsis; en las series, además, el estreno de cada
 * temporada (sin la 0; si el próximo episodio es el 1 de una temporada, su fecha manda) y cuántos episodios tiene cada una.
 * El logo, w500 y elegido como en la ficha (`TmdbMetadataService`): en castellano, si no en inglés, si no sin idioma. Los
 * géneros, en castellano y en el orden de TMDB, como los de la ficha.
 */
data class FichaTmdb(
    val titulo: Titulo,
    val estrenos: Map<Int, LocalDate> = emptyMap(),
    val episodiosPorTemporada: Map<Int, Int> = emptyMap(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun de(cuerpo: String, tipo: Tipo): FichaTmdb? {
            val d = runCatching { json.decodeFromString(DetalleTmdb.serializer(), cuerpo) }.getOrNull() ?: return null
            val id = d.id ?: return null
            val titulo = Titulo(
                tipo = tipo,
                ids = Ids(id),
                titulo = d.title ?: d.name ?: d.originalTitle ?: d.originalName ?: "",
                anio = (d.releaseDate ?: d.firstAirDate)?.take(4)?.toIntOrNull(),
                cartel = imagen("w342", d.posterPath),
                fondo = imagen("w1280", d.backdropPath),
                sinopsis = d.overview?.takeIf { it.isNotBlank() },
                logo = imagen("w500", logo(d.images?.logos.orEmpty())),
                generos = d.genres.orEmpty().mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) },
            )
            val temporadas = d.seasons.orEmpty().filter { (it.numero ?: 0) > 0 }
            val proximo = d.proximo?.takeIf { it.episodio == 1 && it.temporada != null }
            val estrenos = temporadas.mapNotNull { t -> fecha(t.fecha)?.let { t.numero!! to it } }.toMap() +
                listOfNotNull(proximo?.let { p -> fecha(p.fecha)?.let { p.temporada!! to it } })
            return FichaTmdb(titulo, estrenos, temporadas.associate { it.numero!! to (it.episodios ?: 0) })
        }

        private fun logo(logos: List<ImagenTmdb>): String? = logos.sortedWith(
            compareByDescending<ImagenTmdb> { it.idioma == "es" && it.region == "ES" }
                .thenByDescending { it.idioma == "es" && it.region == null }
                .thenByDescending { it.idioma == "es" }
                .thenByDescending { it.idioma == "en" }
                .thenByDescending { it.idioma == null },
        ).firstOrNull { it.idioma in IDIOMAS_LOGO }?.ruta

        private val IDIOMAS_LOGO = setOf("es", "en", null)

        private fun imagen(tamano: String, ruta: String?) = ruta?.takeIf { it.isNotBlank() }?.let { "$IMAGENES/$tamano$it" }

        private fun fecha(texto: String?): LocalDate? = try {
            texto?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

const val IMAGENES = "https://image.tmdb.org/t/p"

@Serializable
private data class DetalleTmdb(
    val id: Int? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    val seasons: List<TemporadaTmdb>? = null,
    @SerialName("next_episode_to_air") val proximo: EpisodioTmdb? = null,
    val images: ImagenesTmdb? = null,
    val genres: List<GeneroTmdb>? = null,
)

@Serializable
private data class GeneroTmdb(val name: String? = null)

@Serializable
private data class ImagenesTmdb(val logos: List<ImagenTmdb>? = null)

@Serializable
private data class ImagenTmdb(
    @SerialName("iso_639_1") val idioma: String? = null,
    @SerialName("iso_3166_1") val region: String? = null,
    @SerialName("file_path") val ruta: String? = null,
)

@Serializable
private data class TemporadaTmdb(
    @SerialName("season_number") val numero: Int? = null,
    @SerialName("air_date") val fecha: String? = null,
    @SerialName("episode_count") val episodios: Int? = null,
)

@Serializable
private data class EpisodioTmdb(
    @SerialName("season_number") val temporada: Int? = null,
    @SerialName("episode_number") val episodio: Int? = null,
    @SerialName("air_date") val fecha: String? = null,
)

/** Las fichas de TMDB del Inicio. null si TMDB falla o no tiene el título. */
interface FuenteFichas {
    suspend fun ficha(tipo: Tipo, tmdbId: Int): FichaTmdb?
}

/**
 * Pide a TMDB `movie|tv/{id}?language=es-ES` con sus logos, como mucho 4 a la vez para no ahogar la tele mientras dibuja. Caché
 * propia en disco de 10 MB y respuestas guardadas 24 h: el Inicio se rehace una vez al día.
 */
class ClienteTmdbInicio(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val base: String = "https://api.themoviedb.org/3",
) : FuenteFichas {
    private val turnos = Semaphore(4)

    override suspend fun ficha(tipo: Tipo, tmdbId: Int): FichaTmdb? = turnos.withPermit {
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$base/${tipo.tmdb}/$tmdbId".toHttpUrl().newBuilder()
                    .addQueryParameter("language", "es-ES")
                    .addQueryParameter("append_to_response", "images")
                    .addQueryParameter("include_image_language", "es,en,null")
                    .addQueryParameter("api_key", apiKey)
                    .build()
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    val cuerpo = r.body?.string()
                    if (!r.isSuccessful || cuerpo == null) null else FichaTmdb.de(cuerpo, tipo)
                }
            }.getOrNull()
        }
    }

    companion object {
        private const val HORAS_CACHE = 24

        fun deLaApp(context: Context): ClienteTmdbInicio {
            val http = OkHttpClient.Builder()
                .cache(Cache(File(context.cacheDir, "balootv_tmdb_inicio"), 10L * 1024 * 1024))
                .addNetworkInterceptor { cadena ->
                    cadena.proceed(cadena.request()).newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public, max-age=${HORAS_CACHE * 3600}")
                        .build()
                }
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
            return ClienteTmdbInicio(http, BuildConfig.TMDB_API_KEY)
        }
    }
}
