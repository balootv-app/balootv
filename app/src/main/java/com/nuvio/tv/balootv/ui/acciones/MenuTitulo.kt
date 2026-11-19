package com.nuvio.tv.balootv.ui.acciones

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.ui.componentes.Text

private val PANEL = RoundedCornerShape(22.dp)
private val OPCION = RoundedCornerShape(13.dp)
private val TECLAS_OK = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Menú de pulsación larga sobre un título (Seguir viendo, carátulas, Mi lista, Buscar). Devuelve la función que lo
 * abre. [onQuitar] y [onFicha] solo se llaman desde las tarjetas de Seguir viendo: la pantalla que las pinta quita la
 * tarjeta al momento y avisa a Trakt, o abre la ficha del título (allí OK reproduce).
 */
@Composable
fun rememberMenuTitulo(onQuitar: (ObjetivoAccion) -> Unit = {}, onFicha: (ObjetivoAccion) -> Unit = {}): (ObjetivoAccion) -> Unit {
    val viewModel: AccionesViewModel = hiltViewModel()
    val menu by viewModel.menu.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.avisos.collect { Toast.makeText(contexto, it, Toast.LENGTH_SHORT).show() }
    }
    menu?.let { abierto ->
        Dialog(onDismissRequest = viewModel::cerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                PanelMenu(
                    abierto,
                    onElegir = { accion ->
                        when (accion) {
                            AccionTitulo.IR_A_FICHA -> { viewModel.cerrar(); onFicha(abierto.objetivo) }
                            AccionTitulo.QUITAR -> { viewModel.cerrar(); onQuitar(abierto.objetivo) }
                            AccionTitulo.MARCAR_VISTA -> viewModel.marcarVista(abierto)
                            AccionTitulo.DESMARCAR_VISTA -> viewModel.desmarcarVista(abierto)
                            AccionTitulo.ANADIR_A_LISTA -> viewModel.anadirALista(abierto.objetivo)
                            AccionTitulo.QUITAR_DE_LISTA -> viewModel.quitarDeLista(abierto.objetivo)
                        }
                    },
                )
            }
        }
    }
    return remember(viewModel) { { objetivo: ObjetivoAccion -> viewModel.abrir(objetivo) } }
}

@Composable
private fun PanelMenu(menu: MenuAbierto, onElegir: (AccionTitulo) -> Unit) {
    val primera = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { primera.requestFocus() } }
    // El menú se abre con OK aún pulsado: sus repeticiones y el soltar llegarían a la primera opción y la elegirían
    // solas. Se ignora OK hasta la primera pulsación nueva.
    var okNuevo by remember { mutableStateOf(false) }
    Column(
        Modifier
            .onPreviewKeyEvent { evento ->
                if (evento.key !in TECLAS_OK || okNuevo) return@onPreviewKeyEvent false
                if (evento.type == KeyEventType.KeyDown && evento.nativeKeyEvent.repeatCount == 0) {
                    okNuevo = true
                    false
                } else {
                    true
                }
            }
            .width(340.dp).background(Color(0xFF1C1C1E), PANEL).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            menu.objetivo.nombre, Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            fontSize = EscalaTv.LLAMADA, fontWeight = FontWeight.Bold, color = Color.White,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        menu.opciones.forEachIndexed { i, accion ->
            Surface(
                onClick = { onElegir(accion) },
                modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(primera) else Modifier),
                shape = ClickableSurfaceDefaults.shape(OPCION),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.14f), contentColor = Color.White,
                    focusedContainerColor = Color.White, focusedContentColor = Color.Black,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
            ) {
                Text(menu.texto(accion), Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontSize = EscalaTv.CUERPO, fontWeight = FontWeight.Bold)
            }
        }
    }
}
