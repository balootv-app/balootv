package com.nuvio.tv.balootv.catalogo

import android.content.Context
import com.nuvio.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Lo que el catálogo necesita de TMDB; en las pruebas se sustituye por uno falso. */
interface FuenteTmdb {
    suspend fun descubrir(tipo: TipoFila, parametros: Map<String, String>): List<Titulo>?
    suspend fun buscar(tipo: TipoFila, texto: String, pagina: Int): List<Titulo>?
}

/**
 * Pide a TMDB las filas (`discover`) y las búsquedas (`search`) del catálogo. Devuelve null si TMDB falla:
 * quien llama decide qué hacer con una fila que no ha llegado. Como mucho 4 consultas a la vez, para no
 * ahogar la CPU de la tele mientras el Inicio se dibuja.
 */
class ClienteTmdbCatalogo(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val base: String = "https://api.themoviedb.org/3",
) : FuenteTmdb {
    private val turnos = Semaphore(4)

    override suspend fun descubrir(tipo: TipoFila, parametros: Map<String, String>): List<Titulo>? =
        pedir("discover/${ruta(tipo)}", parametros, tipo)

    override suspend fun buscar(tipo: TipoFila, texto: String, pagina: Int): List<Titulo>? {
        if (texto.isBlank()) return emptyList()
        return pedir("search/${ruta(tipo)}", mapOf("query" to texto.trim(), "language" to "es-ES", "page" to pagina.toString()), tipo)
    }

    private fun ruta(tipo: TipoFila) = if (tipo == TipoFila.SERIE) "tv" else "movie"

    private suspend fun pedir(camino: String, parametros: Map<String, String>, tipo: TipoFila): List<Titulo>? =
        turnos.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    val url = "$base/$camino".toHttpUrl().newBuilder().apply {
                        parametros.forEach { (k, v) -> addQueryParameter(k, v) }
                        addQueryParameter("api_key", apiKey)
                    }.build()
                    http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                        val cuerpo = r.body?.string()
                        if (!r.isSuccessful || cuerpo == null) null else TmdbCatalogoJson.titulos(cuerpo, tipo)
                    }
                }.getOrNull()
            }
        }

    companion object {
        private const val HORAS_CACHE = 6

        /**
         * Cliente de la app: caché propia en disco de 20 MB y respuestas guardadas 6 h aunque TMDB diga
         * otra cosa, para que volver a abrir la app el mismo día no repita consultas.
         */
        fun deLaApp(context: Context): ClienteTmdbCatalogo {
            val http = OkHttpClient.Builder()
                .cache(Cache(File(context.cacheDir, "balootv_tmdb"), 20L * 1024 * 1024))
                .addNetworkInterceptor { cadena ->
                    cadena.proceed(cadena.request()).newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public, max-age=${HORAS_CACHE * 3600}")
                        .build()
                }
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
            return ClienteTmdbCatalogo(http, BuildConfig.TMDB_API_KEY)
        }
    }
}
