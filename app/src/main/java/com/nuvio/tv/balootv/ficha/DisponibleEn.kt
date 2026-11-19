package com.nuvio.tv.balootv.ficha

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.balootv.catalogo.ConfigCatalogo
import com.nuvio.tv.balootv.catalogo.Plataforma
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * "Disponible en" de la ficha (tablero 18): en qué plataformas de España está el título, de las que usa el
 * catálogo y en su orden. Sin datos de España o con error, ninguna (la fila no se pinta).
 */
class DisponibleEn(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val base: String = "https://api.themoviedb.org/3",
) {
    private val memoria = ConcurrentHashMap<String, List<Plataforma>>()

    suspend fun de(id: String, esSerie: Boolean): List<Plataforma> {
        val clave = "$esSerie|$id"
        memoria[clave]?.let { return it }
        val lista = withContext(Dispatchers.IO) { runCatching { buscar(id, esSerie) }.getOrNull() }.orEmpty()
        memoria[clave] = lista
        return lista
    }

    private fun buscar(id: String, esSerie: Boolean): List<Plataforma> {
        val tipo = if (esSerie) "tv" else "movie"
        val tmdbId = when {
            id.startsWith("tmdb:") -> id.removePrefix("tmdb:").substringBefore(':')
            id.startsWith("tt") -> json("$base/find/${id.substringBefore(':')}?external_source=imdb_id&api_key=$apiKey")
                ?.getAsJsonArray(if (esSerie) "tv_results" else "movie_results")
                ?.firstOrNull()?.asJsonObject?.get("id")?.asString
            else -> null
        } ?: return emptyList()
        val ids = json("$base/$tipo/$tmdbId/watch/providers?api_key=$apiKey")
            ?.getAsJsonObject("results")?.getAsJsonObject("ES")?.getAsJsonArray("flatrate")
            ?.mapNotNull { it.asJsonObject.get("provider_id")?.asInt }
            ?.toSet()
            .orEmpty()
        return ConfigCatalogo.plataformas.filter { it.id in ids }
    }

    private fun json(url: String): JsonObject? =
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val cuerpo = r.body?.string()
            if (!r.isSuccessful || cuerpo == null) null else JsonParser.parseString(cuerpo).asJsonObject
        }
}

@Module
@InstallIn(SingletonComponent::class)
object ModuloDisponibleEn {
    @Provides
    @Singleton
    fun disponibleEn(@ApplicationContext context: Context): DisponibleEn = DisponibleEn(
        OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "balootv_disponible"), 2L * 1024 * 1024))
            .callTimeout(10, TimeUnit.SECONDS)
            .build(),
        BuildConfig.TMDB_API_KEY,
    )
}
