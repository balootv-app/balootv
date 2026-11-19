package com.nuvio.tv.balootv.ui.fuentes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nuvio.tv.R
import com.nuvio.tv.balootv.ajustes.FONDO
import com.nuvio.tv.balootv.ajustes.Fila
import com.nuvio.tv.balootv.ajustes.FilaAddon
import com.nuvio.tv.balootv.fuentes.Comprobacion
import com.nuvio.tv.balootv.fuentes.ComprobacionFuente
import com.nuvio.tv.balootv.fuentes.GuiaFuentes
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.ui.componentes.Text

/** Cómo se añade una fuente (nunca cuáles: BalooTV no recomienda ninguna). */
const val GUIA_FUENTES = "Una fuente es la dirección de un addon de Stremio (acaba en manifest.json). Escanea el código " +
    "con el móvil y pega ahí una o varias, una por línea. Si la dirección lleva tu clave de TorBox, puedes poner " +
    "{torbox} en su lugar. La tele comprueba cada una antes de añadirla."

/** «¿No sabes qué poner? Mira la guía: …», bajo la explicación de las fuentes (aquí y en el primer arranque). */
@Composable
fun LineaGuiaFuentes(modifier: Modifier = Modifier) {
    Text(GuiaFuentes.linea, modifier, color = Color.White, fontSize = EscalaTv.CUERPO, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
}

/** La pantalla «Fuentes», desde Ajustes o desde una ficha sin fuentes. */
@Composable
fun FuentesScreen(onVolver: () -> Unit, viewModel: FuentesViewModel = hiltViewModel()) {
    BackHandler { onVolver() }
    Box(Modifier.fillMaxSize().background(Color.Black).background(FONDO)) {
        Text(
            "Fuentes",
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
                Text(GUIA_FUENTES, color = Color.White.copy(alpha = 0.65f), fontSize = EscalaTv.CUERPO, textAlign = TextAlign.Center)
                LineaGuiaFuentes()
            }
            Spacer(Modifier.width(60.dp))
            ContenidoFuentes(viewModel, Modifier.fillMaxWidth())
        }
    }
}

/**
 * Añadir (QR para el móvil y escribir con el mando), lo que pasó con lo último que se envió y la lista de fuentes
 * para quitarlas. Lo usa también el primer arranque. La página del móvil vive mientras esto se ve.
 */
@Composable
fun ContenidoFuentes(viewModel: FuentesViewModel, modifier: Modifier = Modifier, enfocarAlEntrar: Boolean = true) {
    val filas by viewModel.filas.collectAsState()
    val numero by viewModel.numeroFuentes.collectAsState()
    val enlace by viewModel.enlace.collectAsState()
    val sinRed by viewModel.sinRed.collectAsState()
    val resultados by viewModel.resultados.collectAsState()
    val comprobando by viewModel.comprobando.collectAsState()
    var escribiendo by remember { mutableStateOf(false) }
    var texto by remember { mutableStateOf("") }
    var quitando by remember { mutableStateOf<String?>(null) }
    val primera = remember { FocusRequester() }
    val campo = remember { FocusRequester() }

    DisposableEffect(Unit) {
        viewModel.abrirPagina()
        onDispose { viewModel.cerrarPagina() }
    }
    LaunchedEffect(Unit) { if (enfocarAlEntrar) runCatching { primera.requestFocus() } }
    LaunchedEffect(escribiendo) { if (escribiendo) runCatching { campo.requestFocus() } }

    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                val e = enlace
                if (e != null) {
                    Image(e.qr.asImageBitmap(), contentDescription = "Código QR para añadir fuentes desde el móvil", modifier = Modifier.size(170.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Escanea el código con el móvil (en la misma red que la tele) o entra en:", color = Color.White.copy(alpha = 0.75f), fontSize = EscalaTv.CUERPO)
                        Text(e.direccion, color = Color.White, fontSize = EscalaTv.LLAMADA, fontWeight = FontWeight.SemiBold)
                        Text("El código vale para un envío; luego sale otro.", color = Color.White.copy(alpha = 0.55f), fontSize = EscalaTv.PIE_1)
                    }
                } else {
                    Text(
                        if (sinRed) "La tele no tiene red local: escribe la dirección con el mando." else "Preparando el código para el móvil…",
                        color = Color.White.copy(alpha = 0.75f), fontSize = EscalaTv.CUERPO,
                    )
                }
            }
        }
        item {
            Fila("Escribir con el mando", "", Modifier.focusRequester(primera)) { escribiendo = !escribiendo }
        }
        if (escribiendo) {
            item {
                BasicTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = EscalaTv.CUERPO),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { if (texto.isNotBlank()) { viewModel.anadir(texto); texto = "" } }),
                    modifier = Modifier.fillMaxWidth().focusRequester(campo)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
                        .padding(horizontal = 17.dp, vertical = 13.dp),
                    decorationBox = { interior ->
                        if (texto.isEmpty()) Text("https://…/manifest.json", color = Color.White.copy(alpha = 0.4f), fontSize = EscalaTv.CUERPO)
                        interior()
                    },
                )
            }
            item { Fila("Comprobar y añadir", "") { if (texto.isNotBlank()) { viewModel.anadir(texto); texto = "" } } }
        }
        if (comprobando) item { Aviso("Comprobando…") }
        items(resultados) { r ->
            Aviso("${if (r.comprobacion is Comprobacion.Valida) "✓" else "✕"}  ${r.sitio}: ${ComprobacionFuente.texto(r.comprobacion)}")
        }
        item {
            val n = numero
            Text(
                when (n) { null -> ""; 0 -> "Instalado · aún sin fuentes de enlaces"; 1 -> "Instalado · 1 fuente de enlaces"; else -> "Instalado · $n fuentes de enlaces" },
                color = Color.White.copy(alpha = 0.6f), fontSize = EscalaTv.PIE_1,
                modifier = Modifier.padding(top = 18.dp, start = 4.dp, bottom = 2.dp),
            )
        }
        items(filas.orEmpty(), key = { it.baseUrl.ifBlank { it.nombre } }) { f ->
            FilaFuente(f, confirmando = quitando == f.baseUrl, onFoco = { if (quitando != f.baseUrl) quitando = null }) {
                if (quitando == f.baseUrl) { viewModel.quitar(f); quitando = null } else quitando = f.baseUrl
            }
        }
    }
}

@Composable
private fun FilaFuente(f: FilaAddon, confirmando: Boolean, onFoco: () -> Unit, onClick: () -> Unit) {
    Fila(
        if (confirmando) "Pulsa otra vez para quitar ${f.nombre}" else f.nombre,
        if (confirmando) "" else f.papel,
        Modifier.onFocusChanged { if (it.isFocused) onFoco() },
        onClick,
    )
}

@Composable
private fun Aviso(texto: String) {
    Text(texto, color = Color.White.copy(alpha = 0.85f), fontSize = EscalaTv.CUERPO, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}
