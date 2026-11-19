package com.nuvio.tv.balootv.ui.reproductor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.nuvio.tv.balootv.reproductor.RetrasoSubtitulos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.tv.balootv.ui.componentes.Text
import com.nuvio.tv.balootv.reproductor.EleccionSubtitulos
import com.nuvio.tv.balootv.reproductor.EtiquetaPista
import com.nuvio.tv.balootv.reproductor.OpcionesSubtitulos
import com.nuvio.tv.balootv.ui.componentes.FilaPanel
import com.nuvio.tv.balootv.ui.componentes.cristal
import com.nuvio.tv.ui.screens.player.TrackInfo
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.iconos.Simbolo
import com.nuvio.tv.balootv.ui.componentes.Icono

/**
 * Panel de cristal de audio y subtítulos (tablero 21). Subtítulos solo en castellano: forzados, completos o
 * desactivados; si no hay ninguno, la fila lo dice.
 */
@Composable
fun PanelAudioSubtitulos(
    audio: List<TrackInfo>,
    textoFuente: String?,
    audioElegido: Int,
    opciones: OpcionesSubtitulos,
    subtituloActual: EleccionSubtitulos,
    onAudio: (Int) -> Unit,
    onSubtitulos: (EleccionSubtitulos) -> Unit,
    retrasoMs: Int,
    onRetraso: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    Box(modifier.fillMaxSize()) {
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 50.dp, bottom = 115.dp)
                .width(300.dp)
                .cristal(RoundedCornerShape(15.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Columna(Simbolo.AUDIO, "Audio", Modifier.weight(1f)) {
                audio.forEach { pista ->
                    val elegida = pista.index == audioElegido
                    FilaPanel(
                        texto = EtiquetaPista.audio(pista, textoFuente),
                        elegida = elegida,
                        onClick = { onAudio(pista.index) },
                        modifier = if (elegida) Modifier.focusRequester(foco) else Modifier,
                    )
                }
                if (audio.isEmpty()) Text("Cargando…", fontSize = EscalaTv.PIE_2, color = Color.White.copy(alpha = 0.5f))
            }
            Columna(Simbolo.SUBTITULOS, "Subtítulos", Modifier.weight(1f)) {
                if (opciones.forzados != null) {
                    FilaPanel("Solo forzados", subtituloActual == EleccionSubtitulos.FORZADOS, { onSubtitulos(EleccionSubtitulos.FORZADOS) })
                }
                FilaPanel(
                    texto = if (opciones.completoInterno != null || opciones.completoExterno != null) "Castellano" else "No hay subtítulos en castellano",
                    elegida = subtituloActual == EleccionSubtitulos.CASTELLANO,
                    onClick = { onSubtitulos(EleccionSubtitulos.CASTELLANO) },
                    activa = opciones.completoInterno != null || opciones.completoExterno != null,
                )
                FilaPanel(
                    "Desactivados",
                    subtituloActual == EleccionSubtitulos.DESACTIVADOS,
                    { onSubtitulos(EleccionSubtitulos.DESACTIVADOS) },
                    modifier = if (audio.none { it.index == audioElegido }) Modifier.focusRequester(foco) else Modifier,
                )
                if (subtituloActual != EleccionSubtitulos.DESACTIVADOS) FilaRetraso(retrasoMs, onRetraso)
            }
        }
    }
}

/**
 * Retraso de los subtítulos: izquierda y derecha lo mueven de 0,1 en 0,1 s (a la derecha salen más tarde) y OK lo
 * deja a 0. Mientras tiene el foco, izquierda y derecha no cambian de columna.
 */
@Composable
private fun FilaRetraso(retrasoMs: Int, onRetraso: (Int) -> Unit) {
    var enfocada by remember { mutableStateOf(false) }
    val valor = RetrasoSubtitulos.texto(retrasoMs)
    FilaPanel(
        texto = "Retraso",
        elegida = false,
        onClick = { if (retrasoMs != 0) onRetraso(0) },
        secundario = if (enfocada) "‹ $valor ›" else valor,
        modifier = Modifier
            .padding(top = 6.dp)
            .onFocusChanged { enfocada = it.isFocused }
            .onPreviewKeyEvent { e ->
                val direccion = when (e.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onPreviewKeyEvent false
                }
                if (e.type == KeyEventType.KeyDown) onRetraso(RetrasoSubtitulos.paso(retrasoMs, direccion))
                true
            },
    )
}

@Composable
private fun Columna(simbolo: Simbolo, titulo: String, modifier: Modifier, contenido: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // La cabecera es su icono (propuesta de iconos, T13); las opciones siguen en palabras.
        Icono(simbolo, 18.dp, Modifier.padding(start = 9.dp, bottom = 2.dp), Color.White.copy(alpha = 0.5f), titulo)
        contenido()
    }
}
