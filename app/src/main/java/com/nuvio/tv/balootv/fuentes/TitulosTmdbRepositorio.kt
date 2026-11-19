package com.nuvio.tv.balootv.fuentes

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nuvio.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pide a TMDB, por id de IMDb (o `tmdb:N` si la ficha no lo pudo convertir), los títulos de España y México, el original
 * y el inglés, la duración, si ya ha salido en casa y si hay otra película con el mismo título, para el clasificador de
 * idioma, el validador y el techo de bitrate. Guarda la respuesta en memoria por título. Si TMDB falla, devuelve la
 * petición sin títulos: el motor sigue, pero no confirma ningún enlace (no se reproduce nada solo a ciegas).
 */
class TitulosTmdbRepositorio(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val base: String = "https://api.themoviedb.org/3",
    private val hoy: () -> LocalDate = LocalDate::now,
) {
    private data class Datos(
        val titulos: TitulosTmdb,
        val duracionMin: Int?,
        val anime: Boolean,
        val homonimos: Boolean = false,
        val fechasCasa: List<String> = emptyList(),
        val cines: String? = null,
        /** false si la búsqueda de homónimos no contestó: no se guarda, para repetirla la próxima vez. */
        val completo: Boolean = true,
    )

    private val cache = ConcurrentHashMap<String, Datos>()

    /** [imdbId]: `tt…` o `tmdb:N` (lo que da [idDe]). */
    suspend fun peticion(imdbId: String, esSerie: Boolean, anio: Int?, temporada: Int?, episodio: Int?): Peticion {
        val clave = "$imdbId|$esSerie"
        val datos = cache[clave] ?: withContext(Dispatchers.IO) { runCatching { pedir(imdbId, esSerie) }.getOrNull() }
            ?.also { if (it.completo) cache[clave] = it }
        return Peticion(
            titulos = datos?.titulos ?: TitulosTmdb(),
            anio = anio,
            temporada = temporada,
            episodio = episodio,
            duracionMin = datos?.duracionMin,
            anime = datos?.anime ?: Anime.es(emptySet(), null, imdbId),
            homonimos = datos?.homonimos ?: false,
            estrenadaEnCasa = if (esSerie || datos == null) null else EstrenoEnCasa.yaSalio(datos.fechasCasa, datos.cines, hoy()),
        )
    }

    private fun pedir(id: String, esSerie: Boolean): Datos? {
        val tipo = if (esSerie) "tv" else "movie"
        val tmdbId = if (id.startsWith("tmdb:")) {
            id.removePrefix("tmdb:").toIntOrNull()
        } else {
            val encontrado = json("$base/find/$id?external_source=imdb_id&api_key=$apiKey") ?: return null
            encontrado.getAsJsonArray(if (esSerie) "tv_results" else "movie_results")?.firstOrNull()?.asJsonObject?.get("id")?.asInt
        } ?: return null
        val extra = if (esSerie) "translations" else "translations,release_dates"
        val ficha = json("$base/$tipo/$tmdbId?append_to_response=$extra&api_key=$apiKey") ?: return null
        val campo = if (esSerie) "name" else "title"
        val traducciones = ficha.getAsJsonObject("translations")?.getAsJsonArray("translations")
            ?.map { it.asJsonObject }.orEmpty()
        fun titulo(pais: String) = traducciones
            .firstOrNull { it.texto("iso_639_1") == "es" && it.texto("iso_3166_1") == pais }
            ?.getAsJsonObject("data")?.texto(campo)?.takeIf { it.isNotBlank() }
        val duracion = if (esSerie) {
            ficha.getAsJsonArray("episode_run_time")?.firstOrNull()?.asInt
        } else {
            ficha.get("runtime")?.takeIf { !it.isJsonNull }?.asInt
        }
        val titulos = TitulosTmdb(
            es = titulo("ES"),
            mx = titulo("MX"),
            original = ficha.texto(if (esSerie) "original_name" else "original_title"),
            en = ficha.texto(campo),
        )
        val fechas = if (esSerie) emptyList() else fechas(ficha)
        val homonimos = if (esSerie) false else runCatching { homonimos(tmdbId, titulos, ficha.texto("release_date")) }.getOrNull()
        return Datos(
            titulos,
            duracion?.takeIf { it > 0 },
            Anime.es(
                generos = ficha.getAsJsonArray("genres")?.mapNotNull { it.asJsonObject.get("id")?.asInt }?.toSet().orEmpty(),
                idiomaOriginal = ficha.texto("original_language"),
                imdbId = id,
            ),
            // Si la búsqueda no contesta (plazo, 429…), ante la duda hay homónimos: un enlace sin año no se confirma.
            homonimos = homonimos ?: true,
            fechasCasa = fechas.filter { it.first in TIPOS_CASA }.map { it.second },
            // El tipo 1 es el pase en festival: no es estreno en cines y adelantaría la cuenta de 120 días.
            cines = fechas.filter { it.first in TIPOS_CINES }.minOfOrNull { it.second },
            completo = homonimos != null,
        )
    }

    /** (tipo, día) de las fechas de estreno de todos los países. Tipos de TMDB: 1-3 cines, 4 digital, 5 físico, 6 tele. */
    private fun fechas(ficha: JsonObject): List<Pair<Int, String>> =
        ficha.getAsJsonObject("release_dates")?.getAsJsonArray("results").orEmpty().flatMap { pais ->
            pais.asJsonObject.getAsJsonArray("release_dates").orEmpty().mapNotNull { f ->
                val o = f.asJsonObject
                val dia = o.texto("release_date")?.take(10) ?: return@mapNotNull null
                o.get("type")?.asInt?.let { it to dia }
            }
        }

    /** ¿Hay otra película con alguno de sus títulos, de otro año y algo conocida? Una búsqueda por título. */
    private fun homonimos(tmdbId: Int, titulos: TitulosTmdb, estreno: String?): Boolean? {
        val anio = estreno?.take(4)?.toIntOrNull() ?: return false
        val propios = listOfNotNull(titulos.es, titulos.original, titulos.en).map(::comoPalabras).toSet()
        return listOfNotNull(titulos.es, titulos.en).distinct().any { t ->
            val url = "$base/search/movie".toHttpUrl().newBuilder()
                .addQueryParameter("query", t).addQueryParameter("language", "es-ES").addQueryParameter("api_key", apiKey).build()
            (json(url.toString()) ?: return null).getAsJsonArray("results").orEmpty().map { it.asJsonObject }.any { r ->
                val otroAnio = r.texto("release_date")?.take(4)?.toIntOrNull()
                r.get("id")?.asInt != tmdbId && otroAnio != null && kotlin.math.abs(otroAnio - anio) > 1 &&
                    (r.get("vote_count")?.asInt ?: 0) >= VOTOS_HOMONIMO &&
                    (comoPalabras(r.texto("title").orEmpty()) in propios || comoPalabras(r.texto("original_title").orEmpty()) in propios)
            }
        }
    }

    private fun json(url: String): JsonObject? =
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val cuerpo = r.body?.string()
            if (!r.isSuccessful || cuerpo == null) null else JsonParser.parseString(cuerpo).asJsonObject
        }

    private fun JsonObject.texto(campo: String): String? = get(campo)?.takeIf { it.isJsonPrimitive }?.asString

    private fun com.google.gson.JsonArray?.orEmpty(): List<com.google.gson.JsonElement> = this?.toList() ?: emptyList()

    companion object {
        private val TIPOS_CASA = setOf(4, 5, 6)
        private val TIPOS_CINES = setOf(2, 3)

        /** Una película poco conocida con el mismo título no confunde a los addons: hacen falta votos. */
        private const val VOTOS_HOMONIMO = 100

        /** El id que entiende [peticion] a partir del de la ficha: `tt…` sin temporada ni episodio, o `tmdb:N`. */
        fun idDe(contentId: String): String {
            val id = contentId.trim()
            return if (id.startsWith("tmdb:")) "tmdb:" + id.removePrefix("tmdb:").substringBefore(':') else id.substringBefore(':')
        }

        /** Instancia de la app: clave TMDB de compilación y tiempos cortos (no debe retrasar la elección). */
        fun deLaApp(): TitulosTmdbRepositorio = TitulosTmdbRepositorio(
            OkHttpClient.Builder().callTimeout(4, TimeUnit.SECONDS).build(),
            BuildConfig.TMDB_API_KEY,
        )
    }
}

/** ¿Ha salido ya una película en casa (digital, físico o tele en algún país)? */
object EstrenoEnCasa {
    /**
     * Las fichas de TMDB de muchas películas solo traen la fecha de cines (cine español sobre todo): sin ninguna fecha
     * de salida en casa, pasados [DIAS_CINES] días desde el estreno en cines se da por salida. Sin fechas, null.
     */
    const val DIAS_CINES = 120L
    const val DIAS_REEDICION = 730L

    fun yaSalio(casa: List<String>, cines: String?, hoy: LocalDate): Boolean? {
        val dias = casa.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (dias.any { !it.isAfter(hoy) }) return true
        val estreno = cines?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        // Con fecha de salida en casa y aún por llegar, no ha salido (La Odisea: digital el 17-11-2026). Salvo que lleve
        // años en cines: entonces esa fecha es una reedición (un 4K nuevo de una película de 1980).
        if (dias.isNotEmpty()) return estreno != null && !estreno.plusDays(DIAS_REEDICION).isAfter(hoy)
        if (estreno == null) return null
        return !estreno.plusDays(DIAS_CINES).isAfter(hoy)
    }
}

/** Punto de inyección para la app (Hilt). */
@Singleton
class ProveedorTitulosTmdb @Inject constructor() {
    val repositorio: TitulosTmdbRepositorio by lazy { TitulosTmdbRepositorio.deLaApp() }
}
