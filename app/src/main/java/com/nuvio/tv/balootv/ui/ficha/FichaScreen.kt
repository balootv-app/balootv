package com.nuvio.tv.balootv.ui.ficha

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.balootv.ui.componentes.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.balootv.ui.componentes.veloDoble
import com.nuvio.tv.R
import com.nuvio.tv.balootv.ficha.Saga
import com.nuvio.tv.balootv.ficha.TextosFicha
import com.nuvio.tv.balootv.ui.componentes.BotonCristal
import com.nuvio.tv.balootv.ui.componentes.FilaCarteles
import com.nuvio.tv.balootv.ui.componentes.NotaImdbEtiqueta
import com.nuvio.tv.balootv.iconos.Pieza
import com.nuvio.tv.balootv.iconos.Simbolo
import com.nuvio.tv.balootv.iconos.conSeparadores
import com.nuvio.tv.balootv.ui.componentes.LineaPiezas
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.InsigniaPlataforma
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.resolveContentLanguage
import com.nuvio.tv.ui.components.LocalPlaybackAvailability
import com.nuvio.tv.ui.components.LogoPlataforma
import com.nuvio.tv.ui.screens.detail.MetaDetailsEvent
import com.nuvio.tv.ui.screens.detail.MetaDetailsViewModel
import com.nuvio.tv.ui.screens.detail.resolveHeroPlaybackVideo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.onFocusChanged
import com.nuvio.tv.balootv.ui.componentes.FilaNormal
import com.nuvio.tv.balootv.ui.componentes.rememberListaQuieta
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.ui.movimiento.ConRitmo
import com.nuvio.tv.balootv.ui.componentes.FondoFundido
import com.nuvio.tv.balootv.ui.componentes.colorDominante
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.key.onKeyEvent
import com.nuvio.tv.balootv.ui.movimiento.Movimiento
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.balootv.ui.componentes.devolverFoco
import com.nuvio.tv.balootv.ui.componentes.RecordarAlSalir
import com.nuvio.tv.balootv.ui.componentes.rememberHoraAlMinuto
import com.nuvio.tv.ui.navigation.LocalDetailChildClosedEpoch

/** Lo que necesita la pantalla de fuentes para arrancar (mismos datos que la ficha heredada). */
typealias AlReproducir = (
    videoId: String, contentType: String, contentId: String, title: String, poster: String?, backdrop: String?, logo: String?,
    season: Int?, episode: Int?, episodeName: String?, genres: String?, year: String?, runtime: Int?, contentLanguage: String?,
) -> Unit

private const val LOGOS = "https://image.tmdb.org/t/p/w92"
private const val ESPERA_SIGUIENTE_MS = 2_000L

/**
 * Ficha de película y de serie (tableros 18 y 19): fondo a sangre, título, nota IMDb, "Disponible en", sinopsis
 * y los botones; en series, temporadas y episodios; en películas, su saga; al final "Más como esta". Usa los datos
 * de MetaDetailsViewModel.
 */
private val VELO_FICHA = veloDoble(
    arrayOf(0f to Color.Black.copy(alpha = 0.92f), 0.45f to Color.Black.copy(alpha = 0.55f), 0.8f to Color.Transparent),
    arrayOf(0.5f to Color.Transparent, 1f to Color.Black),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FichaScreen(
    /** El título que se ha pedido abrir (para usar su cartel de fondo mientras carga). */
    idPedido: String? = null,
    viewModel: MetaDetailsViewModel = hiltViewModel(),
    extras: FichaExtrasViewModel = hiltViewModel(),
    playOnLoad: Boolean = false,
    playOnLoadManually: Boolean = false,
    /** Con [playOnLoad], el episodio (temporada, número) que pide el enlace del móvil en lugar del que toca. */
    episodioPedido: Pair<Int, Int>? = null,
    onBackPress: () -> Unit,
    onNavigateToDetail: (itemId: String, itemType: String, addonBaseUrl: String?) -> Unit,
    onPlayClick: AlReproducir,
    onPlayManuallyClick: AlReproducir,
    /** Sin ninguna fuente instalada, Reproducir lleva aquí en vez de a una lista vacía. */
    onAnadirFuentes: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler { onBackPress() }
    val meta = uiState.meta
    val previa = remember(idPedido) { idPedido?.let { NavegacionFichas.actual.previa(it) } }
    // Desde una fila, el fondo crece de tarjeta a pantalla completa. Empieza ya mientras carga (con el cartel de la
    // fila de fondo), para que no haya pantalla negra entre medias.
    val entrada = remember { Animatable(if (previa != null) 0f else 1f) }
    LaunchedEffect(Unit) { entrada.animateTo(1f, Movimiento.aperturaFicha) }

    ConRitmo(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val t = entrada.value
                val e = 0.9f + 0.1f * t
                scaleX = e; scaleY = e
                shape = RoundedCornerShape((1f - t) * 28.dp.toPx())
                clip = t < 1f
            }
        ) {
            // Mientras llega la imagen grande, el color del cartel (ya en memoria) en lugar de negro; luego se funde.
            FondoFundido(
                meta?.let { it.backdropUrl ?: it.poster } ?: previa?.let { it.background ?: it.poster },
                Modifier.fillMaxSize(),
                colorEspera = colorDominante(meta?.poster ?: previa?.poster),
            )
            Box(Modifier.fillMaxSize().background(VELO_FICHA))
        }
        if (meta == null) {
            if (uiState.error != null) {
                val contexto = LocalContext.current
                val m = remember { com.nuvio.tv.balootv.errores.MensajesError.ficha(com.nuvio.tv.balootv.errores.Red.hayConexion(contexto)) }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(m.titulo, color = Color.White, fontSize = EscalaTv.LLAMADA, fontWeight = FontWeight.Bold)
                    Text(m.texto, color = Color.White.copy(alpha = 0.7f), fontSize = EscalaTv.CUERPO)
                }
            }
        } else {
            LaunchedEffect(meta.id) { extras.cargar(meta.id, meta.apiType) }
            val estado by extras.estado.collectAsStateWithLifecycle()
            val lineas by extras.lineaElegida.collectAsStateWithLifecycle()
            val motivos by extras.motivos.collectAsStateWithLifecycle()
            val sinFuentes by extras.sinFuentes.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val disponibilidad = LocalPlaybackAvailability.current
            val esSerie = meta.type == ContentType.SERIES || meta.apiType == "series"

            val heroe = remember(meta, uiState.nextToWatch, uiState.episodesForSeason) {
                resolveHeroPlaybackVideo(meta, uiState.nextToWatch, uiState.episodesForSeason)
            }
            val progresoHeroe = if (heroe != null) {
                uiState.episodeProgressMap[(heroe.season ?: 0) to (heroe.episode ?: 0)]
            } else {
                uiState.nextToWatch?.takeIf { it.isResume }?.watchProgress
            }
            // «Termina a las» bajo el botón (contrato C4): la película o el episodio que pondría Reproducir.
            val ahora = rememberHoraAlMinuto()
            val duracionHeroe = if (heroe != null) heroe.runtime else TextosFicha.minutos(meta.runtime)
            val boton = TextosFicha.boton(uiState.nextToWatch, heroe, progresoHeroe, duracionHeroe, ahora)
            val claveFuente = "${meta.apiType}|${heroe?.id ?: meta.id}|${heroe?.season}|${heroe?.episode}"
            val detalle = if (sinFuentes) listOf(Pieza.Texto(TextosFicha.SIN_FUENTES)) else TextosFicha.detalle(boton, lineas[claveFuente], motivos[claveFuente])

            val reproducir: (Video?, Boolean) -> Unit = reproducir@{ video, manual ->
                if (sinFuentes) { onAnadirFuentes(); return@reproducir }
                val videoId = video?.id ?: meta.id
                if (!disponibilidad.canStream(meta.apiType, videoId, meta.id, video)) {
                    Toast.makeText(context, R.string.playback_unavailable_message, Toast.LENGTH_SHORT).show()
                    return@reproducir
                }
                val accion = if (manual) onPlayManuallyClick else onPlayClick
                if (video != null) {
                    accion(video.id, meta.apiType, meta.id, meta.name, video.thumbnail ?: meta.poster, meta.backdropUrl, meta.logo,
                        video.season, video.episode, video.title, null, null, video.runtime, meta.resolveContentLanguage())
                } else {
                    accion(meta.id, meta.apiType, meta.id, meta.name, meta.poster, meta.backdropUrl, meta.logo,
                        null, null, null, meta.genres.joinToString(" • ").ifEmpty { null }, meta.releaseInfo?.trim(), null, meta.resolveContentLanguage())
                }
            }

            // "Reproducir" desde la cabecera de Inicio: en series se espera un momento a saber qué episodio toca.
            var playOnLoadHecho by rememberSaveable(meta.id) { mutableStateOf(false) }
            val heroeActual by rememberUpdatedState(heroe)
            val siguienteActual by rememberUpdatedState(uiState.nextToWatch)
            val estadoActual by rememberUpdatedState(uiState)
            LaunchedEffect(meta.id, playOnLoad) {
                if (!playOnLoad || playOnLoadHecho) return@LaunchedEffect
                // El móvil pide un episodio concreto: ese, y si no está, la ficha abierta en su temporada.
                if (esSerie && episodioPedido != null) {
                    playOnLoadHecho = true
                    val pedido = EpisodioDelEnlace.de(meta.videos, episodioPedido.first, episodioPedido.second)
                    pedido.temporada?.let { viewModel.onEvent(MetaDetailsEvent.OnSeasonSelected(it)) }
                    pedido.video?.let { reproducir(it, playOnLoadManually) }
                    return@LaunchedEffect
                }
                if (esSerie) withTimeoutOrNull(ESPERA_SIGUIENTE_MS) { snapshotFlow { siguienteActual }.first { it != null } }
                playOnLoadHecho = true
                // Serie vista entera: no hay siguiente episodio, se queda en la ficha.
                if (esSerie) {
                    val vistos = estadoActual.watchedEpisodes +
                        estadoActual.episodeProgressMap.filterValues { it.isCompleted() }.keys
                    val episodios = meta.videos.filter { it.available != false }
                        .mapNotNull { v -> v.season?.let { s -> v.episode?.let { e -> s to e } } }
                    if (SerieVista.entera(episodios, vistos)) return@LaunchedEffect
                }
                reproducir(heroeActual, playOnLoadManually)
            }

            // Lo que se pulsó para salir a otra pantalla («Cambiar fuente», un episodio, un cartel de «Más como esta»):
            // al volver, el foco cae ahí, como en tvOS. Si no hay nada, en Reproducir.
            val vuelta = extras.vuelta
            RecordarAlSalir(vuelta)
            // «Más como esta» abre su ficha encima, sin desmontar esta: la vuelta se arma cuando se cierra.
            val cerradas = LocalDetailChildClosedEpoch.current
            LaunchedEffect(cerradas) { if (cerradas > 0) vuelta.salir() }
            val focoReproducir = remember { FocusRequester() }
            LaunchedEffect(meta.id) {
                if (vuelta.activa) {
                    // Si lo pulsado ya no está (el reproductor siguió con episodios de otra temporada), a Reproducir.
                    delay(ESPERA_VUELTA_MS)
                    if (!vuelta.activa) return@LaunchedEffect
                    vuelta.cumplida()
                }
                runCatching { focoReproducir.requestFocus() }
            }

            // El texto aparece con un fundido corto cuando llega la ficha (el fondo ya estaba).
            val aparece = remember { Animatable(0f) }
            LaunchedEffect(Unit) { aparece.animateTo(1f, tween(260)) }

            // La ficha salta por zonas enteras (cabecera; temporadas y episodios; la saga; Más como esta): con el
            // desplazamiento mínimo la sinopsis quedaba cortada arriba al bajar a las temporadas.
            val normal = LocalBringIntoViewSpec.current
            val lista = rememberLazyListState()
            // Guardada para que, al volver de otra pantalla, la lista no suba a la cabecera y vuelva a bajar.
            var zona by rememberSaveable { mutableStateOf(ZonaFicha.CABECERA) }
            val conTemporadas = esSerie && uiState.seasons.isNotEmpty()
            // Solo las películas tienen saga (belongs_to_collection de TMDB).
            val saga = if (esSerie) emptyList() else uiState.collection
            LaunchedEffect(zona) {
                val indice = when (zona) {
                    ZonaFicha.CABECERA -> 0
                    ZonaFicha.TEMPORADAS -> 1
                    ZonaFicha.SAGA -> if (conTemporadas) 2 else 1
                    ZonaFicha.MAS -> lista.layoutInfo.totalItemsCount - 1
                }
                lista.animateScrollToItem(indice.coerceAtLeast(0))
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberListaQuieta()) {
                LazyColumn(Modifier.fillMaxSize(), state = lista, contentPadding = PaddingValues(bottom = 40.dp)) {
                    item("cabecera") {
                        Box(
                            Modifier
                                .onFocusChanged { if (it.hasFocus) zona = ZonaFicha.CABECERA }
                                .graphicsLayer { alpha = aparece.value }
                        ) {
                            FilaNormal(normal) {
                                Cabecera(
                                    meta = meta,
                                    nota = estado.nota,
                                    plataformas = estado.plataformas,
                                    meta2 = TextosFicha.meta(
                                        anio = meta.releaseInfo?.trim()?.take(4),
                                        generos = meta.genres,
                                        temporadas = if (esSerie) uiState.seasons.count { it > 0 } else null,
                                    ),
                                    escena = if (esSerie) null else uiState.escenaCreditos?.texto,
                                    boton = if (sinFuentes) "Añadir fuentes" else boton.titulo,
                                    detalle = detalle,
                                    enLista = uiState.isInWatchlist || uiState.isInLibrary,
                                    conTrailer = uiState.trailerButtonEnabled,
                                    focoReproducir = focoReproducir,
                                    volverAFuente = vuelta.en(FILA_FUENTE) != null,
                                    onVuelto = vuelta::cumplida,
                                    onReproducir = { reproducir(heroe, false) },
                                    onCambiarFuente = { vuelta.abrir(FILA_FUENTE, FILA_FUENTE); reproducir(heroe, true) },
                                    onLista = { viewModel.onEvent(MetaDetailsEvent.OnToggleLibrary) },
                                    onTrailer = { viewModel.onEvent(MetaDetailsEvent.OnTrailerButtonClick) },
                                )
                            }
                        }
                    }
                    if (conTemporadas) {
                        item("temporadas") {
                            Column(Modifier.onFocusChanged { if (it.hasFocus) zona = ZonaFicha.TEMPORADAS }) {
                                FilaNormal(normal) {
                                    val anios = remember(meta.videos) { TextosFicha.aniosTemporada(meta.videos) }
                                    PestanasTemporada(uiState.seasons, uiState.selectedSeason, anios) { viewModel.onEvent(MetaDetailsEvent.OnSeasonSelected(it)) }
                                    FilaEpisodios(
                                        episodios = uiState.episodesForSeason,
                                        vistos = uiState.watchedEpisodes,
                                        progreso = uiState.episodeProgressMap,
                                        idAlVolver = vuelta.en(FILA_EPISODIOS),
                                        onVuelto = vuelta::cumplida,
                                        onReproducir = { vuelta.abrir(FILA_EPISODIOS, it.id); reproducir(it, false) },
                                        onMarcarVisto = { viewModel.onEvent(MetaDetailsEvent.OnToggleEpisodeWatched(it)) },
                                    )
                                }
                            }
                        }
                    }
                    if (saga.isNotEmpty()) {
                        item("saga") {
                            FilaNormal(normal) {
                                FilaCarteles(
                                    titulo = Saga.titulo(uiState.collectionName),
                                    items = saga,
                                    onFoco = {},
                                    onClick = { vuelta.abrir(FILA_SAGA, it.id); onNavigateToDetail(it.id, it.apiType, null) },
                                    conTitulos = true,
                                    idAlVolver = vuelta.en(FILA_SAGA),
                                    onVuelto = vuelta::cumplida,
                                    vista = { Saga.vista(it.id, uiState.relatedWatchedStatus) },
                                    modifier = Modifier.padding(top = 18.dp).onFocusChanged { if (it.hasFocus) zona = ZonaFicha.SAGA },
                                )
                            }
                        }
                    }
                    if (uiState.moreLikeThis.isNotEmpty()) {
                        item("mas") {
                            FilaNormal(normal) {
                                FilaCarteles(
                                    titulo = "Más como esta",
                                    items = uiState.moreLikeThis,
                                    onFoco = {},
                                    onClick = { vuelta.abrir(FILA_MAS, it.id); onNavigateToDetail(it.id, it.apiType, null) },
                                    conTitulos = true,
                                    idAlVolver = vuelta.en(FILA_MAS),
                                    onVuelto = vuelta::cumplida,
                                    modifier = Modifier.padding(top = 18.dp).onFocusChanged { if (it.hasFocus) zona = ZonaFicha.MAS },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class ZonaFicha { CABECERA, TEMPORADAS, SAGA, MAS }

// «Filas» de la ficha para la vuelta del foco.
private const val FILA_FUENTE = "fuente"
private const val FILA_EPISODIOS = "episodios"
private const val FILA_SAGA = "saga"
private const val FILA_MAS = "mas"
private const val ESPERA_VUELTA_MS = 800L

@Composable
private fun Cabecera(
    meta: Meta,
    nota: Float?,
    plataformas: List<com.nuvio.tv.balootv.catalogo.Plataforma>,
    meta2: String,
    /** La escena en los créditos (C6), en palabras: aquí va solo su icono; cuál es lo dice el reproductor. */
    escena: String?,
    boton: String,
    detalle: List<Pieza>,
    enLista: Boolean,
    conTrailer: Boolean,
    focoReproducir: FocusRequester,
    volverAFuente: Boolean,
    onVuelto: () -> Unit,
    onReproducir: () -> Unit,
    onCambiarFuente: () -> Unit,
    onLista: () -> Unit,
    onTrailer: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 430.dp).padding(start = 50.dp, end = 400.dp, top = 70.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (meta.logo != null) {
            AsyncImage(
                model = meta.logo, contentDescription = meta.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                modifier = Modifier.height(80.dp).widthIn(max = 320.dp),
            )
        } else {
            Text(meta.name, fontSize = EscalaTv.TITULO_1, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // Alto fijo: la nota IMDb llega después y no debe empujar lo de abajo.
        Row(Modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            nota?.let { NotaImdbEtiqueta(it) }
            LineaPiezas(
                conSeparadores(
                    listOfNotNull(meta2.takeIf { it.isNotEmpty() }?.let(Pieza::Texto)),
                    listOfNotNull(escena?.let { Pieza.Icono(Simbolo.ESCENA, it) }),
                ),
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        // Hueco reservado: las plataformas llegan después (TMDB) y, si se insertaban, empujaban sinopsis y botones.
        // Solo los logos, sin «Disponible en» ni los nombres (propuesta de iconos, T3), a 22 dp para leerse a 3 m.
        val alfaPlataformas by animateFloatAsState(if (plataformas.isNotEmpty()) 1f else 0f, tween(300), label = "plataformas")
        Row(
            Modifier.height(22.dp).graphicsLayer { alpha = alfaPlataformas },
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            plataformas.forEach { p -> LogoPlataforma(InsigniaPlataforma(p.nombre, LOGOS + p.logo), Modifier.size(22.dp)) }
        }
        meta.description?.let { Sinopsis(it, meta.name, meta.backdropUrl ?: meta.poster, Modifier.width(430.dp)) }
        Spacer(Modifier.height(4.dp))
        // Los botones pueden pasar del margen derecho del texto: "Continuar" con su detalle aplastaba el Tráiler.
        Row(
            Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotonCristal(boton, onReproducir, Modifier.focusRequester(focoReproducir), piezas = detalle, principal = true,
                relleno = PaddingValues(horizontal = 20.dp, vertical = 9.dp))
            BotonCristal("Cambiar fuente", onCambiarFuente, Modifier.devolverFoco(volverAFuente, onVuelto))
            BotonCristal(if (enLista) "En mi lista" else "Mi lista", onLista, icono = if (enLista) Simbolo.APUNTADA else Simbolo.APUNTAR)
            if (conTrailer) BotonCristal("Tráiler", onTrailer)
        }
    }
}
