package com.nuvio.tv.balootv.inicio

import android.content.Context
import android.util.Log
import com.nuvio.tv.balootv.episodios.NumeracionTmdb
import com.nuvio.tv.balootv.catalogo.TituloEspanol
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.data.remote.api.TraktApi
import com.nuvio.tv.data.remote.dto.trakt.TraktMasVistoDto
import com.nuvio.tv.data.repository.TraktAuthService
import com.nuvio.tv.domain.repository.MetaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Las filas de ideas del Inicio, las mismas peticiones que `RepositorioIdeas` del móvil: lo visto (`sync/watched`), las
 * recomendaciones de Trakt, lo más visto de la semana y del mes, «Porque viste» (`related` de las semillas del día) y las
 * series que siguen en emisión con su progreso y sus estrenos de TMDB. Se rehace una vez al día de Madrid y se guarda en
 * disco: al abrir, se dibuja lo guardado y, si es de ayer, se pide de nuevo detrás.
 */
@Singleton
class RepositorioInicio @Inject constructor(
    @ApplicationContext context: Context,
    private val trakt: TraktApi,
    private val auth: TraktAuthService,
    private val metas: MetaRepository,
) {
    private val fichas: FuenteFichas = ClienteTmdbInicio.deLaApp(context)
    private val fichero = File(context.filesDir, "balootv_inicio.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val cerrojo = Mutex()

    private val _guardado = MutableStateFlow<InicioGuardado?>(null)
    val guardado: StateFlow<InicioGuardado?> = _guardado.asStateFlow()

    /**
     * Lee lo guardado y, si no hay, es de otro día de Madrid o de otra cuenta de Trakt (se acaba de vincular o de
     * desconectar), lo rehace. Si Trakt falla, se queda lo que había.
     */
    suspend fun asegurar() = cerrojo.withLock {
        if (_guardado.value == null) _guardado.value = leer()?.takeIf { it.version == InicioGuardado.VERSION }?.also(::resumir)
        val cuenta = auth.getCurrentAuthState().takeIf { it.isAuthenticated }?.let { it.userSlug ?: it.username.orEmpty() }
        val antes = _guardado.value?.takeIf { it.cuenta == cuenta }?.refrescadaEn?.let(Instant::ofEpochMilli)
        if (!Ideas.hayQueRefrescar(Instant.now(), antes)) return@withLock
        val nuevo = runCatching { rehacer(cuenta) }.onFailure { Log.w(TAG, "No se pudo rehacer el Inicio", it) }.getOrNull()
            ?: return@withLock
        _guardado.value = nuevo
        resumir(nuevo)
        escribir(nuevo)
    }

    /** En el registro, de qué salen las filas: para saber, sin abrir la app, por qué falta una. */
    private fun resumir(g: InicioGuardado) {
        val hoy = LocalDate.now(MADRID)
        val e = g.entrada
        val siguientes = g.series.mapNotNull { s ->
            val t = s.ultimaTemporadaVista + 1
            s.estrenos[t]?.let { "${s.titulo.titulo} T$t $it" }
        }
        Log.i(TAG, "Inicio del ${Instant.ofEpochMilli(g.refrescadaEn).atZone(MADRID).toLocalDate()}: para ti ${e.paraTi.size}, " +
            "porque viste ${e.porqueViste.map { it.titulo.titulo to it.relacionados.size }}, semana ${e.estaSemana.size}, " +
            "mes ${e.esteMes.size}, vistos ${e.vistos.size}; ${g.series.size} series en emisión, siguiente temporada con fecha: " +
            "$siguientes; temporada nueva hoy: ${TemporadaNueva.calcular(g.series, hoy).map { it.titulo.titulo }}")
    }

    /** Sin [cuenta] (Trakt sin vincular) no hay vistos ni recomendaciones: solo lo más visto, que es público. */
    private suspend fun rehacer(cuenta: String?): InicioGuardado? = coroutineScope {
        val hoy = LocalDate.now(MADRID)
        val peliculasVistas = async {
            if (cuenta == null) emptyList() else paginas { a, p -> trakt.getWatched(a, "movies", page = p, limit = POR_PAGINA) }
        }
        val seriesVistas = async {
            if (cuenta == null) emptyList() else paginas { a, p -> trakt.getWatchedShows(a, "full,noseasons", page = p, limit = POR_PAGINA) }
        }
        val recomendadasPeliculas = async {
            if (cuenta == null) emptyList() else conCuenta { trakt.getRecommendedMovies(it) }.orEmpty().mapNotNull(DeTrakt::pelicula)
        }
        val recomendadasSeries = async {
            if (cuenta == null) emptyList() else conCuenta { trakt.getRecommendedShows(it) }.orEmpty().mapNotNull(DeTrakt::serie)
        }
        val semana = async { masVisto("weekly") }
        val mes = async { masVisto("monthly") }

        val pv = peliculasVistas.await() ?: return@coroutineScope null
        val sv = seriesVistas.await() ?: return@coroutineScope null
        val vistasP = pv.mapNotNull { v -> DeTrakt.pelicula(v.movie)?.let { t -> DeTrakt.instante(v.lastWatchedAt)?.let { VistoFechado(t, it) } } }
        val vistasS = sv.mapNotNull { v -> DeTrakt.serie(v.show)?.let { t -> DeTrakt.instante(v.lastWatchedAt)?.let { VistoFechado(t, it) } } }
        val porqueViste = Semillas.elegir(vistasP, vistasS, hoy).map { s -> async { Semilla(s, relacionados(s)) } }
        val estrenos = sv.filter { DeTrakt.puedeEstrenar(it.show) }.mapNotNull { DeTrakt.serie(it.show) }
            .map { s -> async { estrenosDe(s) } }

        val minima = EntradaIdeas(
            paraTi = Ideas.ordenDelDia(recomendadasPeliculas.await() + recomendadasSeries.await(), hoy),
            porqueViste = porqueViste.awaitAll(),
            estaSemana = semana.await(),
            esteMes = mes.await(),
            vistos = (vistasP + vistasS).map { Marca(it.titulo.tipo, it.titulo.ids) },
            enLista = emptyList(),
        )
        val conFicha = estrenos.awaitAll().filterNotNull()
        val series = conFicha.map { it.first }
        val entrada = conFichas(minima)
        val enLaFicha = TemporadaNueva.calcular(series, hoy).mapNotNull { e ->
            val recuento = conFicha.firstOrNull { it.first.titulo.clave == e.titulo.clave }?.second ?: return@mapNotNull null
            dondeEmpieza(e, recuento)?.let { InicioGuardado.claveTemporada(e.titulo, e.temporada) to it }
        }.toMap()
        InicioGuardado(System.currentTimeMillis(), InicioGuardado.VERSION, entrada, series, enLaFicha, cuenta)
    }

    /**
     * Pone el nombre en castellano y las imágenes de TMDB solo a lo que puede salir: lo que entra en las filas con
     * [MARGEN] de más por si Mi lista o Seguir viendo quitan algo, y los títulos de «Porque viste».
     */
    private suspend fun conFichas(e: EntradaIdeas): EntradaIdeas = coroutineScope {
        val visibles = Ideas.componer(e, FiltroIdeas.Todo, Ideas.MAXIMO_POR_FILA + MARGEN).flatMap { it.titulos } +
            e.porqueViste.map { it.titulo }
        val resueltos = visibles.distinctBy { it.clave }.map { t -> async { t.clave to conFicha(t) } }.awaitAll().toMap()
        val r: (Titulo) -> Titulo = { resueltos[it.clave] ?: it }
        e.copy(
            paraTi = e.paraTi.map(r),
            porqueViste = e.porqueViste.map { Semilla(r(it.titulo), it.relacionados.map(r)) },
            estaSemana = e.estaSemana.map(r),
            esteMes = e.esteMes.map(r),
        )
    }

    private suspend fun conFicha(t: Titulo): Titulo {
        val tmdb = t.ids.tmdb ?: return t
        val ficha = fichas.ficha(t.tipo, tmdb) ?: return t
        return deFicha(t, ficha)
    }

    /** El título de TMDB con los ids de Trakt (TMDB no los da) y, si TMDB lo deja en japonés, el nombre de Trakt. */
    private fun deFicha(t: Titulo, ficha: FichaTmdb): Titulo {
        val nombre = ficha.titulo.titulo.ifBlank { t.titulo }
        return ficha.titulo.copy(ids = t.ids.copy(tmdb = ficha.titulo.ids.tmdb), titulo = TituloEspanol.elegir(nombre, t.titulo))
    }

    /** La serie con sus estrenos de TMDB y, aparte, cuántos episodios tiene cada temporada allí. */
    private suspend fun estrenosDe(s: Titulo): Pair<SerieEstrenos, Map<Int, Int>>? {
        val progreso = conCuenta { trakt.getShowProgressWatched(it, s.ids.trakt.toString()) }
        val ficha = s.ids.tmdb?.let { fichas.ficha(Tipo.SERIE, it) } ?: return null
        return SerieEstrenos(deFicha(s, ficha), DeTrakt.ultimaTemporadaVista(progreso), ficha.estrenos) to ficha.episodiosPorTemporada
    }

    /**
     * Dónde empieza en la ficha (Cinemeta) la temporada nueva [e], para decir «Temporada S» como la ficha y abrirla
     * ahí. Si Cinemeta no la tiene aún o no responde, null: se usa la de TMDB.
     */
    private suspend fun dondeEmpieza(e: Estreno, recuento: Map<Int, Int>): EpisodioFicha? {
        val imdb = e.titulo.ids.imdb ?: return null
        val meta = withTimeoutOrNull(10_000) {
            metas.getMetaFromAllAddons("series", imdb).first { it !is NetworkResult.Loading }
        } as? NetworkResult.Success ?: return null
        val episodios = meta.data.videos.mapNotNull { v -> v.season?.let { s -> v.episode?.let { s to it } } }
        return NumeracionTmdb.deLaFicha(episodios, recuento.filterValues { it > 0 }, e.temporada to 1)
            ?.let { EpisodioFicha(it.first, it.second) }
    }

    private suspend fun relacionados(s: Titulo): List<Titulo> {
        val id = s.ids.trakt?.toString() ?: return emptyList()
        return when (s.tipo) {
            Tipo.PELICULA -> conCuenta { trakt.getMovieRelated(it, id, extended = "min") }.orEmpty().mapNotNull(DeTrakt::pelicula)
            Tipo.SERIE -> conCuenta { trakt.getShowRelated(it, id, extended = "min") }.orEmpty().mapNotNull(DeTrakt::serie)
        }
    }

    /** Lo más visto de [periodo] (`weekly` o `monthly`): películas y luego series, como en el móvil. */
    private suspend fun masVisto(periodo: String): List<Titulo> = coroutineScope {
        val p = async { publica { trakt.getMostWatched("movies", periodo) }.orEmpty().mapNotNull { m: TraktMasVistoDto -> DeTrakt.pelicula(m.movie) } }
        val s = async { publica { trakt.getMostWatched("shows", periodo) }.orEmpty().mapNotNull { m: TraktMasVistoDto -> DeTrakt.serie(m.show) } }
        p.await() + s.await()
    }

    private suspend fun <T> conCuenta(llamada: suspend (String) -> Response<T>): T? =
        auth.executeAuthorizedRequest(llamada)?.takeIf { it.isSuccessful }?.body()

    private suspend fun <T> publica(llamada: suspend () -> Response<T>): T? =
        auth.executePublicRequest(llamada)?.takeIf { it.isSuccessful }?.body()

    /** Todas las páginas de una lista de `sync/watched`; null si alguna falla (sin lo visto, las filas no valen). */
    private suspend fun <T> paginas(llamada: suspend (String, Int) -> Response<List<T>>): List<T>? {
        val todo = mutableListOf<T>()
        for (pagina in 1..MAXIMO_PAGINAS) {
            val r = auth.executeAuthorizedRequest { llamada(it, pagina) }?.takeIf { it.isSuccessful } ?: return null
            val items = r.body().orEmpty()
            todo += items
            val total = r.headers()["X-Pagination-Page-Count"]?.toIntOrNull()
            if (items.isEmpty() || total == null || pagina >= total) return todo
        }
        return todo
    }

    private suspend fun leer(): InicioGuardado? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(InicioGuardado.serializer(), fichero.readText()) }.getOrNull()
    }

    private suspend fun escribir(g: InicioGuardado) = withContext(Dispatchers.IO) {
        runCatching {
            val temporal = File(fichero.parentFile, "${fichero.name}.tmp")
            temporal.writeText(json.encodeToString(InicioGuardado.serializer(), g))
            temporal.renameTo(fichero)
        }.onFailure { Log.w(TAG, "No se pudo guardar el Inicio", it) }
    }

    private companion object {
        const val TAG = "BalooInicio"
        const val POR_PAGINA = 250
        const val MAXIMO_PAGINAS = 20
        const val MARGEN = 6
        val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
    }
}
