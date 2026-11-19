package com.nuvio.tv.balootv.ui.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import com.nuvio.tv.balootv.catalogo.Plataforma
import com.nuvio.tv.balootv.iconos.Simbolo
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.LogoPlataforma
import java.util.Locale
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import com.nuvio.tv.balootv.ui.movimiento.LocalRitmo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.nuvio.tv.balootv.ui.ficha.NavegacionFichas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.focusProperties

private val RADIO = RoundedCornerShape(7.dp)
private const val ESCALA_FOCO = 1.1f
private const val ESCALA_PULSADO = 1.03f

/** Alto del título de una fila de carteles (una línea de CUERPO): fijo, para saber dónde quedan los carteles. */
private val ALTO_TITULO_FILA = 18.dp

/** Del borde de arriba de una fila de carteles a sus carteles: el título, su margen y el de la fila. */
val CARTEL_EN_FILA = ALTO_TITULO_FILA + 8.dp + 8.dp
private val CAPSULA = RoundedCornerShape(13.dp)

/** Opacidad del título de una fila sin el foco: atenuado, para que se vea dónde estás (como en el Apple TV). */
const val TITULO_SIN_FOCO = 0.55f

/** Opacidad de lo que no tiene el foco cuando la pantalla está en reposo. */
const val ALFA_REPOSO = 0.3f

/**
 * Fondo de lo elegido sin el foco (pestaña, chip, temporada, botón principal): cristal más claro. El blanco queda
 * solo para lo enfocado, para que nunca haya dos cosas blancas a la vez.
 */
val ELEGIDO = Color.White.copy(alpha = 0.22f)

/** Marca el primer elemento de una fila para que la fila le dé el foco al entrar en ella la primera vez. */
fun Modifier.primero(indice: Int, foco: FocusRequester): Modifier = if (indice == 0) focusRequester(foco) else this
private val AMARILLO_IMDB = Color(0xFFF5C518)

/**
 * Cartel 2:3 (tableros 13–16): crece y se bordea de blanco al enfocarlo; logo de plataforma arriba.
 * El crecimiento lo hace el propio Surface, por dentro: si se escalara el cartel por fuera, las listas lo verían
 * crecer y se recolocarían mientras dura la animación (el rebote vertical al pasar carteles).
 */
@Composable
fun Cartel(
    item: MetaPreview,
    onFoco: (MetaPreview) -> Unit,
    onClick: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier,
    /** Pulsación larga: el menú de quitar, marcar como vista y añadir a Mi lista. */
    onMantener: ((MetaPreview) -> Unit)? = null,
    /** Ya vista: una marca en la esquina (la saga de la ficha). */
    vista: Boolean = false,
) {
    val ritmo = LocalRitmo.current
    var enfocado by remember { mutableStateOf(false) }
    // En reposo se apagan los carteles sin foco (despacio) y vuelven al momento con cualquier tecla.
    val apagado = ritmo.inactivo && !enfocado
    val alfa by animateFloatAsState(if (apagado) ALFA_REPOSO else 1f, tween(if (apagado) 900 else 150), label = "reposo")
    Surface(
        onClick = { onClick(item) },
        onLongClick = onMantener?.let { mantener -> { mantener(item) } },
        modifier = modifier
            .size(100.dp, 150.dp)
            .graphicsLayer { alpha = alfa }
            .onFocusChanged {
                enfocado = it.isFocused
                if (it.isFocused) onFoco(item)
            },
        shape = ClickableSurfaceDefaults.shape(RADIO),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1C1C1E), focusedContainerColor = Color(0xFF1C1C1E)),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.9f)), shape = RADIO)),
        // Se hunde un poco al pulsarlo, antes de abrir la ficha (como en el Apple TV).
        scale = ClickableSurfaceDefaults.scale(focusedScale = ESCALA_FOCO, pressedScale = ESCALA_PULSADO),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = item.poster, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (item.poster == null) {
                Text(item.name, Modifier.align(Alignment.BottomStart).padding(6.dp), fontSize = EscalaTv.PIE_2, fontWeight = FontWeight.Bold, maxLines = 3)
            }
            item.plataforma?.let { LogoPlataforma(it, Modifier.align(Alignment.TopStart).padding(5.dp).size(15.dp)) }
            if (vista) {
                Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(17.dp).background(Color.White.copy(alpha = 0.92f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icono(Simbolo.VISTO, 13.dp, color = Color.Black, descripcion = "Vista")
                }
            }
        }
    }
}

/**
 * Una fila de rejilla: [columnas] huecos fijos que no se desplazan, con la misma geometría que [FilaCarteles] (el
 * título, o su hueco si no lo hay, encima de los carteles). En una lista con [rememberFilasArriba] la fila enfocada
 * sube entera con su título y la de encima desaparece entera, sin asomar cortada bajo lo que haya arriba.
 */
@Composable
fun <T> FilaRejilla(titulo: String?, items: List<T>, columnas: Int, celda: @Composable (T) -> Unit) {
    var conFoco by remember { mutableStateOf(false) }
    val alfaTitulo by animateFloatAsState(if (conFoco) 1f else TITULO_SIN_FOCO, tween(200), label = "tituloRejilla")
    Column(Modifier.fillMaxWidth().onFocusChanged { conFoco = it.hasFocus }) {
        Text(
            titulo.orEmpty(), Modifier.padding(start = 50.dp, bottom = 8.dp).height(ALTO_TITULO_FILA).graphicsLayer { alpha = alfaTitulo },
            fontSize = EscalaTv.CUERPO, fontWeight = FontWeight.Bold, color = Color.White,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 50.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(17.dp)) {
            repeat(columnas) { i -> Box(Modifier.weight(1f)) { items.getOrNull(i)?.let { celda(it) } } }
        }
    }
}

/** Una fila de carteles con su título. */
@Composable
fun FilaCarteles(
    titulo: String,
    items: List<MetaPreview>,
    onFoco: (MetaPreview) -> Unit,
    onClick: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier,
    idAlVolver: String? = null,
    onVuelto: () -> Unit = {},
    onMantener: ((MetaPreview) -> Unit)? = null,
    /** El nombre bajo cada cartel, donde no hay cabecera que diga cuál es el enfocado («Más como esta»). */
    conTitulos: Boolean = false,
    vista: (MetaPreview) -> Boolean = { false },
) {
    var conFoco by remember { mutableStateOf(false) }
    val ritmo = LocalRitmo.current
    val alfaTitulo by animateFloatAsState(
        when {
            ritmo.inactivo -> ALFA_REPOSO
            conFoco -> 1f
            else -> TITULO_SIN_FOCO
        },
        tween(if (ritmo.inactivo) 900 else 200), label = "tituloFila",
    )
    Column(modifier.fillMaxWidth().onFocusChanged { conFoco = it.hasFocus }) {
        Text(
            titulo, Modifier.padding(start = 50.dp, bottom = 8.dp).height(ALTO_TITULO_FILA).graphicsLayer { alpha = alfaTitulo },
            fontSize = EscalaTv.CUERPO, fontWeight = FontWeight.Bold, color = Color.White,
        )
        // Al entrar en la fila desde arriba o abajo se cae en el último cartel enfocado en ella; la primera vez, en el
        // primero (no en el que quede debajo). Se guarda aparte del focusRestorer: el Inicio deja de pintar la fila al
        // subir a la cabecera y con ella se perdía el recuerdo, así que al bajar se caía en otro cartel.
        var ultimo by rememberSaveable { mutableStateOf<String?>(null) }
        val focos = remember { HashMap<String, FocusRequester>() }
        LazyRow(
            Modifier
                .focusProperties {
                    onEnter = {
                        val destino = ultimo?.let(focos::get) ?: items.firstOrNull()?.let { focos[clave(it)] }
                        if (destino != null) runCatching { destino.requestFocus() }
                    }
                }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = 50.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            items(items, key = ::clave) {
                val k = clave(it)
                val foco = remember { FocusRequester() }
                DisposableEffect(k) {
                    focos[k] = foco
                    onDispose { if (focos[k] === foco) focos.remove(k) }
                }
                val cartel = @Composable {
                    Cartel(it, { enfocado -> ultimo = k; onFoco(enfocado) }, { abierto ->
                        NavegacionFichas.actual.abrir(abierto)
                        onClick(abierto)
                    }, Modifier.focusRequester(foco).devolverFoco(it.id == idAlVolver, onVuelto), onMantener, vista(it))
                }
                if (!conTitulos) {
                    cartel()
                } else {
                    Column(Modifier.width(100.dp)) {
                        cartel()
                        // Con hueco arriba: el cartel crece con el foco y no debe tapar el nombre.
                        Text(
                            it.name, Modifier.padding(top = 12.dp), fontSize = EscalaTv.PIE_2, fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

val ALTO_CABECERA_ENFOCADA = 150.dp

private fun clave(item: MetaPreview) = "${item.apiType}:${item.id}"

/** Píldora amarilla con la nota IMDb (formato español: 8,5). */
@Composable
fun NotaImdbEtiqueta(nota: Float, modifier: Modifier = Modifier) {
    Text(
        "IMDb " + String.format(Locale("es", "ES"), "%.1f", nota),
        modifier.background(AMARILLO_IMDB, RoundedCornerShape(3.dp)).padding(horizontal = 4.5.dp, vertical = 1.5.dp),
        color = Color.Black, fontSize = EscalaTv.PIE_2, fontWeight = FontWeight.Bold,
    )
}

/**
 * Información del título enfocado (tablero 13): el logo de su plataforma (sin «En Prime Video», propuesta de iconos T6),
 * título, nota IMDb, año y sinopsis. Se reserva siempre su alto para que las filas no salten al cambiar de título.
 */
@Composable
fun CabeceraEnfocada(item: MetaPreview?, nota: Float?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().height(ALTO_CABECERA_ENFOCADA).padding(start = 50.dp, end = 400.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (item == null) return@Column
        item.plataforma?.let { LogoPlataforma(it, Modifier.size(22.dp)) }
        Text(item.name, fontSize = EscalaTv.TITULO_2, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // Alto fijo: la nota IMDb llega 400 ms después y es algo más alta que el año; sin esto la sinopsis bajaba y
        // subía unos píxeles en cada cartel.
        Row(Modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            nota?.let { NotaImdbEtiqueta(it) }
            item.releaseInfo?.let { Text(it, fontSize = EscalaTv.PIE_2, color = Color.White.copy(alpha = 0.8f)) }
        }
        item.description?.let {
            Text(it, fontSize = EscalaTv.PIE_2, color = Color.White.copy(alpha = 0.78f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Velo del fondo del título enfocado: oscuro a la izquierda (donde va el texto) y abajo. */
val VELO_ENFOCADO = veloDoble(
    arrayOf(0f to Color.Black.copy(alpha = 0.9f), 0.4f to Color.Black.copy(alpha = 0.6f), 0.75f to Color.Transparent),
    arrayOf(0.45f to Color.Transparent, 1f to Color.Black),
)

/** Fondo del título enfocado, arriba a la derecha y fundido a negro (sin desenfoque en tiempo real). */
@Composable
fun FondoEnfocado(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(340.dp)) {
        FondoFundido(url, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(VELO_ENFOCADO))
    }
}

@Immutable
data class OpcionChip(val nombre: String, val plataforma: Plataforma?)

/** Chips de cristal para filtrar por plataforma (tablero 14). */
@Composable
fun ChipsPlataforma(plataformas: List<Plataforma>, elegida: Plataforma?, onElegir: (Plataforma?) -> Unit, modifier: Modifier = Modifier) {
    val opciones = listOf(OpcionChip("Todas", null)) + plataformas.map { OpcionChip(it.nombre, it) }
    // Al entrar en los chips, la primera vez el foco cae en el elegido.
    val elegido = remember { FocusRequester() }
    LazyRow(modifier.focusRestorer(elegido), contentPadding = PaddingValues(horizontal = 50.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(opciones, key = { it.nombre }) { o ->
            val activa = o.plataforma == elegida
            Surface(
                onClick = { onElegir(o.plataforma) },
                modifier = if (activa) Modifier.focusRequester(elegido) else Modifier,
                shape = ClickableSurfaceDefaults.shape(CAPSULA),
                // Blanco solo con el foco; el elegido, en cristal más claro (si no, parecían dos enfocados).
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (activa) ELEGIDO else Color.White.copy(alpha = 0.08f),
                    contentColor = Color.White,
                    focusedContainerColor = Color.White, focusedContentColor = Color.Black,
                ),
                border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.16f)), shape = CAPSULA)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            ) {
                Text(o.nombre, Modifier.padding(horizontal = 11.dp, vertical = 5.5.dp), fontSize = EscalaTv.PIE_2,
                    fontWeight = if (activa) FontWeight.Bold else FontWeight.SemiBold)
            }
        }
    }
}
