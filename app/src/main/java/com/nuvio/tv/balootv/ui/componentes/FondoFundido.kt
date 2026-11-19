package com.nuvio.tv.balootv.ui.componentes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fondo que se funde al cambiar de imagen, como en el Apple TV: la nueva aparece encima de la anterior cuando ya ha
 * cargado (no hay fogonazo negro entre las dos). Mientras no hay ninguna, se ve [colorEspera] (el color dominante del
 * cartel, por ejemplo). Solo durante el fundido se dibujan dos imágenes. Sin imagen (null), la que había se funde a
 * negro.
 */
@Composable
fun FondoFundido(url: String?, modifier: Modifier = Modifier, colorEspera: Color = Color.Transparent) {
    var debajo by remember { mutableStateOf<String?>(null) }
    var encima by remember { mutableStateOf(url) }
    val alfa = remember { Animatable(0f) }
    val salida = remember { Animatable(1f) }
    var cargada by remember { mutableStateOf(false) }
    var pedidaEn by remember { mutableStateOf(System.nanoTime()) }
    if (url != encima) {
        // Lo que se veía pasa abajo; la nueva entra transparente y se funde cuando llega.
        debajo = encima.takeIf { cargada } ?: debajo
        encima = url
        cargada = false
        pedidaEn = System.nanoTime()
    }
    LaunchedEffect(encima, cargada) {
        if (encima == null) {
            salida.snapTo(1f)
            if (debajo != null) salida.animateTo(0f, tween(DURACION_MS))
            debajo = null
        } else if (!cargada) {
            alfa.snapTo(0f)
        } else {
            // Sin nada debajo y servida desde memoria (precargada): se pone sin fundido, para no pasar por negro.
            val alInstante = debajo == null && (System.nanoTime() - pedidaEn) / 1_000_000 < AL_INSTANTE_MS
            if (alInstante) alfa.snapTo(1f) else alfa.animateTo(1f, tween(DURACION_MS))
            debajo = null
        }
    }
    Box(modifier.background(colorEspera)) {
        debajo?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (encima == null) salida.value else 1f })
        }
        if (encima != null) {
            AsyncImage(
                model = encima, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = alfa.value },
                onSuccess = { cargada = true },
            )
        }
    }
}

/** Color medio de una imagen (pedida a 8 × 8 px), para ocupar el sitio mientras carga la grande. */
@Composable
fun colorDominante(url: String?): Color {
    val contexto = LocalContext.current
    val color by produceState(Color.Transparent, url) {
        if (url == null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val r = SingletonImageLoader.get(contexto).execute(
                    ImageRequest.Builder(contexto).data(url).size(8, 8).allowHardware(false).build()
                ) as? SuccessResult ?: return@runCatching Color.Transparent
                val b = r.image.toBitmap()
                var rr = 0L; var gg = 0L; var bb = 0L
                val n = b.width * b.height
                for (y in 0 until b.height) for (x in 0 until b.width) {
                    val p = b.getPixel(x, y)
                    rr += (p shr 16) and 0xFF; gg += (p shr 8) and 0xFF; bb += p and 0xFF
                }
                // Algo más oscuro que la media, para que el texto blanco se lea sobre él.
                Color(red = (rr / n) / 255f * 0.7f, green = (gg / n) / 255f * 0.7f, blue = (bb / n) / 255f * 0.7f)
            }.getOrDefault(Color.Transparent)
        }
    }
    return color
}

private const val DURACION_MS = 450
private const val AL_INSTANTE_MS = 150

