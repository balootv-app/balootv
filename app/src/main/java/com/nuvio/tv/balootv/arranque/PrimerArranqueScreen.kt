package com.nuvio.tv.balootv.arranque

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nuvio.tv.R
import com.nuvio.tv.balootv.ajustes.FONDO
import com.nuvio.tv.balootv.ajustes.Fila
import com.nuvio.tv.balootv.ajustes.PanelVinculo
import com.nuvio.tv.balootv.ajustes.Vinculacion
import com.nuvio.tv.balootv.cuenta.EstadoTorbox
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.ui.componentes.Text
import com.nuvio.tv.balootv.ui.fuentes.ContenidoFuentes
import com.nuvio.tv.balootv.ui.fuentes.FuentesViewModel
import com.nuvio.tv.balootv.ui.fuentes.GUIA_FUENTES

private fun PasoPrimerArranque.titulo() = when (this) {
    PasoPrimerArranque.BIENVENIDA -> "Te damos la bienvenida a BalooTV"
    PasoPrimerArranque.TORBOX -> "Tu cuenta de TorBox"
    PasoPrimerArranque.TRAKT -> "Trakt (opcional)"
    PasoPrimerArranque.FUENTES -> "Tus fuentes"
}

private fun PasoPrimerArranque.explicacion() = when (this) {
    PasoPrimerArranque.BIENVENIDA -> "Películas y series en castellano de España, sin configurar nada a mano. Te ayudo a dejarla lista en tres pasos: tu cuenta de TorBox, Trakt si lo usas y tus fuentes."
    PasoPrimerArranque.TORBOX -> "TorBox es el servicio que prepara los enlaces. Necesitas una cuenta con la suscripción activa. La tele recibe tu clave sola al aprobar el código: no hay que escribirla."
    PasoPrimerArranque.TRAKT -> "Con Trakt, Seguir viendo, Mi lista y las ideas del Inicio salen de lo que ves. Sin Trakt la app funciona igual; puedes conectarlo más tarde en Ajustes."
    PasoPrimerArranque.FUENTES -> GUIA_FUENTES
}

/**
 * Primer arranque de la edición pública: bienvenida → TorBox → Trakt (se puede saltar) → fuentes → Inicio.
 * Cada paso se puede retomar después desde Ajustes. Atrás vuelve al paso anterior.
 */
@Composable
fun PrimerArranqueScreen(
    onAbrirTrakt: () -> Unit,
    onTerminar: () -> Unit,
    viewModel: PrimerArranqueViewModel = hiltViewModel(),
) {
    var paso by rememberSaveable { mutableStateOfPaso() }
    val vinculacion by viewModel.vinculacion.collectAsState()
    val torbox by viewModel.torbox.collectAsState()
    val trakt by viewModel.usuarioTrakt.collectAsState()
    val foco = remember { FocusRequester() }
    val terminar = { viewModel.terminar(onTerminar) }
    val avanzar = { AlmacenPrimerArranque.siguiente(paso)?.let { paso = it } ?: terminar() }

    BackHandler(enabled = paso != PasoPrimerArranque.BIENVENIDA) {
        viewModel.cancelarVinculo()
        AlmacenPrimerArranque.anterior(paso)?.let { paso = it }
    }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(paso) {
        if (paso == PasoPrimerArranque.TORBOX && torbox == null && vinculacion == null) viewModel.vincularTorbox()
        // Si la fila nueva es la única enfocable, Compose le da el foco al quitar la anterior, antes de que su Surface
        // escuche: lo tiene pero no se pinta, y volver a pedirlo no hace nada. Se suelta y se pide de nuevo.
        focusManager.clearFocus(force = true)
        runCatching { foco.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).background(FONDO)) {
        Row(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 70.dp)) {
            Column(
                Modifier.width(400.dp).padding(top = 25.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Image(
                    painterResource(R.drawable.balootv_banner),
                    contentDescription = "BalooTV",
                    modifier = Modifier.size(280.dp, 158.dp).clip(RoundedCornerShape(15.dp)),
                )
                Puntos(paso)
                Text(paso.titulo(), color = Color.White, fontSize = EscalaTv.TITULO_3, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(paso.explicacion(), color = Color.White.copy(alpha = 0.65f), fontSize = EscalaTv.CUERPO, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.width(60.dp))
            Column(Modifier.fillMaxWidth().padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                when (paso) {
                    PasoPrimerArranque.BIENVENIDA -> {
                        Fila("Empezar", "", Modifier.focusRequester(foco)) { avanzar() }
                    }
                    PasoPrimerArranque.TORBOX -> {
                        val lista = torbox == EstadoTorbox.Funciona || vinculacion == Vinculacion.Hecha
                        if (lista) {
                            Fila("Siguiente", "TorBox vinculado", Modifier.focusRequester(foco)) { avanzar() }
                            Fila("Usar otra cuenta", "") { viewModel.vincularTorbox() }
                        } else {
                            Fila(if (vinculacion is Vinculacion.Fallo) "Volver a intentarlo" else "Pedir otro código", "", Modifier.focusRequester(foco)) { viewModel.vincularTorbox() }
                            Fila("Ahora no", "Sin TorBox no se puede reproducir") { viewModel.cancelarVinculo(); avanzar() }
                        }
                        vinculacion?.let { PanelVinculo(it) }
                        if (vinculacion == null && torbox == EstadoTorbox.Caducada) {
                            Text("La cuenta que hay no tiene la suscripción activa.", color = Color.White, fontSize = EscalaTv.CUERPO)
                        }
                    }
                    PasoPrimerArranque.TRAKT -> {
                        val t = trakt
                        if (t != null) {
                            Fila("Siguiente", "Conectado · $t", Modifier.focusRequester(foco)) { avanzar() }
                        } else {
                            Fila("Conectar Trakt", "", Modifier.focusRequester(foco)) { onAbrirTrakt() }
                            Fila("Saltar", "") { avanzar() }
                        }
                    }
                    PasoPrimerArranque.FUENTES -> {
                        val fuentes: FuentesViewModel = hiltViewModel()
                        val n by fuentes.numeroFuentes.collectAsState()
                        Fila("Terminar e ir al Inicio", if ((n ?: 0) == 0) "Sin fuentes aún" else "", Modifier.focusRequester(foco)) { terminar() }
                        ContenidoFuentes(fuentes, Modifier.fillMaxWidth().padding(top = 12.dp), enfocarAlEntrar = false)
                    }
                }
            }
        }
    }
}

private fun mutableStateOfPaso() = androidx.compose.runtime.mutableStateOf(PasoPrimerArranque.BIENVENIDA)

@Composable
private fun Puntos(paso: PasoPrimerArranque) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PasoPrimerArranque.entries.forEach { p ->
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = if (p == paso) 0.95f else 0.25f)))
        }
    }
}
