package com.nuvio.tv.balootv.ui.reproductor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.tv.balootv.ui.componentes.Text
import com.nuvio.tv.balootv.ui.componentes.BotonCristal
import com.nuvio.tv.balootv.ui.componentes.cristal
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.ui.componentes.LineaPiezas
import com.nuvio.tv.balootv.fuentes.Formato
import com.nuvio.tv.balootv.iconos.Pieza
import com.nuvio.tv.balootv.iconos.Simbolo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/**
 * Capa del reproductor (tablero 21): degradado abajo, título y etiqueta de la fuente, barra de progreso, tiempos
 * y una fila de botones de cristal. La barra y los tiempos los pone el reproductor (conocen el reloj).
 */
@Composable
fun CapaReproductor(
    titulo: String,
    /** "T1 · E2 · El camino real", en pequeño encima del título; null en películas. */
    episodio: String?,
    /** El formato de la fuente que suena, en insignias y sin códec (solo sale en «Cambiar fuente»). */
    etiqueta: Formato?,
    reproduciendo: Boolean,
    esSerie: Boolean,
    haySiguiente: Boolean,
    /** «Parar al acabar este episodio»: en los créditos sale del reproductor en vez de seguir. */
    pararAlAcabar: Boolean,
    focoBotones: FocusRequester,
    onPlayPause: () -> Unit,
    onAudioSubtitulos: () -> Unit,
    onCambiarFuente: () -> Unit,
    onEpisodios: () -> Unit,
    onSiguiente: () -> Unit,
    onPararAlAcabar: () -> Unit,
    onFoco: () -> Unit,
    modifier: Modifier = Modifier,
    bloqueInferior: Modifier = Modifier,
    barra: @Composable () -> Unit,
    tiempos: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(210.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
        )
        Column(
            bloqueInferior
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 50.dp, end = 50.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            episodio?.let {
                Text(it, fontSize = EscalaTv.PIE_1, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(titulo, fontSize = if (episodio != null) EscalaTv.TITULO_3 else EscalaTv.LLAMADA, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (etiqueta != null) {
                    LineaPiezas(etiqueta.piezas(conCodec = false), Modifier.padding(bottom = 4.dp), color = Color.White.copy(alpha = 0.7f))
                }
            }
            barra()
            tiempos()
            Row(
                Modifier.padding(top = 4.dp).onFocusChanged { if (it.hasFocus) onFoco() },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val relleno = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                BotonCristal(if (reproduciendo) "Pausa" else "Reproducir", onPlayPause, Modifier.focusRequester(focoBotones), relleno = relleno)
                BotonCristal("Audio y subtítulos", onAudioSubtitulos, relleno = relleno)
                BotonCristal("Cambiar fuente", onCambiarFuente, relleno = relleno)
                if (esSerie) BotonCristal("Episodios", onEpisodios, relleno = relleno)
                if (haySiguiente) {
                    BotonCristal("Siguiente episodio", onSiguiente, relleno = relleno)
                    // Encendido se ve con el cristal claro de lo elegido; se apaga pulsando otra vez.
                    BotonCristal(if (pararAlAcabar) "Se parará al acabar" else "Parar al acabar", onPararAlAcabar,
                        principal = pararAlAcabar, relleno = relleno)
                }
            }
        }
    }
}

/** "Esa fuente falla. Probando la siguiente: …", arriba al centro y discreto (tablero 21); con [icono] delante si lo hay. */
@Composable
fun AvisoFuente(texto: String, modifier: Modifier = Modifier, icono: Simbolo? = null) {
    Box(modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
        LineaPiezas(
            listOfNotNull(icono?.let { Pieza.Icono(it) }, Pieza.Texto(texto)),
            Modifier.cristal(RoundedCornerShape(15.dp)).padding(horizontal = 13.dp, vertical = 7.dp),
            color = Color.White,
        )
    }
}

/**
 * Al empezar una fuente, unos segundos arriba a la derecha: [4K] [DV] 🔊 Castellano, como el distintivo de formato del
 * Apple TV. Confirma de un vistazo que suena en castellano sin abrir los controles.
 */
@Composable
fun InsigniaFormato(formato: Formato?, reproduciendo: Boolean, controlesVisibles: Boolean, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    val reproduciendoAhora by rememberUpdatedState(reproduciendo)
    // Una vez por fuente: espera a que empiece a sonar y cuenta 4 s seguidos (un parón para cargar no la reinicia).
    LaunchedEffect(formato) {
        if (formato == null) return@LaunchedEffect
        snapshotFlow { reproduciendoAhora }.first { it }
        visible = true
        delay(4_000)
        visible = false
    }
    AnimatedVisibility(visible = visible && !controlesVisibles && formato != null, enter = fadeIn(tween(300)), exit = fadeOut(tween(400)), modifier = modifier) {
        LineaPiezas(
            formato?.piezas(conCodec = false).orEmpty(),
            Modifier.cristal(RoundedCornerShape(15.dp)).padding(horizontal = 13.dp, vertical = 7.dp),
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}
