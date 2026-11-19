package com.nuvio.tv.balootv.nota

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nuvio.tv.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** De dónde sale la nota de un título; en las pruebas, una falsa. */
interface FuenteNota {
    suspend fun de(id: String, tipo: String): Float?
}

/**
 * Nota de IMDb de un título (F4 §2): la única que enseña BalooTV, y solo del título enfocado y en la ficha.
 * Para ids `tmdb:` se pide antes el id de IMDb a TMDB; la nota sale de Cinemeta. Se recuerda en memoria
 * (también la ausencia de nota) y en la caché HTTP. Cualquier fallo es "sin nota".
 */
class NotaImdb(
    private val http: OkHttpClient,
    private val tmdbApiKey: String,
    private val baseTmdb: String = "https://api.themoviedb.org/3",
    private val baseCinemeta: String = "https://v3-cinemeta.strem.io",
) : FuenteNota {
    private val SIN_NOTA = -1f
    private val memoria = ConcurrentHashMap<String, Float>()

    override suspend fun de(id: String, tipo: String): Float? {
        val clave = "$tipo|$id"
        memoria[clave]?.let { return it.takeIf { n -> n != SIN_NOTA } }
        val nota = withContext(Dispatchers.IO) { runCatching { buscar(id, tipo) }.getOrNull() }
        memoria[clave] = nota ?: SIN_NOTA
        return nota
    }

    private fun buscar(id: String, tipo: String): Float? {
        val serie = tipo.equals("series", ignoreCase = true) || tipo.equals("tv", ignoreCase = true)
        val imdb = when {
            id.startsWith("tt") -> id
            id.startsWith("tmdb:") -> {
                val tmdbId = id.removePrefix("tmdb:").substringBefore(':')
                json("$baseTmdb/${if (serie) "tv" else "movie"}/$tmdbId/external_ids?api_key=$tmdbApiKey")
                    ?.texto("imdb_id")?.takeIf { it.startsWith("tt") }
            }
            else -> null
        } ?: return null
        return json("$baseCinemeta/meta/${if (serie) "series" else "movie"}/$imdb.json")
            ?.getAsJsonObject("meta")?.texto("imdbRating")?.replace(',', '.')?.toFloatOrNull()?.takeIf { it > 0f }
    }

    private fun json(url: String): JsonObject? =
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val cuerpo = r.body?.string()
            if (!r.isSuccessful || cuerpo == null) null else JsonParser.parseString(cuerpo).asJsonObject
        }

    private fun JsonObject.texto(campo: String): String? = get(campo)?.takeIf { it.isJsonPrimitive }?.asString
}

@Module
@InstallIn(SingletonComponent::class)
object ModuloNotaImdb {
    @Provides
    @Singleton
    fun nota(@ApplicationContext context: Context): NotaImdb {
        val http = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "balootv_imdb"), 5L * 1024 * 1024))
            .addNetworkInterceptor { cadena ->
                cadena.proceed(cadena.request()).newBuilder()
                    .removeHeader("Pragma")
                    .header("Cache-Control", "public, max-age=${7 * 24 * 3600}")
                    .build()
            }
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
        return NotaImdb(http, BuildConfig.TMDB_API_KEY)
    }

    @Provides
    fun fuente(nota: NotaImdb): FuenteNota = nota
}
