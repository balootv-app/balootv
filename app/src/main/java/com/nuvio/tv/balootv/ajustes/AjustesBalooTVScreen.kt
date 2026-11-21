package com.nuvio.tv.balootv.ajustes

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.nuvio.tv.balootv.ui.componentes.Text
import com.nuvio.tv.R
import com.nuvio.tv.balootv.cuenta.PasoTrakt
import com.nuvio.tv.balootv.cuenta.TextosTrakt
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.balootv.ui.componentes.EscalaTv

/** Atribución que exige TMDB. */
const val TEXTO_TMDB = "Este producto usa la API de TMDB, pero TMDB no lo respalda ni lo certifica. Fichas, carteles y fondos: The Movie Database (TMDB)."

/** El aviso que Trakt pide a las apps que usan su API. */
const val TEXTO_TRAKT = "BalooTV usa Trakt, pero no está avalada ni certificada por Trakt."

/** La frase exacta que pide Trakt, en inglés. */
const val TEXTO_TRAKT_EN = "BalooTV uses Trakt but is not endorsed or certified by Trakt."

internal val FONDO = Brush.radialGradient(listOf(Color(0xFF1F2B2E), Color(0xFF0D1012), Color.Black), radius = 1400f)
private val FORMA = RoundedCornerShape(10.dp)

/**
 * Ajustes de BalooTV (tablero 10): el icono y la explicación de la fila enfocada a la izquierda; las filas
 * a la derecha. Al entrar en una fila, su detalle sustituye a la lista; Atrás vuelve.
 */
@Composable
fun AjustesBalooTVScreen(
    onAbrirFuentes: () -> Unit,
    onRepetirPrimerosPasos: () -> Unit,
    onVolver: () -> Unit,
    viewModel: AjustesBalooTVViewModel = hiltViewModel(),
    actualizacion: com.nuvio.tv.balootv.actualizador.ActualizacionViewModel = hiltViewModel(),
) {
    var avisoActualizacion by remember { mutableStateOf("") }
    val entrada by viewModel.entrada.collectAsState()
    val plataformas by viewModel.plataformasConEstado.collectAsState()
    val vinculacion by viewModel.vinculacion.collectAsState()
    val vinculacionTrakt by viewModel.vinculacionTrakt.collectAsState()
    var abierta by remember { mutableStateOf<SeccionAjustes?>(null) }
    var enfocada by remember { mutableStateOf(SeccionAjustes.TRAKT) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var confirmarDesconexion by remember { mutableStateOf(false) }
    val primera = remember { FocusRequester() }

    BackHandler {
        if (abierta != null) { abierta = null; confirmarDesconexion = false; viewModel.cancelarVinculo() } else onVolver()
    }
    // También al conectar Trakt: la fila enfocada («Pedir otro código») desaparece y la que queda es «Cuenta».
    LaunchedEffect(abierta, entrada != null, entrada?.usuarioTrakt) { if (entrada != null) runCatching { primera.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color.Black).background(FONDO)) {
        Text(
            abierta?.titulo ?: "Ajustes",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
            color = Color.White, fontSize = EscalaTv.TITULAR, fontWeight = FontWeight.SemiBold,
        )
        Row(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 100.dp)) {
            Column(
                Modifier.width(350.dp).padding(top = 25.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Image(
                    painterResource(R.drawable.balootv_banner),
                    contentDescription = "BalooTV",
                    modifier = Modifier.size(280.dp, 158.dp).clip(RoundedCornerShape(15.dp)),
                )
                Text(
                    (abierta ?: enfocada).explicacion,
                    color = Color.White.copy(alpha = 0.65f), fontSize = EscalaTv.CUERPO, textAlign = TextAlign.Center,
                )
                aviso?.let { Text(it, color = Color.White, fontSize = EscalaTv.CUERPO, textAlign = TextAlign.Center) }
            }
            Spacer(Modifier.width(60.dp))
            val e = entrada ?: return@Row
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                when (abierta) {
                    null -> items(ResumenAjustes.filas(e), key = { it.seccion }) { f ->
                        Fila(
                            f.seccion.titulo, f.valor,
                            modifier = (if (f.seccion == SeccionAjustes.TRAKT) Modifier.focusRequester(primera) else Modifier)
                                .onFocusChanged { if (it.isFocused) enfocada = f.seccion },
                        ) {
                            // Trakt sin cuenta: se pide el código al entrar, como TorBox (nada de la pantalla heredada).
                            when {
                                f.seccion == SeccionAjustes.TRAKT && e.usuarioTrakt == null -> { abierta = f.seccion; viewModel.vincularTrakt() }
                                f.seccion == SeccionAjustes.FUENTES -> onAbrirFuentes()
                                else -> abierta = f.seccion
                            }
                        }
                    }
                    SeccionAjustes.FUENTE -> {
                        item { Opcion("BalooTV automático", e.modo != com.nuvio.tv.data.local.StreamAutoPlayMode.MANUAL, Modifier.focusRequester(primera)) { viewModel.elegirModo(false) } }
                        item { Opcion("Preguntarme siempre", e.modo == com.nuvio.tv.data.local.StreamAutoPlayMode.MANUAL) { viewModel.elegirModo(true) } }
                    }
                    SeccionAjustes.SUBTITULOS -> {
                        items(TamanoSubtitulos.entries.toList()) { t ->
                            Opcion("Tamaño ${t.nombre.lowercase()}", e.tamanoSubtitulos == t.valor, if (t == TamanoSubtitulos.PEQUENO) Modifier.focusRequester(primera) else Modifier) { viewModel.elegirTamano(t) }
                        }
                        items(AlturaSubtitulos.entries.toList()) { a ->
                            Opcion("Posición: ${a.nombre}", (e.alturaSubtitulos > AlturaSubtitulos.ABAJO.valor) == (a == AlturaSubtitulos.ARRIBA)) { viewModel.elegirAltura(a) }
                        }
                    }
                    SeccionAjustes.TORBOX -> {
                        item { Fila("Estado", ResumenAjustes.filas(e).first { it.seccion == SeccionAjustes.TORBOX }.valor, Modifier.focusRequester(primera)) { viewModel.comprobarTorbox() } }
                        item { Fila("Usar otra cuenta", "") { viewModel.vincularTorbox() } }
                        vinculacion?.let { v -> item { PanelVinculo(v) } }
                    }
                    SeccionAjustes.PLATAFORMAS -> items(plataformas, key = { it.first.id }) { (p, activa) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Opcion(p.nombre, activa, (if (p == plataformas.first().first) Modifier.focusRequester(primera) else Modifier).weight(1f)) { viewModel.alternarPlataforma(p) }
                            if (activa) Fila("Subir", "", Modifier.width(110.dp)) { viewModel.subirPlataforma(p) }
                        }
                    }
                    SeccionAjustes.FUENTES -> Unit
                    SeccionAjustes.ACERCA_DE -> {
                        item { Fila("Versión", "BalooTV ${e.version}", Modifier.focusRequester(primera)) {} }
                        item { Fila("Borrar caché", "") { viewModel.borrarCache { aviso = "Caché borrada" } } }
                        item { Fila("Repetir los primeros pasos", "TorBox, Trakt y fuentes") { onRepetirPrimerosPasos() } }
                        if (actualizacion.activo) item {
                            val estadoAct by actualizacion.estado.collectAsState()
                            val disponible = (estadoAct as? com.nuvio.tv.balootv.actualizador.EstadoActualizacion.Disponible)?.a
                            Fila(
                                if (disponible != null) "Actualizar a ${disponible.version}" else "Buscar actualizaciones",
                                avisoActualizacion,
                            ) {
                                if (disponible != null) actualizacion.actualizar(disponible)
                                else { avisoActualizacion = "Buscando…"; actualizacion.comprobar(aunqueAplazada = true) { avisoActualizacion = if (it == null) "Tienes la última" else "" } }
                            }
                        }
                        item { Text(stringResource(R.string.about_gpl_attribution), color = Color.White.copy(alpha = 0.5f), fontSize = EscalaTv.PIE_1, modifier = Modifier.padding(top = 12.dp, start = 4.dp)) }
                        // TMDB pide su aviso en la app que usa su API (https://www.themoviedb.org/api-terms-of-use).
                        item { Text(TEXTO_TMDB, color = Color.White.copy(alpha = 0.5f), fontSize = EscalaTv.PIE_1, modifier = Modifier.padding(top = 6.dp, start = 4.dp)) }
                        item { Text(TEXTO_TRAKT, color = Color.White.copy(alpha = 0.5f), fontSize = EscalaTv.PIE_1, modifier = Modifier.padding(top = 6.dp, start = 4.dp)) }
                        item { Text(TEXTO_TRAKT_EN, color = Color.White.copy(alpha = 0.5f), fontSize = EscalaTv.PIE_1, modifier = Modifier.padding(start = 4.dp)) }
                    }
                    SeccionAjustes.TRAKT -> if (e.usuarioTrakt == null) {
                        item {
                            Fila(if (vinculacionTrakt is PasoTrakt.Fallo) "Volver a intentarlo" else "Pedir otro código", "", Modifier.focusRequester(primera)) {
                                viewModel.vincularTrakt()
                            }
                        }
                        vinculacionTrakt?.let { p -> item { PanelTrakt(p) } }
                    } else {
                        item { Fila("Cuenta", e.usuarioTrakt, Modifier.focusRequester(primera)) {} }
                        item {
                            Fila(if (confirmarDesconexion) "Pulsa otra vez para desconectar" else "Desconectar", "") {
                                if (!confirmarDesconexion) confirmarDesconexion = true
                                else viewModel.desconectarTrakt { confirmarDesconexion = false; abierta = null; aviso = "Trakt desconectado" }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Fila de cristal: blanca y algo más grande al enfocarla, como en los Ajustes de tvOS. */
@Composable
internal fun Fila(titulo: String, valor: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(FORMA),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.07f), contentColor = Color.White,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
        ),
        border = ClickableSurfaceDefaults.border(border = Border(androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.10f)), shape = FORMA)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(titulo, fontSize = EscalaTv.CUERPO, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (valor.isNotEmpty()) Text(valor, fontSize = EscalaTv.CUERPO, color = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** Opción de una lista de elección: marca a la derecha si es la actual. */
@Composable
private fun Opcion(titulo: String, marcada: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Fila(titulo, if (marcada) "✓" else "", modifier, onClick)

/** El código de Trakt para aprobar desde el móvil (o su QR), y luego cómo ha ido. */
@Composable
internal fun PanelTrakt(p: PasoTrakt) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        when (p) {
            PasoTrakt.Pidiendo -> Text("Pidiendo un código a Trakt…", color = Color.White, fontSize = EscalaTv.CUERPO)
            is PasoTrakt.Codigo -> {
                val qr = remember(p.direccion) { QrCodeGenerator.generate("https://${p.direccion}", 360) }
                Image(qr.asImageBitmap(), contentDescription = "Código QR para vincular Trakt", modifier = Modifier.size(180.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("En el móvil, entra en ${p.direccion} con tu cuenta de Trakt y escribe:", color = Color.White.copy(alpha = 0.75f), fontSize = EscalaTv.CUERPO)
                    Text(p.codigo, color = Color.White, fontSize = EscalaTv.TITULAR, fontWeight = FontWeight.SemiBold)
                    Text("O escanea el código QR. La tele se vincula sola al aprobarlo.", color = Color.White.copy(alpha = 0.6f), fontSize = EscalaTv.CUERPO)
                }
            }
            is PasoTrakt.Hecho -> Text("Listo: Trakt conectado${p.usuario?.let { " como $it" }.orEmpty()}.", color = Color.White, fontSize = EscalaTv.CUERPO)
            is PasoTrakt.Fallo -> Text(p.motivo, color = Color.White, fontSize = EscalaTv.CUERPO)
        }
    }
}

/** El código de TorBox para aprobar desde el móvil (o su QR), y luego cómo ha ido. */
@Composable
internal fun PanelVinculo(v: Vinculacion) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        when (v) {
            Vinculacion.Pidiendo -> Text("Pidiendo un código a TorBox…", color = Color.White, fontSize = EscalaTv.CUERPO)
            is Vinculacion.Codigo -> {
                Image(v.qr.asImageBitmap(), contentDescription = "Código QR para vincular TorBox", modifier = Modifier.size(180.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("En el móvil, entra en ${v.paso.direccion} con tu cuenta de TorBox y escribe:", color = Color.White.copy(alpha = 0.75f), fontSize = EscalaTv.CUERPO)
                    Text(v.paso.codigo, color = Color.White, fontSize = EscalaTv.TITULAR, fontWeight = FontWeight.SemiBold)
                    Text("O escanea el código QR. La tele se vincula sola al aprobarlo.", color = Color.White.copy(alpha = 0.6f), fontSize = EscalaTv.CUERPO)
                }
            }
            Vinculacion.Hecha -> Text("Listo: BalooTV ya usa tu cuenta de TorBox.", color = Color.White, fontSize = EscalaTv.CUERPO)
            is Vinculacion.Fallo -> Text(v.motivo, color = Color.White, fontSize = EscalaTv.CUERPO)
        }
    }
}
