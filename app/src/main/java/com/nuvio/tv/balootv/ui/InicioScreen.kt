package com.nuvio.tv.balootv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.nuvio.tv.balootv.ui.componentes.Text
import com.nuvio.tv.balootv.ui.acciones.ObjetivoAccion
import com.nuvio.tv.balootv.ui.acciones.rememberMenuTitulo
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import coil3.compose.AsyncImage
import com.nuvio.tv.balootv.ui.componentes.FilaNormal
import com.nuvio.tv.balootv.ui.componentes.rememberFilasArriba
import com.nuvio.tv.balootv.ui.componentes.rememberListaQuieta
import com.nuvio.tv.balootv.errores.MensajesError
import com.nuvio.tv.balootv.errores.Red
import com.nuvio.tv.balootv.ui.componentes.CabeceraEnfocada
import com.nuvio.tv.balootv.ui.componentes.FilaCarteles
import com.nuvio.tv.balootv.inicio.EnCurso
import com.nuvio.tv.balootv.inicio.EpisodioFicha
import com.nuvio.tv.balootv.ui.componentes.huecoFinal
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem
import com.nuvio.tv.ui.screens.home.HomeEvent
import com.nuvio.tv.ui.screens.home.HomeViewModel
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import androidx.compose.ui.focus.focusRestorer
import com.nuvio.tv.balootv.ui.componentes.devolverFoco
import com.nuvio.tv.balootv.ui.componentes.RecordarAlSalir
import com.nuvio.tv.balootv.ui.componentes.pedirTrasDibujar
import com.nuvio.tv.balootv.ui.movimiento.ConRitmo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.nuvio.tv.balootv.ui.componentes.FondoFundido
import com.nuvio.tv.balootv.ui.componentes.TITULO_SIN_FOCO
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.lazy.itemsIndexed
import com.nuvio.tv.balootv.ui.componentes.ELEGIDO
import com.nuvio.tv.balootv.ui.componentes.primero
import com.nuvio.tv.balootv.ui.componentes.VELO_ENFOCADO
import androidx.compose.ui.platform.LocalDensity
import com.nuvio.tv.balootv.ui.componentes.HUECO_SOBRE_FILA
import com.nuvio.tv.balootv.ui.componentes.ALTO_CABECERA_ENFOCADA
import com.nuvio.tv.balootv.ui.movimiento.Movimiento
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.tv.material3.Border
import kotlinx.coroutines.launch

private val PILDORA = RoundedCornerShape(20.dp)
private val TARJETA = RoundedCornerShape(8.dp)

/**
 * Inicio de BalooTV: barra de pestañas, Seguir viendo de Trakt (`HomeViewModel`) y las filas de ideas del día
 * (`IdeasViewModel`: temporada nueva, Para ti, Porque viste, lo de esta semana y lo del mes). Al bajar a las filas, arriba
 * sale la cabecera del título enfocado con su nota IMDb.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InicioScreen(
    onIr: (String) -> Unit,
    /** La ficha de un cartel; en «Ya está aquí la temporada nueva», con el episodio en el que abrirla. */
    onAbrir: (MetaPreview, EpisodioFicha?) -> Unit,
    onSeguir: (ContinueWatchingItem) -> Unit,
    /** La ficha de un título de Seguir viendo (id y tipo), desde su pulsación larga. */
    onFicha: (String, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    enfoque: EnfoqueViewModel = hiltViewModel(),
    ideas: IdeasViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsState()
    val filas by ideas.filas.collectAsState()
    val ideasListas by ideas.listas.collectAsState()
    val foco by enfoque.estado.collectAsState()
    val contextoAvisos = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.mensajes.collect { Toast.makeText(contextoAvisos, it, Toast.LENGTH_SHORT).show() } }
    val abrirMenu = rememberMenuTitulo(onQuitar = { o ->
        viewModel.onEvent(HomeEvent.OnRemoveContinueWatching(contentId = o.id, season = o.episodio?.temporada, episode = o.episodio?.episodio, isNextUp = o.esSerie))
    }, onFicha = { o -> enfoque.vuelta.abrir(FILA_SEGUIR, o.id); onFicha(o.id, o.tipo) })
    val focoBarra = remember { FocusRequester() }

    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { viewModel.refreshHomeCatalogsIfStale(); ideas.refrescar() } }
        ciclo.lifecycle.addObserver(observador)
        onDispose { ciclo.lifecycle.removeObserver(observador) }
    }

    // Lo que está en Seguir viendo no sale como idea.
    LaunchedEffect(ui.continueWatchingItems) {
        ideas.seguirViendo(ui.continueWatchingItems.map { EnCurso(contenido(it), esSerie(it)) })
    }
    val enFilas = foco.enfocado != null

    // Avisa a MainActivity de que puede quitar la pantalla de carga (lo hacía el Inicio heredado).
    val arranque = com.nuvio.tv.ui.components.LocalStartupLoadingState.current
    val listo = filas.isNotEmpty() || ui.continueWatchingItems.isNotEmpty() || !ui.isLoading
    LaunchedEffect(listo, arranque) { if (listo) arranque?.complete = true }
    // Al volver de una ficha o del reproductor, el foco vuelve a lo que se abrió; si no, a las pestañas.
    RecordarAlSalir(enfoque.vuelta)
    LaunchedEffect(listo) { if (listo && !enfoque.vuelta.activa) runCatching { focoBarra.requestFocus() } }

    // Al pasar de la cabecera a las filas (o al revés) cambia lo que hay arriba: el contenido entra con un fundido
    // corto, sobre el mismo fondo, que se funde de una imagen a otra.
    val entrada = remember { Animatable(1f) }
    val primeraVez = remember { booleanArrayOf(true) }
    LaunchedEffect(enFilas) {
        if (primeraVez[0]) { primeraVez[0] = false; return@LaunchedEffect }
        entrada.snapTo(ALFA_INICIO_CAMBIO)
        entrada.animateTo(1f, tween(DURACION_CAMBIO_MS))
    }
    // La fila de carteles con el foco, para saber cuál colocar al cambiar de modo (un título puede estar en varias).
    val filaEnfocada = remember { arrayOfNulls<String>(1) }
    // Con una tarjeta de Seguir viendo enfocada, de fondo su serie o su película, como en las filas; en las pestañas, negro.
    var fondoSeguir by remember { mutableStateOf<String?>(null) }

    ConRitmo(Modifier.fillMaxSize()) {
        FondoInicio(if (enFilas) foco.fondo else fondoSeguir)
        Column(Modifier.fillMaxSize()) {
            // Solo se funde la cabecera: fundir la pantalla entera era una capa de 1080p más en cada fotograma.
            if (enFilas) CabeceraEnfocada(foco.enfocado, foco.nota, Modifier.padding(top = ARRIBA_CABECERA_ENFOCADA).graphicsLayer { alpha = entrada.value })
            // Arriba (pestañas y Seguir viendo) manda el desplazamiento normal; en las filas, la enfocada sube.
            val normal = LocalBringIntoViewSpec.current
            val arriba = rememberFilasArriba()
            val lista = rememberLazyListState()
            val densidad = LocalDensity.current
            // La fila queda donde la dejaría el foco al moverse por ella (si no, el primer paso la movía unos píxeles).
            val hueco = with(densidad) { HUECO_SOBRE_FILA.roundToPx() }
            val altoCabecera = with(densidad) { (ARRIBA_CABECERA_ENFOCADA + ALTO_CABECERA_ENFOCADA).roundToPx() }
            val alcance = rememberCoroutineScope()
            // En las filas la barra no está dibujada y con ella faltaba su Atrás: la app se cerraba sin avisar. Atrás
            // vuelve a la vista de arriba y deja el foco en las pestañas, como en las demás portadas.
            BackHandler(enabled = enFilas) {
                enfoque.limpiar()
                alcance.launch { focoBarra.pedirTrasDibujar() }
            }
            // Dónde queda Seguir viendo con la lista arriba del todo: a donde vuelve al subir desde las filas.
            val seguirArriba = remember { intArrayOf(-1) }
            val enFilasAhora by rememberUpdatedState(enFilas)
            LaunchedEffect(lista) {
                snapshotFlow {
                    if (lista.firstVisibleItemIndex != 0 || lista.firstVisibleItemScrollOffset != 0) null
                    else lista.layoutInfo.visibleItemsInfo.firstOrNull { it.key == FILA_SEGUIR }?.offset
                }.collect { y -> if (y != null && !enFilasAhora) seguirArriba[0] = y }
            }
            // Al cambiar de modo la lista cambia de elementos y se recolocaba de golpe: parecía otra pantalla. Ahora, en
            // el mismo fotograma, la fila a la que va el foco se deja donde estaba y desde ahí se desplaza a su sitio con
            // el muelle de siempre, como al pasar de una fila a otra.
            val modoAnterior = remember { booleanArrayOf(enFilas) }
            SideEffect {
                if (modoAnterior[0] == enFilas) return@SideEffect
                modoAnterior[0] = enFilas
                val visibles = lista.layoutInfo.visibleItemsInfo
                if (!enFilas) {
                    // La lista estaba bajo la cabecera del título enfocado y ahora empieza arriba del todo.
                    val antes = visibles.firstOrNull { it.key == FILA_SEGUIR }?.offset?.plus(altoCabecera)
                    if (antes == null || seguirArriba[0] < 0 || ui.continueWatchingItems.isEmpty()) {
                        lista.requestScrollToItem(0)
                    } else {
                        lista.requestScrollToItem(1, -antes)
                        alcance.launch { withFrameNanos { }; lista.animateScrollBy((antes - seguirArriba[0]).toFloat(), Movimiento.desplazamiento) }
                    }
                    return@SideEffect
                }
                val fila = filas.indexOfFirst { it.clave == filaEnfocada[0] }
                    .takeIf { it >= 0 } ?: filas.indexOfFirst { f -> f.items.any { it.id == foco.enfocado?.id } }
                if (fila < 0) return@SideEffect
                val indice = fila + if (ui.continueWatchingItems.isNotEmpty()) 1 else 0
                // Antes, la lista empezaba arriba del todo; ahora, bajo la cabecera del título enfocado.
                val antes = visibles.firstOrNull { it.key == filas[fila].clave }?.offset?.minus(altoCabecera)
                if (antes == null || antes <= hueco) {
                    lista.requestScrollToItem(indice, -hueco)
                } else {
                    lista.requestScrollToItem(indice, -antes)
                    alcance.launch { withFrameNanos { }; lista.animateScrollBy((antes - hueco).toFloat(), Movimiento.desplazamiento) }
                }
            }
            // Arriba cabe todo (pestañas y Seguir viendo): la lista no se mueve. Con el desplazamiento de
            // Android TV, que lleva lo enfocado a un tercio de la pantalla, cada paso en Seguir viendo bajaba la lista.
            val quieta = rememberListaQuieta()
            CompositionLocalProvider(LocalBringIntoViewSpec provides if (enFilas) arriba else quieta) {
                LazyColumn(Modifier.weight(1f), state = lista, contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!enFilas) item(key = "barra") { BarraPestanas(Pestana.INICIO, onIr, focoBarra) }
                    if (ui.continueWatchingItems.isNotEmpty()) item(key = "seguir") {
                        FilaNormal(normal) {
                            FilaSeguir(
                                ui.continueWatchingItems, onFoco = { enfoque.limpiar(); fondoSeguir = it.fondo }, onSalir = { fondoSeguir = null },
                                onClick = { enfoque.vuelta.abrir(FILA_SEGUIR, contenido(it)); onSeguir(it) },
                                onMantener = { abrirMenu(ObjetivoAccion.de(it)) },
                                idAlVolver = enfoque.vuelta.en(FILA_SEGUIR), onVuelto = enfoque.vuelta::cumplida,
                            )
                        }
                    }
                    items(filas, key = { it.clave }) { fila ->
                        FilaNormal(normal) {
                            FilaCarteles(
                                fila.titulo, fila.items, { filaEnfocada[0] = fila.clave; enfoque.enfocar(it) },
                                onClick = { enfoque.vuelta.abrir(fila.clave, it.id); onAbrir(it, fila.temporadas[it.id]) },
                                onMantener = { abrirMenu(ObjetivoAccion.de(it)) },
                                idAlVolver = enfoque.vuelta.en(fila.clave), onVuelto = enfoque.vuelta::cumplida,
                            )
                        }
                    }
                    if (ideasListas && !ui.isLoading && filas.isEmpty() && ui.continueWatchingItems.isEmpty()) item(key = "sin-ideas") {
                        val contexto = LocalContext.current
                        val m = remember(ui.isLoading) { MensajesError.inicio(Red.hayConexion(contexto)) }
                        Column(Modifier.fillMaxWidth().padding(start = 50.dp, top = 80.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(m.titulo, fontSize = EscalaTv.TITULO_3, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(m.texto, fontSize = EscalaTv.CUERPO, color = Color.White.copy(alpha = 0.7f))
                            Boton("Volver a intentarlo", principal = true, onClick = { viewModel.onEvent(HomeEvent.OnRetry); ideas.refrescar() })
                        }
                    }
                    huecoFinal()
                }
            }
        }
    }
}

/**
 * Fondo del Inicio: el del título enfocado (en las filas o en Seguir viendo), a sangre y fundido a negro por la izquierda
 * y por abajo; en las pestañas, negro.
 */
@Composable
private fun FondoInicio(url: String?) {
    Box(Modifier.fillMaxWidth().height(400.dp)) {
        FondoFundido(url, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(VELO_ENFOCADO))
    }
}

private const val DURACION_CAMBIO_MS = 400
private const val ALFA_INICIO_CAMBIO = 0f
private val ARRIBA_CABECERA_ENFOCADA = 24.dp

@Composable
private fun Boton(texto: String, principal: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(PILDORA),
        colors = ClickableSurfaceDefaults.colors(
            // Blanco solo con el foco: Reproducir se distingue con un cristal más claro.
            containerColor = if (principal) ELEGIDO else Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Text(if (principal) "▶  $texto" else texto, Modifier.padding(horizontal = 20.dp, vertical = 9.dp), fontSize = EscalaTv.CUERPO, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FilaSeguir(
    items: List<ContinueWatchingItem>,
    onFoco: (TarjetaSeguir) -> Unit,
    /** El foco sale de la fila (a las pestañas, a las filas o a otra pantalla). */
    onSalir: () -> Unit,
    onClick: (ContinueWatchingItem) -> Unit,
    onMantener: (ContinueWatchingItem) -> Unit,
    idAlVolver: String?,
    onVuelto: () -> Unit,
) {
    var conFoco by remember { mutableStateOf(false) }
    val alfaTitulo by animateFloatAsState(if (conFoco) 1f else TITULO_SIN_FOCO, tween(200), label = "tituloSeguir")
    Column(Modifier.fillMaxWidth().onFocusChanged { conFoco = it.hasFocus; if (!it.hasFocus) onSalir() }) {
        Text(
            "Seguir viendo", Modifier.padding(start = 50.dp, bottom = 8.dp).graphicsLayer { alpha = alfaTitulo },
            fontSize = EscalaTv.CUERPO, fontWeight = FontWeight.Bold, color = Color.White,
        )
        // Al entrar desde arriba, la primera vez el foco cae en la primera tarjeta (no en la que quede debajo).
        val focoPrimera = remember { FocusRequester() }
        LazyRow(Modifier.focusRestorer(focoPrimera), contentPadding = PaddingValues(horizontal = 50.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            itemsIndexed(items, key = { _, it -> clave(it) }) { i, item ->
                val t = remember(item) { TarjetaSeguir.de(item) }
                Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Surface(
                        onClick = { onClick(item) },
                        onLongClick = { onMantener(item) },
                        modifier = Modifier.fillMaxWidth().height(112.5.dp).onFocusChanged { if (it.isFocused) onFoco(t) }
                            .primero(i, focoPrimera).devolverFoco(contenido(item) == idAlVolver, onVuelto),
                        shape = ClickableSurfaceDefaults.shape(TARJETA),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1C1C1E), focusedContainerColor = Color(0xFF1C1C1E)),
                        // Como los carteles: crece y se bordea de blanco; con solo el zoom no se veía cuál era.
                        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.5.dp, Color.White), shape = TARJETA)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.14f, pressedScale = 1.05f),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            // Si una imagen no carga se prueba la siguiente; sin ninguna, el título sobre la tarjeta.
                            var intento by remember(t) { mutableIntStateOf(0) }
                            val url = t.imagenes.getOrNull(intento)
                            if (url != null) {
                                AsyncImage(model = url, contentDescription = t.titulo, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                                    onError = { intento++ })
                            } else {
                                Text(t.titulo, Modifier.align(Alignment.Center).padding(horizontal = 12.dp), fontSize = EscalaTv.CUERPO, fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            // El siguiente episodio no lleva barra vacía: lo dice su icono.
                            if (!t.siguiente) {
                                Box(Modifier.align(Alignment.BottomStart).padding(8.dp).fillMaxWidth().height(2.5.dp).background(Color.White.copy(alpha = 0.3f), CircleShape)) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(t.progreso).background(Color.White, CircleShape))
                                }
                            }
                        }
                    }
                    // Con hueco arriba: la imagen crece con el foco y no debe tapar el título.
                    Text(t.titulo, Modifier.padding(top = 7.dp), fontSize = EscalaTv.PIE_2, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t.corto.isNotEmpty()) {
                        com.nuvio.tv.balootv.ui.componentes.LineaPiezas(t.corto, peso = FontWeight.Medium, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

private const val FILA_SEGUIR = "seguir"

/** El título, sin el episodio: al volver del reproductor la tarjeta ya puede ser la del siguiente. */
private fun contenido(item: ContinueWatchingItem): String = when (item) {
    is ContinueWatchingItem.InProgress -> item.progress.contentId
    is ContinueWatchingItem.NextUp -> item.info.contentId
}

private fun esSerie(item: ContinueWatchingItem): Boolean = when (item) {
    is ContinueWatchingItem.InProgress -> item.progress.contentType != "movie"
    is ContinueWatchingItem.NextUp -> true
}

private fun clave(item: ContinueWatchingItem): String = when (item) {
    is ContinueWatchingItem.InProgress -> "p:${item.progress.contentId}:${item.progress.videoId}"
    is ContinueWatchingItem.NextUp -> "n:${item.info.contentId}:${item.info.videoId}"
}
