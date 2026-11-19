package com.nuvio.tv.balootv.ui.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.remember
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import com.nuvio.tv.balootv.iconos.Pieza
import com.nuvio.tv.balootv.iconos.Simbolo

/** Cristal de tvOS sin desenfoque (Android 10 no lo tiene): gris translúcido, borde de luz de 1 px. */
val CristalFondo = Color(0xFF242428).copy(alpha = 0.78f)
val CristalBorde = Color.White.copy(alpha = 0.16f)

fun Modifier.cristal(forma: Shape): Modifier =
    background(CristalFondo, forma).border(BorderStroke(0.5.dp, CristalBorde), forma)

/**
 * Cristal «esmerilado» para paneles grandes sobre una imagen conocida (fase E): la misma imagen pedida a 48 × 27 px y
 * ampliada hace de desenfoque (Android 10 no lo tiene en tiempo real) y se oscurece encima. Se decodifica una vez y
 * no cuesta nada por fotograma. Sin imagen, es el cristal de siempre.
 */
@Composable
fun FondoEsmerilado(imagen: String?, forma: Shape, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    Box(modifier.clip(forma).background(Color(0xFF1C1C20))) {
        if (imagen != null) {
            val peticion = remember(imagen) {
                ImageRequest.Builder(contexto).data(imagen).size(48, 27).precision(Precision.EXACT).build()
            }
            AsyncImage(
                model = peticion, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(Modifier.matchParentSize().background(Color(0xFF18181C).copy(alpha = if (imagen != null) 0.62f else 0.78f)).border(BorderStroke(0.5.dp, CristalBorde), forma))
    }
}

/**
 * Píldora de cristal que al enfocarse se vuelve blanca (tableros 18–22). Debajo del texto, [detalle] o, si las hay,
 * [piezas] con iconos e insignias; delante del texto, [icono].
 */
@Composable
fun BotonCristal(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detalle: String? = null,
    principal: Boolean = false,
    radio: Dp = 17.dp,
    relleno: PaddingValues = PaddingValues(horizontal = 17.dp, vertical = 9.dp),
    piezas: List<Pieza> = emptyList(),
    icono: Simbolo? = null,
) {
    val forma = RoundedCornerShape(radio)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(forma),
        colors = ClickableSurfaceDefaults.colors(
            // Blanco solo con el foco: el principal se distingue con un cristal más claro.
            containerColor = if (principal) ELEGIDO else Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(0.5.dp, if (principal) Color.Transparent else CristalBorde), shape = forma)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Column(Modifier.padding(relleno), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            if (icono == null) {
                Text(texto, fontSize = EscalaTv.PIE_1, fontWeight = FontWeight.Bold, maxLines = 1)
            } else {
                LineaPiezas(listOf(Pieza.Icono(icono), Pieza.Texto(texto)), tamanoTexto = EscalaTv.PIE_1, peso = FontWeight.Bold)
            }
            if (piezas.isNotEmpty()) {
                LineaPiezas(piezas, Modifier.padding(top = 1.dp), color = LocalContentColor.current.copy(alpha = 0.6f))
            } else if (detalle != null) {
                Text(detalle, fontSize = EscalaTv.PIE_2, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp), color = LocalContentColor.current.copy(alpha = 0.6f))
            }
        }
    }
}

/**
 * Fila de un panel de cristal: blanca si tiene el foco, gris claro si es la elegida. [piezas] y [piezasSecundario], si
 * las hay, se dibujan en lugar de [texto] y [secundario]; a la derecha, [etiqueta] en palabras o [marca] en icono.
 */
@Composable
fun FilaPanel(
    texto: String,
    elegida: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activa: Boolean = true,
    secundario: String? = null,
    etiqueta: String? = null,
    piezas: List<Pieza> = emptyList(),
    piezasSecundario: List<Pieza> = emptyList(),
    marca: Pieza.Icono? = null,
) {
    val forma = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        enabled = activa,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(forma),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (elegida) Color.White.copy(alpha = 0.14f) else Color.Transparent,
            contentColor = Color.White.copy(alpha = if (activa) 0.85f else 0.4f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color.White.copy(alpha = 0.4f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val peso = if (elegida) FontWeight.Bold else FontWeight.SemiBold
                if (piezas.isNotEmpty()) {
                    LineaPiezas(piezas, peso = peso)
                } else {
                    Text(texto, fontSize = EscalaTv.PIE_2, fontWeight = peso, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val gris = LocalContentColor.current.copy(alpha = 0.65f)
                if (piezasSecundario.isNotEmpty()) {
                    LineaPiezas(piezasSecundario, Modifier.padding(top = 2.dp), peso = FontWeight.Medium, color = gris)
                } else if (secundario != null) {
                    Text(secundario, fontSize = EscalaTv.PIE_2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp), color = gris)
                }
            }
            if (marca != null) {
                Icono(marca.simbolo, tamanoIcono(EscalaTv.CUERPO), Modifier.padding(start = 6.dp), descripcion = marca.descripcion)
            } else if (etiqueta != null) {
                Text(
                    etiqueta,
                    fontSize = EscalaTv.PIE_2,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp).border(BorderStroke(0.75.dp, LocalContentColor.current.copy(alpha = 0.75f)), RoundedCornerShape(7.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
    }
}
