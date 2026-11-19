package com.nuvio.tv.balootv.ui.ficha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.ui.componentes.FondoEsmerilado
import com.nuvio.tv.balootv.ui.componentes.Text

private val FORMA = RoundedCornerShape(12.dp)
private val TECLAS_OK = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
private const val LINEAS = 4
private val RELLENO_X = 10.dp
private val RELLENO_Y = 6.dp

/**
 * La sinopsis de la ficha en [LINEAS] líneas. Si no cabe, se enfoca subiendo desde los botones (como en tvOS) y con
 * OK se abre entera encima de la ficha; Atrás u OK la cierran. Antes se quedaba en «…» sin forma de leer el resto.
 * El hueco es el mismo enfocable o no, para que el texto no se mueva al saber si cabe.
 */
@Composable
internal fun Sinopsis(texto: String, titulo: String, fondo: String?, modifier: Modifier = Modifier) {
    var cortada by remember(texto) { mutableStateOf(false) }
    var abierta by remember { mutableStateOf(false) }
    // Siempre activa: tv-material atenúa un Surface desactivado. Lo que la deja fuera del foco es canFocus.
    Surface(
        onClick = { abierta = true },
        modifier = modifier
            // El relleno del resalte sale hacia fuera y no ocupa sitio: el texto queda alineado con lo de arriba y
            // los botones no bajan.
            .layout { medible, restricciones ->
                val x = RELLENO_X.roundToPx()
                val y = RELLENO_Y.roundToPx()
                val p = medible.measure(restricciones.offset(horizontal = 2 * x, vertical = 2 * y))
                layout(p.width - 2 * x, p.height - 2 * y) { p.place(-x, -y) }
            }
            .focusProperties { canFocus = cortada },
        shape = ClickableSurfaceDefaults.shape(FORMA),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.14f), focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Text(
            texto, Modifier.padding(horizontal = RELLENO_X, vertical = RELLENO_Y), fontSize = EscalaTv.PIE_1,
            color = Color.White.copy(alpha = 0.8f), maxLines = LINEAS, overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (it.hasVisualOverflow) cortada = true },
        )
    }
    if (abierta) SinopsisEntera(texto, titulo, fondo) { abierta = false }
}

@Composable
private fun SinopsisEntera(texto: String, titulo: String, fondo: String?, onCerrar: () -> Unit) {
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val foco = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
        // Se abre con OK aún pulsado: el soltar o sus repeticiones la cerrarían enseguida. OK cierra solo tras una
        // pulsación nueva (como el menú de pulsación larga).
        var okNuevo by remember { mutableStateOf(false) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .onPreviewKeyEvent { evento ->
                    if (evento.key !in TECLAS_OK || okNuevo) return@onPreviewKeyEvent false
                    if (evento.type == KeyEventType.KeyDown && evento.nativeKeyEvent.repeatCount == 0) {
                        okNuevo = true
                        false
                    } else {
                        true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = onCerrar,
                modifier = Modifier.width(820.dp).focusRequester(foco),
                shape = ClickableSurfaceDefaults.shape(FORMA),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent, contentColor = Color.White,
                    focusedContainerColor = Color.Transparent, focusedContentColor = Color.White,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Box {
                    FondoEsmerilado(fondo, FORMA, Modifier.matchParentSize())
                    Column(Modifier.padding(horizontal = 40.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(titulo, fontSize = EscalaTv.TITULO_3, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(texto, fontSize = EscalaTv.CUERPO, color = Color.White.copy(alpha = 0.9f), maxLines = 18, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
