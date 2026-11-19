package com.nuvio.tv.balootv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.nuvio.tv.balootv.ui.componentes.Text
import com.nuvio.tv.balootv.ui.acciones.ObjetivoAccion
import com.nuvio.tv.balootv.ui.acciones.rememberMenuTitulo
import com.nuvio.tv.balootv.ui.componentes.Cartel
import com.nuvio.tv.balootv.ui.componentes.FilaCarteles
import com.nuvio.tv.balootv.ui.componentes.FilaNormal
import com.nuvio.tv.balootv.ui.componentes.FilaRejilla
import com.nuvio.tv.balootv.ui.componentes.huecoFinal
import com.nuvio.tv.balootv.ui.componentes.rememberFilasArriba
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.LibraryListTab
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import com.nuvio.tv.ui.screens.home.isFullyWatchedAgainstAiredList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.ui.componentes.VueltaFoco
import com.nuvio.tv.balootv.ui.componentes.devolverFoco
import com.nuvio.tv.balootv.ui.componentes.RecordarAlSalir
import com.nuvio.tv.balootv.ui.movimiento.ConRitmo
import com.nuvio.tv.balootv.ui.ficha.NavegacionFichas
import com.nuvio.tv.balootv.ui.componentes.ELEGIDO

enum class FiltroMiLista(val titulo: String) { TODO("Todo"), PELICULAS("Películas"), SERIES("Series"), ANIME("Anime") }

/** Las dos filas de Mi lista: lo que te han recomendado y lo que has apuntado tú. */
data class FilasMiLista(val recomendadas: List<LibraryEntry> = emptyList(), val lista: List<LibraryEntry> = emptyList()) {
    val total: Int get() = recomendadas.size + lista.size
}

/** Lo que no sale en Recomendadas, por cualquiera de sus ids (`tt…`, `tmdb:…`, `trakt:…`): películas vistas y series terminadas. */
data class Visto(val peliculas: Set<String> = emptySet(), val series: Set<String> = emptySet())

/**
 * Mi lista (F4 §1): arriba la lista personal «Recomendadas» de Trakt, que llena el móvil (sin el «quién»: contrato con
 * el móvil del 31-10-2026), por cuándo entró en ella y sin lo ya visto; debajo, la lista de seguimiento sin lo que esté
 * en Recomendadas, lo último añadido primero.
 */
object MiLista {
    private val ANIMACION = setOf("animación", "animation", "anime")
    private const val RECOMENDADAS = "Recomendadas"

    fun de(entradas: List<LibraryEntry>, pestanas: List<LibraryListTab>, filtro: FiltroMiLista, visto: Visto = Visto()): FilasMiLista {
        val seguimiento = pestanas.firstOrNull { it.type == LibraryListTab.Type.WATCHLIST }?.key ?: return FilasMiLista()
        val recomendadas = recomendadas(pestanas)?.key
        val filtradas = entradas.filter { pasa(it, filtro) }
        return FilasMiLista(
            recomendadas = if (recomendadas == null) emptyList() else filtradas
                .filter { recomendadas in it.listKeys && !yaVisto(it, visto) }
                .sortedByDescending { it.fechaEnLista[recomendadas] ?: it.listedAt },
            lista = filtradas
                .filter { seguimiento in it.listKeys && (recomendadas == null || recomendadas !in it.listKeys) }
                .sortedByDescending { it.fechaEnLista[seguimiento] ?: it.listedAt },
        )
    }

    /** La lista «Recomendadas»: por nombre, sin mayúsculas ni espacios, y la primera en el orden de Trakt (como el móvil). */
    fun recomendadas(pestanas: List<LibraryListTab>): LibraryListTab? =
        pestanas.firstOrNull { it.type == LibraryListTab.Type.PERSONAL && it.title.trim().equals(RECOMENDADAS, ignoreCase = true) }

    /** Los ids con los que Trakt puede nombrar el título. */
    fun claves(e: LibraryEntry): Set<String> = setOfNotNull(e.id, e.imdbId, e.tmdbId?.let { "tmdb:$it" }, e.traktId?.let { "trakt:$it" })

    private fun yaVisto(e: LibraryEntry, visto: Visto): Boolean {
        val vistos = if (e.type == "movie") visto.peliculas else visto.series
        return claves(e).any(vistos::contains)
    }

    private fun pasa(e: LibraryEntry, filtro: FiltroMiLista): Boolean {
        val anime = esAnime(e)
        return when (filtro) {
            FiltroMiLista.TODO -> true
            FiltroMiLista.PELICULAS -> e.type == "movie" && !anime
            FiltroMiLista.SERIES -> e.type == "series" && !anime
            FiltroMiLista.ANIME -> anime
        }
    }

    /** Sin idioma original en la biblioteca: anime = serie de animación (lo habitual en lo que se ve aquí). */
    private fun esAnime(e: LibraryEntry) = e.type == "series" && e.genres.any { it.lowercase() in ANIMACION }
}

data class EstadoMiLista(val filas: FilasMiLista = FilasMiLista(), val filtro: FiltroMiLista = FiltroMiLista.TODO, val trakt: Boolean = true)

@HiltViewModel
class MiListaViewModel @Inject constructor(
    biblioteca: LibraryRepository,
    trakt: TraktAuthDataStore,
    private val progreso: WatchProgressRepository,
) : ViewModel() {
    private val filtro = MutableStateFlow(FiltroMiLista.TODO)
    private val seriesTerminadas = MutableStateFlow<Set<String>>(emptySet())

    /** El cartel abierto, para devolverle el foco al volver de la ficha. */
    val vuelta = VueltaFoco()

    private val visto = combine(progreso.observeWatchedMovieIds(), seriesTerminadas) { p, s -> Visto(p, s) }

    val estado: StateFlow<EstadoMiLista> = combine(biblioteca.libraryItems, biblioteca.listTabs, filtro, trakt.isAuthenticated, visto) { e, p, f, t, v ->
        EstadoMiLista(MiLista.de(e, p, f, v), f, t)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoMiLista())

    init {
        // Series recomendadas terminadas: solo se pregunta a Trakt por los episodios emitidos de las que ya tienen algo
        // visto (una petición por serie, en caché), que en Recomendadas son pocas.
        viewModelScope.launch {
            combine(biblioteca.libraryItems, biblioteca.listTabs) { e, p ->
                val clave = MiLista.recomendadas(p)?.key ?: return@combine emptyList()
                e.filter { it.type == "series" && clave in it.listKeys }
            }.distinctUntilChanged().collectLatest { series ->
                if (series.isEmpty()) return@collectLatest
                val vistos = runCatching { progreso.getWatchedShowEpisodes() }.getOrDefault(emptyMap())
                val terminadas = coroutineScope {
                    series.map { serie ->
                        async {
                            val suyos = MiLista.claves(serie).firstNotNullOfOrNull(vistos::get) ?: return@async null
                            val emitidos = withTimeoutOrNull(15_000) { progreso.getAiredEpisodeOrder(serie.id).first { it.isNotEmpty() } }
                            serie.takeIf { emitidos != null && isFullyWatchedAgainstAiredList(emitidos.toSet(), suyos) }
                        }
                    }.awaitAll().filterNotNull()
                }
                seriesTerminadas.value = terminadas.flatMap(MiLista::claves).toSet()
            }
        }
    }

    fun filtrar(f: FiltroMiLista) { filtro.value = f }
}

private val CAPSULA = RoundedCornerShape(13.dp)

/** Mi lista (tablero 16): barra, filtros, la fila Recomendadas y la rejilla de carteles. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiListaScreen(onIr: (String) -> Unit, onAbrir: (MetaPreview) -> Unit, viewModel: MiListaViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsState()
    val abrirMenu = rememberMenuTitulo()
    val focoBarra = remember { FocusRequester() }
    RecordarAlSalir(viewModel.vuelta)
    LaunchedEffect(Unit) { if (!viewModel.vuelta.activa) runCatching { focoBarra.requestFocus() } }

    ConRitmo(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BarraPestanas(Pestana.MI_LISTA, onIr, focoBarra)
            Row(Modifier.fillMaxWidth().padding(horizontal = 50.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FiltroMiLista.entries.forEach { f ->
                    val activo = f == estado.filtro
                    Surface(
                        onClick = { viewModel.filtrar(f) },
                        shape = ClickableSurfaceDefaults.shape(CAPSULA),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (activo) ELEGIDO else Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White,
                            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    ) {
                        Text(f.titulo, Modifier.padding(horizontal = 11.dp, vertical = 5.5.dp), fontSize = EscalaTv.PIE_2,
                            fontWeight = if (activo) FontWeight.Bold else FontWeight.SemiBold)
                    }
                }
                Box(Modifier.weight(1f))
                if (estado.trakt) Text("Tu lista de Trakt · ${estado.filas.total} títulos", fontSize = EscalaTv.PIE_2, color = Color.White.copy(alpha = 0.55f))
            }
            when {
                !estado.trakt -> Aviso("Conecta Trakt en Ajustes › Trakt para ver aquí tu lista.")
                estado.filas.total == 0 -> Aviso("Tu lista está vacía. Añade títulos desde su ficha con «+».")
                // Como las filas del Inicio: la enfocada sube con su título y la de encima desaparece entera (antes, al
                // bajar por la rejilla, Recomendadas quedaba cortada bajo los filtros).
                else -> {
                    val normal = LocalBringIntoViewSpec.current
                    val filasLista = remember(estado.filas.lista) { estado.filas.lista.chunked(COLUMNAS) }
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberFilasArriba()) {
                        LazyColumn(contentPadding = PaddingValues(top = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (estado.filas.recomendadas.isNotEmpty()) item(key = FILA_RECOMENDADAS) {
                                val vistas = remember(estado.filas.recomendadas) { estado.filas.recomendadas.map { it.comoVista() } }
                                FilaNormal(normal) {
                                    FilaCarteles(
                                        "Recomendadas", vistas, onFoco = {},
                                        onClick = { viewModel.vuelta.abrir(FILA_RECOMENDADAS, it.id); onAbrir(it) },
                                        idAlVolver = viewModel.vuelta.en(FILA_RECOMENDADAS), onVuelto = viewModel.vuelta::cumplida,
                                        onMantener = { abrirMenu(ObjetivoAccion.de(it)) }, conTitulos = true,
                                    )
                                }
                            }
                            itemsIndexed(filasLista, key = { _, fila -> fila.joinToString { "${it.type}:${it.id}" } }) { i, fila ->
                                val titulo = "Mi lista".takeIf { i == 0 && estado.filas.recomendadas.isNotEmpty() }
                                FilaRejilla(titulo, fila, COLUMNAS) { e ->
                                    val item = remember(e) { e.comoVista() }
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Cartel(
                                            item, onFoco = {}, onClick = {
                                                viewModel.vuelta.abrir(FILA_LISTA, it.id)
                                                NavegacionFichas.actual.abrir(it)
                                                onAbrir(it)
                                            },
                                            modifier = Modifier.devolverFoco(viewModel.vuelta.en(FILA_LISTA) == item.id, viewModel.vuelta::cumplida),
                                            onMantener = { abrirMenu(ObjetivoAccion.de(it)) },
                                        )
                                        Text(e.name, fontSize = EscalaTv.PIE_2, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                                    }
                                }
                            }
                            huecoFinal()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(texto, Modifier.fillMaxWidth().padding(top = 80.dp), color = Color.White.copy(alpha = 0.6f), fontSize = EscalaTv.CUERPO, textAlign = TextAlign.Center)
}

private fun LibraryEntry.comoVista() = MetaPreview(
    id = id, type = ContentType.fromString(type), rawType = type, name = name, poster = poster,
    posterShape = posterShape, background = background, logo = logo, description = description,
    releaseInfo = releaseInfo, imdbRating = null, genres = genres,
)

private const val COLUMNAS = 7
private const val FILA_LISTA = "lista"
private const val FILA_RECOMENDADAS = "recomendadas"
