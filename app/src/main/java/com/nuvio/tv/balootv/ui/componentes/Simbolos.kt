package com.nuvio.tv.balootv.ui.componentes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import com.nuvio.tv.R
import com.nuvio.tv.balootv.iconos.Pieza
import com.nuvio.tv.balootv.iconos.Simbolo

@DrawableRes
private fun Simbolo.dibujo(): Int = when (this) {
    Simbolo.ESCENA -> R.drawable.simbolo_hourglass_bottom
    Simbolo.SIN_ESCENA -> R.drawable.simbolo_hourglass_disabled
    Simbolo.TERMINA -> R.drawable.simbolo_sports_score
    Simbolo.SIGUIENTE -> R.drawable.simbolo_skip_next
    Simbolo.VISTO -> R.drawable.simbolo_check
    Simbolo.APUNTAR -> R.drawable.simbolo_bookmark_add
    Simbolo.APUNTADA -> R.drawable.simbolo_bookmark_added
    Simbolo.SALE -> R.drawable.simbolo_event
    Simbolo.AUDIO -> R.drawable.simbolo_volume_up
    Simbolo.SUBTITULOS -> R.drawable.simbolo_subtitles
    Simbolo.LISTA -> R.drawable.simbolo_bolt
    Simbolo.PREPARAR -> R.drawable.simbolo_download
    Simbolo.REPRODUCIENDO -> R.drawable.simbolo_graphic_eq
    Simbolo.PAUSA -> R.drawable.simbolo_pause
}

/** El icono que acompaña a un texto de [tamanoTexto] mide lo mismo que él (regla 8 de la propuesta de iconos). */
fun tamanoIcono(tamanoTexto: TextUnit): Dp = (tamanoTexto.value * 1.18f).dp

/** Un icono del vocabulario, en el color del texto al que acompaña (sin [color], el del contenido). */
@Composable
fun Icono(simbolo: Simbolo, tamano: Dp, modifier: Modifier = Modifier, color: Color = Color.Unspecified, descripcion: String? = null) {
    Icon(
        painterResource(simbolo.dibujo()), contentDescription = descripcion, modifier = modifier.size(tamano),
        tint = if (color.isSpecified) color else LocalContentColor.current,
    )
}

/**
 * Insignia de formato («4K», «HDR10», «DTS»), como el distintivo del Apple TV: el texto con un borde fino de su mismo
 * color. Sobre blanco (con el foco) sale en negro sin hacer nada.
 */
@Composable
fun Insignia(texto: String, modifier: Modifier = Modifier, tamanoTexto: TextUnit = EscalaTv.PIE_2, color: Color = Color.Unspecified) {
    val tinta = if (color.isSpecified) color else LocalContentColor.current
    Text(
        texto,
        modifier.border(BorderStroke(1.dp, tinta.copy(alpha = tinta.alpha * 0.55f)), RoundedCornerShape(3.5.dp))
            .padding(horizontal = 3.5.dp, vertical = 0.5.dp),
        color = tinta, fontSize = tamanoTexto, fontWeight = FontWeight.SemiBold, lineHeight = (tamanoTexto.value * 1.15f).sp, maxLines = 1,
    )
}

/** Una línea de [piezas]: textos, iconos del tamaño del texto e insignias, con «·» entre los datos. */
@Composable
fun LineaPiezas(
    piezas: List<Pieza>,
    modifier: Modifier = Modifier,
    tamanoTexto: TextUnit = EscalaTv.PIE_2,
    peso: FontWeight = FontWeight.SemiBold,
    color: Color = Color.Unspecified,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        piezas.forEach { p ->
            when (p) {
                is Pieza.Texto -> Text(p.texto, color = color, fontSize = tamanoTexto, fontWeight = peso, maxLines = 1)
                is Pieza.Icono -> Icono(p.simbolo, tamanoIcono(tamanoTexto), color = color, descripcion = p.descripcion)
                is Pieza.Insignia -> Insignia(p.texto, tamanoTexto = tamanoTexto, color = color)
                Pieza.Separador -> Text("·", color = color, fontSize = tamanoTexto, fontWeight = peso)
            }
        }
    }
}
