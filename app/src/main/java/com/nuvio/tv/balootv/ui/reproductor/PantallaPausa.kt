package com.nuvio.tv.balootv.ui.reproductor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.tv.balootv.reproductor.AccionesContextuales
import com.nuvio.tv.balootv.iconos.Simbolo
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.ui.componentes.Icono
import com.nuvio.tv.balootv.ui.componentes.Text
import com.nuvio.tv.balootv.ui.componentes.veloDoble
import java.time.format.DateTimeFormatter

/** Velo de la pausa: oscurece abajo a la izquierda, donde va el texto, y deja ver la imagen arriba a la derecha. */
private val VELO_PAUSA = veloDoble(
    arrayOf(0f to Color.Black.copy(alpha = 0.78f), 0.5f to Color.Black.copy(alpha = 0.45f), 0.85f to Color.Black.copy(alpha = 0.15f)),
    arrayOf(0f to Color.Black.copy(alpha = 0.25f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f)),
)
private val HORA = DateTimeFormatter.ofPattern("H:mm")

/**
 * Lo que se ve a los 5 s de pausar, como en el Apple TV: la imagen congelada con un velo, la hora arriba a la derecha y
 * abajo qué estás viendo, cuánto queda y de qué va. No tiene nada enfocable: OK sigue viendo y cualquier otra tecla la
 * quita (lo gestiona el reproductor).
 */
@Composable
fun PantallaPausa(
    visible: Boolean,
    titulo: String,
    logo: String?,
    temporada: Int?,
    episodio: Int?,
    tituloEpisodio: String?,
    anio: String?,
    sinopsis: String?,
    queda: () -> String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600)),
        exit = fadeOut(tween(250)),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize().background(VELO_PAUSA)) {
            Reloj(Modifier.align(Alignment.TopEnd).padding(top = 34.dp, end = 50.dp))
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 50.dp, end = 50.dp, bottom = 56.dp)
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icono(Simbolo.PAUSA, 22.dp, color = Color.White.copy(alpha = 0.6f), descripcion = "En pausa")
                var sinLogo by remember(logo) { mutableStateOf(logo.isNullOrBlank()) }
                if (!sinLogo) {
                    AsyncImage(
                        model = logo, contentDescription = titulo, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                        modifier = Modifier.heightIn(max = 80.dp).widthIn(max = 380.dp),
                        onError = { sinLogo = true },
                    )
                } else {
                    Text(titulo, fontSize = EscalaTv.TITULO_2, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val esEpisodio = temporada != null && episodio != null
                if (esEpisodio) {
                    Text(
                        AccionesContextuales.episodio(temporada!!, episodio!!, tituloEpisodio),
                        fontSize = EscalaTv.LLAMADA, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                val linea = listOfNotNull(anio?.takeIf { !esEpisodio && it.isNotBlank() }, queda().takeIf { it.isNotBlank() }).joinToString(" · ")
                if (linea.isNotEmpty()) Text(linea, fontSize = EscalaTv.PIE_1, color = Color.White.copy(alpha = 0.75f))
                sinopsis?.takeIf { it.isNotBlank() }?.let(com.nuvio.tv.balootv.ficha.SinopsisSinAnuncio::limpiar)?.let {
                    Text(it, fontSize = EscalaTv.CUERPO, color = Color.White.copy(alpha = 0.82f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                Text("Pulsa OK para seguir viendo", Modifier.padding(top = 6.dp), fontSize = EscalaTv.PIE_2, color = Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

/** La hora en grande, como la del Apple TV; se actualiza al cambiar de minuto. */
@Composable
private fun Reloj(modifier: Modifier = Modifier) {
    val ahora = com.nuvio.tv.balootv.ui.componentes.rememberHoraAlMinuto()
    Text(ahora.format(HORA), modifier, fontSize = EscalaTv.TITULO_3, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.92f))
}
