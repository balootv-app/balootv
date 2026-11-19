package com.nuvio.tv.balootv.ui.ficha

import com.nuvio.tv.balootv.iconos.Pieza
import com.nuvio.tv.balootv.ui.componentes.LineaPiezas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import com.nuvio.tv.balootv.ui.componentes.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.balootv.ficha.TextosFicha
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.ui.componentes.devolverFoco
import com.nuvio.tv.balootv.ui.componentes.imagenSinVer
import androidx.compose.ui.focus.focusRestorer
import com.nuvio.tv.balootv.ui.componentes.ELEGIDO
import java.time.LocalDate

private val PILDORA = RoundedCornerShape(15.dp)
private val TARJETA = RoundedCornerShape(9.dp)

/** Pestañas de temporada en píldora (tablero 19), con el año de cada una en [anios]; los especiales (temporada 0) al final. */
@Composable
fun PestanasTemporada(temporadas: List<Int>, elegida: Int, anios: Map<Int, String>, onElegir: (Int) -> Unit) {
    val orden = temporadas.filter { it > 0 } + temporadas.filter { it <= 0 }
    LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 50.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(orden, key = { it }) { t ->
            val activa = t == elegida
            Surface(
                onClick = { onElegir(t) },
                shape = ClickableSurfaceDefaults.shape(PILDORA),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (activa) ELEGIDO else Color.White.copy(alpha = 0.08f),
                    contentColor = Color.White,
                    focusedContainerColor = Color.White, focusedContentColor = Color.Black,
                ),
                border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.16f)), shape = PILDORA)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            ) {
                Row(Modifier.padding(horizontal = 13.dp, vertical = 6.dp)) {
                    Text(if (t > 0) "Temporada $t" else "Especiales", fontSize = EscalaTv.PIE_2, fontWeight = if (activa) FontWeight.Bold else FontWeight.SemiBold)
                    anios[t]?.let { Text(" · $it", fontSize = EscalaTv.PIE_2, color = LocalContentColor.current.copy(alpha = 0.55f)) }
                }
            }
        }
    }
}

/** Episodios en tarjetas apaisadas con su progreso; OK reproduce, mantener pulsado marca visto/no visto. */
@Composable
fun FilaEpisodios(
    episodios: List<Video>,
    vistos: Set<Pair<Int, Int>>,
    progreso: Map<Pair<Int, Int>, WatchProgress>,
    /** Episodio que recupera el foco al volver del reproductor. */
    idAlVolver: String? = null,
    onVuelto: () -> Unit = {},
    onReproducir: (Video) -> Unit,
    onMarcarVisto: (Video) -> Unit,
) {
    val hoy = remember { LocalDate.now() }
    LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 50.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(episodios, key = { it.id }) { v ->
            val clave = (v.season ?: 0) to (v.episode ?: 0)
            val visto = clave in vistos
            val p = progreso[clave]
            val fraccion = when {
                visto -> 1f
                p != null && p.duration > 0 -> (p.position.toFloat() / p.duration).coerceIn(0f, 1f)
                else -> 0f
            }
            TarjetaEpisodio(v, TextosFicha.etiquetaEpisodio(v, visto, p), TextosFicha.fechaEpisodio(v.released, hoy), fraccion,
                sinVer = fraccion == 0f, { onReproducir(v) }, { onMarcarVisto(v) }, Modifier.devolverFoco(v.id == idAlVolver, onVuelto))
        }
    }
}

@Composable
private fun TarjetaEpisodio(
    v: Video, etiqueta: List<Pieza>, fecha: List<Pieza>?, fraccion: Float,
    /** Ni visto ni empezado: su imagen va difuminada. */
    sinVer: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(Modifier.width(190.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = ClickableSurfaceDefaults.shape(TARJETA),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1C1C1E), focusedContainerColor = Color(0xFF1C1C1E)),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.9f)), shape = TARJETA)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).then(modifier),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (v.thumbnail != null) {
                    AsyncImage(model = imagenSinVer(v.thumbnail, sinVer), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (fraccion > 0f) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp).height(3.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fraccion).background(Color.White, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
        LineaPiezas(etiqueta, Modifier.padding(top = 4.dp), peso = FontWeight.Bold, color = Color.White.copy(alpha = 0.55f))
        Text(v.title, fontSize = EscalaTv.PIE_2, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        fecha?.let { LineaPiezas(it, peso = FontWeight.Medium, color = Color.White.copy(alpha = 0.45f)) }
    }
}
