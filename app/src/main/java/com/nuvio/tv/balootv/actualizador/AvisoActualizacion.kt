package com.nuvio.tv.balootv.actualizador

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.balootv.ajustes.Fila
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.ui.componentes.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActualizacionViewModel @Inject constructor(private val actualizador: Actualizador) : ViewModel() {
    val estado: StateFlow<EstadoActualizacion> = actualizador.estado
    val activo: Boolean get() = actualizador.activo

    fun comprobar(aunqueAplazada: Boolean = false, hecho: (Actualizacion?) -> Unit = {}) =
        viewModelScope.launch { hecho(actualizador.comprobar(aunqueAplazada)) }

    fun actualizar(a: Actualizacion) = viewModelScope.launch { actualizador.actualizar(a) }
    fun aplazar(a: Actualizacion) = viewModelScope.launch { actualizador.aplazar(a) }

    fun abrirPermiso(context: Context) {
        runCatching { context.startActivity(actualizador.ajustesDelPermiso()) }
    }
}

/** Cómo se da el permiso de instalar apps según el aparato (cada sistema lo esconde en un sitio). */
const val PERMISO_INSTALAR = "Para instalarla, BalooTV necesita permiso para instalar apps. En Google TV y Android TV: " +
    "Ajustes → Apps → Seguridad y restricciones → Fuentes desconocidas → BalooTV. En Fire TV: Configuración → Mi Fire TV → " +
    "Opciones para desarrolladores → Instalar apps desconocidas → BalooTV."

/**
 * El aviso de versión nueva: una tarjeta abajo a la derecha del Inicio con «Actualizar» y «Más tarde». Solo se
 * pinta donde lo pone quien lo llama (el Inicio), nunca en el reproductor. Busca una vez al abrir la app.
 */
@Composable
fun AvisoActualizacion(visible: Boolean, viewModel: ActualizacionViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { if (viewModel.activo) viewModel.comprobar() }
    val estado by viewModel.estado.collectAsState()
    if (!visible || estado == EstadoActualizacion.Nada) return
    val context = LocalContext.current
    val foco = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var dentro by remember { mutableStateOf(false) }
    // Atrás con el aviso enfocado es «Más tarde», no salir de la app (gana al Atrás del Inicio por registrarse después).
    val aplazable = (estado as? EstadoActualizacion.Disponible)?.a ?: (estado as? EstadoActualizacion.FaltaPermiso)?.a
        ?: (estado as? EstadoActualizacion.Fallo)?.a
    BackHandler(enabled = dentro && aplazable != null) { aplazable?.let { viewModel.aplazar(it) } }
    // El Inicio coloca su foco al aparecer: se espera a que lo haga y se trae al aviso. Soltar y pedir, porque si el
    // botón ya tenía el foco antes de que su Surface escuche no se pintaría (ver PrimerArranque).
    LaunchedEffect(estado::class) {
        kotlinx.coroutines.delay(600)
        focusManager.clearFocus(force = true)
        runCatching { foco.requestFocus() }
    }

    Box(Modifier.fillMaxSize().padding(end = 48.dp, bottom = 40.dp), contentAlignment = Alignment.BottomEnd) {
        Column(
            Modifier.widthIn(max = 460.dp)
                .onFocusChanged { dentro = it.hasFocus }
                .background(Color(0xF0161A1C), RoundedCornerShape(16.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (val e = estado) {
                is EstadoActualizacion.Disponible -> {
                    Text("BalooTV ${e.a.version} ya está aquí", color = Color.White, fontSize = EscalaTv.LLAMADA, fontWeight = FontWeight.SemiBold)
                    Notas(e.a.notas)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { Fila("Actualizar", "", Modifier.focusRequester(foco)) { viewModel.actualizar(e.a) } }
                        Box(Modifier.weight(1f)) { Fila("Más tarde", "") { viewModel.aplazar(e.a) } }
                    }
                }
                is EstadoActualizacion.Bajando -> {
                    val pct = e.progreso?.let { " · ${(it * 100).toInt()} %" }.orEmpty()
                    Text("Bajando BalooTV ${e.a.version}$pct", color = Color.White, fontSize = EscalaTv.LLAMADA, fontWeight = FontWeight.SemiBold)
                    Text("Puedes seguir usando la app; al terminar, Android pregunta si instalarla.", color = Color.White.copy(alpha = 0.7f), fontSize = EscalaTv.PIE_1)
                }
                is EstadoActualizacion.FaltaPermiso -> {
                    Text("Falta un permiso", color = Color.White, fontSize = EscalaTv.LLAMADA, fontWeight = FontWeight.SemiBold)
                    Text(PERMISO_INSTALAR, color = Color.White.copy(alpha = 0.75f), fontSize = EscalaTv.PIE_1)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { Fila("Abrir ajustes", "", Modifier.focusRequester(foco)) { viewModel.abrirPermiso(context) } }
                        Box(Modifier.weight(1f)) { Fila("Ya está", "") { viewModel.actualizar(e.a) } }
                    }
                }
                is EstadoActualizacion.Fallo -> {
                    Text(e.motivo, color = Color.White, fontSize = EscalaTv.CUERPO)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { Fila("Reintentar", "", Modifier.focusRequester(foco)) { viewModel.actualizar(e.a) } }
                        Box(Modifier.weight(1f)) { Fila("Más tarde", "") { viewModel.aplazar(e.a) } }
                    }
                }
                EstadoActualizacion.Nada -> Unit
            }
        }
    }
}

@Composable
private fun Notas(notas: String) {
    // Las notas de GitHub vienen en Markdown: la primera línea con texto que no sea un título basta.
    val linea = notas.lines().map { it.trim() }.filterNot { it.startsWith("#") }
        .map { it.trimStart('-', '*', ' ') }.firstOrNull { it.isNotBlank() } ?: return
    Text(linea, color = Color.White.copy(alpha = 0.7f), fontSize = EscalaTv.PIE_1, maxLines = 2)
}
