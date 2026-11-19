package com.nuvio.tv.balootv.ui.reproductor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import com.nuvio.tv.balootv.reproductor.AccionesContextuales
import com.nuvio.tv.balootv.reproductor.Poscreditos
import com.nuvio.tv.balootv.ui.componentes.BotonCristal
import com.nuvio.tv.balootv.ui.componentes.CristalBorde
import com.nuvio.tv.balootv.ui.componentes.imagenSinVer
import com.nuvio.tv.balootv.ui.componentes.CristalFondo
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.iconos.Pieza
import com.nuvio.tv.balootv.iconos.Simbolo
import com.nuvio.tv.balootv.ui.componentes.LineaPiezas
import com.nuvio.tv.balootv.ui.componentes.Text
import com.nuvio.tv.balootv.ui.componentes.pedirTrasDibujar
import com.nuvio.tv.data.repository.SkipInterval
import com.nuvio.tv.ui.screens.player.NextEpisodeInfo
import com.nuvio.tv.ui.screens.player.PostPlayMode
import com.nuvio.tv.ui.screens.player.isSkipIntroButtonVisible
import com.nuvio.tv.ui.screens.player.SKIP_INTRO_AUTO_HIDE_TIMEOUT_MS
import kotlinx.coroutines.delay

private val FORMA_BOTON = RoundedCornerShape(17.dp)
private const val PASO_MS = 100L
private val FORMA_TARJETA = RoundedCornerShape(16.dp)

/**
 * «Saltar introducción», «Saltar resumen» o «Saltar créditos», como las acciones contextuales de tvOS: aparece sola
 * con el foco, abajo a la derecha (sin pisar el título), y se esconde a los 10 s; con los controles a la vista vuelve
 * encima de ellos. OK salta (también lo garantiza el reproductor aunque el foco no haya llegado).
 */
@Composable
fun BotonSaltar(
    tramo: SkipInterval?,
    descartado: Boolean,
    aPoscreditos: Boolean,
    controlesVisibles: Boolean,
    reproduciendo: Boolean,
    puedeEnfocar: Boolean,
    foco: FocusRequester,
    focoAbajo: FocusRequester?,
    onSaltar: () -> Unit,
    onVisible: (Boolean) -> Unit,
    onEnfocado: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ultimoTipo by remember { mutableStateOf(tramo?.type) }
    if (tramo != null) ultimoTipo = tramo.type
    var ocultoSolo by remember { mutableStateOf(false) }
    var transcurrido by remember { mutableFloatStateOf(0f) }

    // Tramo nuevo, o se ha salido de él (al rebobinar antes de la intro vuelve a aparecer solo).
    LaunchedEffect(tramo?.startTime, tramo?.type, tramo == null) {
        ocultoSolo = false
        transcurrido = 0f
    }
    // Cuenta los 10 s solo mientras se ve el vídeo con los controles ocultos; cargando o en pausa se queda quieta.
    LaunchedEffect(tramo != null, descartado, ocultoSolo, controlesVisibles, reproduciendo) {
        if (tramo != null && !descartado && !ocultoSolo && !controlesVisibles && reproduciendo) {
            // Reloj real, no una animación: la tele tiene las animaciones del sistema a 0,5x y los 10 s duraban 5.
            while (transcurrido < 1f) {
                delay(PASO_MS)
                transcurrido = (transcurrido + PASO_MS.toFloat() / SKIP_INTRO_AUTO_HIDE_TIMEOUT_MS).coerceAtMost(1f)
            }
            ocultoSolo = true
        }
    }
    val visible = isSkipIntroButtonVisible(
        hasActiveInterval = tramo != null,
        dismissed = descartado,
        controlsVisible = controlesVisibles,
        autoHidden = ocultoSolo,
    )
    LaunchedEffect(visible) { onVisible(visible) }
    LaunchedEffect(visible, controlesVisibles, puedeEnfocar) {
        if (visible && !controlesVisibles && puedeEnfocar) foco.pedirTrasDibujar()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 4 },
        exit = fadeOut(tween(160)) + slideOutHorizontally(tween(200)) { it / 4 },
        modifier = modifier,
    ) {
        var enfocado by remember { mutableStateOf(false) }
        Surface(
            onClick = onSaltar,
            modifier = Modifier
                .focusRequester(foco)
                .focusProperties {
                    canFocus = puedeEnfocar
                    focoAbajo?.let { down = it }
                }
                .onFocusChanged {
                    enfocado = it.isFocused
                    if (it.isFocused) onEnfocado()
                },
            shape = ClickableSurfaceDefaults.shape(FORMA_BOTON),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = CristalFondo, contentColor = Color.White,
                focusedContainerColor = Color.White, focusedContentColor = Color.Black,
            ),
            border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(0.5.dp, CristalBorde), shape = FORMA_BOTON)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        ) {
            Box {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(AccionesContextuales.etiquetaSalto(ultimoTipo, aPoscreditos), fontSize = EscalaTv.CUERPO, fontWeight = FontWeight.SemiBold)
                }
                // Lo que queda antes de esconderse, en una línea fina al pie del botón (medida contra el botón).
                if (!controlesVisibles) {
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomStart) {
                        Box(
                            Modifier
                                .fillMaxWidth(1f - transcurrido)
                                .height(2.dp)
                                .background(if (enfocado) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.45f))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta de los créditos: el siguiente episodio en castellano con su imagen, «T1 · E2 · título» y una cuenta atrás
 * de [AccionesContextuales.CUENTA_ATRAS_S] s; OK lo pone ya, Atrás la descarta y deja ver los créditos. En
 * «¿Sigues viendo?» ofrece seguir o salir.
 */
@Composable
fun TarjetaSiguienteEpisodio(
    modo: PostPlayMode?,
    controlesVisibles: Boolean,
    foco: FocusRequester,
    focoAbajo: FocusRequester?,
    onReproducir: () -> Unit,
    onSeguirViendo: () -> Unit,
    onSalir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ultimo by remember { mutableStateOf(modo) }
    if (modo != null) ultimo = modo
    AnimatedVisibility(
        visible = modo != null,
        enter = fadeIn(tween(220)) + slideInHorizontally(tween(280)) { it / 3 },
        exit = fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 3 },
        modifier = modifier,
    ) {
        when (val m = ultimo) {
            is PostPlayMode.AutoPlay -> CuerpoSiguiente(m, controlesVisibles, foco, focoAbajo, onReproducir)
            is PostPlayMode.StillWatching -> CuerpoSigues(m, foco, onSeguirViendo, onSalir)
            null -> Unit
        }
    }
}

@Composable
private fun CuerpoSiguiente(
    modo: PostPlayMode.AutoPlay,
    controlesVisibles: Boolean,
    foco: FocusRequester,
    focoAbajo: FocusRequester?,
    onReproducir: () -> Unit,
) {
    val ep = modo.nextEpisode
    val estrenado = ep.hasAired && !(ep.released.isNullOrBlank() && ep.available == false)
    LaunchedEffect(Unit) { if (!controlesVisibles) foco.pedirTrasDibujar() }
    var enfocado by remember { mutableStateOf(false) }
    Surface(
        onClick = { if (estrenado) onReproducir() },
        modifier = Modifier
            .width(430.dp)
            .focusRequester(foco)
            .focusProperties { focoAbajo?.let { down = it } }
            .onFocusChanged { enfocado = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(FORMA_TARJETA),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = CristalFondo, contentColor = Color.White,
            focusedContainerColor = CristalFondo, focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.5.dp, CristalBorde), shape = FORMA_TARJETA),
            focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.95f)), shape = FORMA_TARJETA),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Column {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Miniatura(ep)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    // «Siguiente episodio» es su icono, con la temporada y el episodio (propuesta de iconos, T11).
                    LineaPiezas(
                        listOfNotNull(
                            Pieza.Icono(Simbolo.SIGUIENTE, "Siguiente episodio"),
                            if (ep.isOtherType) null else Pieza.Texto(AccionesContextuales.episodio(ep.season, ep.episode, null)),
                        ),
                        peso = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f),
                    )
                    Text(
                        if (ep.isOtherType) ep.title else ep.title.trim().ifEmpty { AccionesContextuales.episodio(ep.season, ep.episode, null) },
                        fontSize = EscalaTv.CUERPO, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    val estado = if (estrenado) AccionesContextuales.estado(modo.searching, modo.countdownSec) else "Aún no se ha estrenado"
                    Text(estado ?: if (enfocado) "Pulsa OK para verlo ya" else "Pulsa OK para verlo", fontSize = EscalaTv.PIE_2, color = Color.White.copy(alpha = 0.7f))
                }
            }
            // Barra de la cuenta atrás: se vacía segundo a segundo sin saltos. Con el reloj de fotogramas, no con una
            // animación (la tele tiene las animaciones del sistema a 0,5x).
            val objetivo = modo.countdownSec?.let { AccionesContextuales.fraccionCuentaAtras(it - 1) } ?: 1f
            var fraccion by remember { mutableFloatStateOf(objetivo) }
            LaunchedEffect(objetivo) {
                val desde = fraccion
                val inicio = withFrameNanos { it }
                do {
                    val t = ((withFrameNanos { it } - inicio) / 1_000_000_000f).coerceAtMost(1f)
                    fraccion = desde + (objetivo - desde) * t
                } while (t < 1f)
            }
            if (modo.countdownSec != null) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraccion).background(Color.White))
                }
            }
        }
    }
}

@Composable
private fun Miniatura(ep: NextEpisodeInfo) {
    Box(Modifier.size(128.dp, 72.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.08f))) {
        if (!ep.thumbnail.isNullOrBlank()) {
            // El siguiente episodio aún no se ha visto: su imagen va difuminada para que no destripe nada.
            AsyncImage(model = imagenSinVer(ep.thumbnail, sinVer = true), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().fillMaxHeight())
        }
    }
}

@Composable
private fun CuerpoSigues(
    modo: PostPlayMode.StillWatching,
    foco: FocusRequester,
    onSeguirViendo: () -> Unit,
    onSalir: () -> Unit,
) {
    LaunchedEffect(Unit) { foco.pedirTrasDibujar() }
    Column(
        Modifier.width(430.dp).clip(FORMA_TARJETA).background(CristalFondo).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("¿Sigues viendo?", fontSize = EscalaTv.TITULAR, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            listOfNotNull(
                AccionesContextuales.episodio(modo.nextEpisode.season, modo.nextEpisode.episode, modo.nextEpisode.title),
                modo.countdownSec?.let { "se detiene en $it s" },
            ).joinToString(" · "),
            fontSize = EscalaTv.PIE_1, color = Color.White.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonCristal("Seguir viendo", onSeguirViendo, Modifier.focusRequester(foco), principal = true)
            BotonCristal("Salir", onSalir)
        }
    }
}

/**
 * Tarjeta de los créditos de una película (C6): lleva a la escena poscréditos que marca IntroDB o, si no hay escena,
 * sale. Cuenta atrás con el reloj real y solo mientras se reproduce; OK lo hace ya y Atrás la descarta (lo lleva el
 * reproductor) y deja ver los créditos. Como la de siguiente episodio, una sola superficie: sin botones entre los que
 * moverse, izquierda y derecha siguen buscando.
 */
@Composable
fun TarjetaPoscreditos(
    aviso: Poscreditos.Aviso?,
    reproduciendo: Boolean,
    controlesVisibles: Boolean,
    foco: FocusRequester,
    focoAbajo: FocusRequester?,
    onIr: () -> Unit,
    onSalir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tarjeta = aviso?.takeIf { it is Poscreditos.Aviso.IrALaEscena || it == Poscreditos.Aviso.SinEscena }
    var ultima by remember { mutableStateOf(tarjeta) }
    if (tarjeta != null) ultima = tarjeta
    AnimatedVisibility(
        visible = tarjeta != null,
        enter = fadeIn(tween(220)) + slideInHorizontally(tween(280)) { it / 3 },
        exit = fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 3 },
        modifier = modifier,
    ) {
        val a = ultima ?: return@AnimatedVisibility
        val salir = a == Poscreditos.Aviso.SinEscena
        val total = if (salir) Poscreditos.CUENTA_ATRAS_SALIR_S else Poscreditos.CUENTA_ATRAS_ESCENA_S
        val accion by rememberUpdatedState(if (salir) onSalir else onIr)
        var restante by remember(a) { mutableIntStateOf(total) }
        var hecho by remember(a) { mutableStateOf(false) }
        // Reloj real y no una animación (la tele tiene las del sistema a 0,5x); en pausa o cargando se queda quieta.
        LaunchedEffect(a, reproduciendo) {
            while (reproduciendo && restante > 0) {
                delay(1_000)
                restante--
            }
            if (restante == 0 && !hecho) {
                hecho = true
                accion()
            }
        }
        LaunchedEffect(a) { if (!controlesVisibles) foco.pedirTrasDibujar() }
        var enfocado by remember { mutableStateOf(false) }
        Surface(
            onClick = { if (!hecho) { hecho = true; accion() } },
            modifier = Modifier
                .width(430.dp)
                .focusRequester(foco)
                .focusProperties { focoAbajo?.let { down = it } }
                .onFocusChanged { enfocado = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(FORMA_TARJETA),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = CristalFondo, contentColor = Color.White,
                focusedContainerColor = CristalFondo, focusedContentColor = Color.White,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(0.5.dp, CristalBorde), shape = FORMA_TARJETA),
                focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.95f)), shape = FORMA_TARJETA),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        ) {
            Column {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    LineaPiezas(
                        if (salir) listOf(Pieza.Icono(Simbolo.SIN_ESCENA), Pieza.Texto("Sin escena"))
                        else listOf(Pieza.Icono(Simbolo.ESCENA), Pieza.Texto("Escena poscréditos")),
                        peso = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f),
                    )
                    Text(if (salir) "Salir" else "Ir a la escena", fontSize = EscalaTv.CUERPO, fontWeight = FontWeight.Bold)
                    Text(
                        if (salir) "Sale en $restante s · Atrás para ver los créditos" else "Empieza en $restante s",
                        fontSize = EscalaTv.PIE_2, color = Color.White.copy(alpha = 0.7f),
                    )
                }
                BarraCuentaAtras(((restante - 1).coerceAtLeast(0).toFloat() / total).coerceIn(0f, 1f))
            }
        }
    }
}

/** Barra de una cuenta atrás: se vacía segundo a segundo sin saltos, con el reloj de fotogramas. */
@Composable
private fun BarraCuentaAtras(objetivo: Float) {
    var fraccion by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(objetivo) {
        val desde = fraccion
        val inicio = withFrameNanos { it }
        do {
            val t = ((withFrameNanos { it } - inicio) / 1_000_000_000f).coerceAtMost(1f)
            fraccion = desde + (objetivo - desde) * t
        } while (t < 1f)
    }
    Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraccion).background(Color.White))
    }
}

/**
 * Aviso de la escena poscréditos (C6): «Tiene escena poscréditos: no lo quites» al empezar los créditos o «… Pulsa
 * Atrás otra vez para salir». Arriba al centro, como el de la fuente, durante [Poscreditos.AVISO_S] s de reloj real.
 */
@Composable
fun AvisoPoscreditos(texto: String?, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    var ultimo by remember { mutableStateOf(texto) }
    LaunchedEffect(texto) {
        visible = false
        if (texto == null) return@LaunchedEffect
        ultimo = texto
        visible = true
        delay(Poscreditos.AVISO_S * 1_000L)
        visible = false
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200)), modifier = modifier) {
        ultimo?.let { AvisoFuente(it, icono = Simbolo.ESCENA) }
    }
}
