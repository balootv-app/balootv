package com.nuvio.tv.balootv.ui.fuentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.balootv.ui.componentes.Text
import com.nuvio.tv.balootv.errores.MensajeError
import com.nuvio.tv.balootv.fuentes.Aviso
import com.nuvio.tv.balootv.fuentes.EtiquetaFuente
import com.nuvio.tv.balootv.fuentes.LineaFuente
import com.nuvio.tv.balootv.iconos.Pieza
import com.nuvio.tv.balootv.iconos.Simbolo
import com.nuvio.tv.balootv.iconos.conSeparadores
import com.nuvio.tv.balootv.ui.componentes.BotonCristal
import com.nuvio.tv.balootv.ui.componentes.FilaPanel
import com.nuvio.tv.balootv.ui.componentes.cristal
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.ui.componentes.FondoEsmerilado

/**
 * "Cambiar fuente" (tablero 20): panel de cristal a la derecha con las líneas del motor (solo castellano y
 * dudosos, ≥1080p, lo mejor primero). "Esta no está en castellano" solo desde el reproductor.
 */
@Composable
fun PanelFuentes(
    lineas: List<LineaFuente>,
    buscando: Boolean,
    onElegir: (LineaFuente) -> Unit,
    onNoEsCastellano: (() -> Unit)?,
    modifier: Modifier = Modifier,
    anime: Boolean = false,
    porQueNoEmpieza: String? = null,
    vacio: MensajeError? = null,
    notaCaidas: String? = null,
    /** La imagen que hay detrás (la ficha): el panel la muestra esmerilada. Null en el reproductor (es vídeo). */
    fondo: String? = null,
) {
    val foco = remember { FocusRequester() }
    val hayLineas = lineas.isNotEmpty()
    LaunchedEffect(hayLineas) { if (hayLineas) runCatching { foco.requestFocus() } }
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
        val forma = RoundedCornerShape(18.dp)
        val caja = Modifier.align(Alignment.CenterEnd).padding(20.dp).width(430.dp).fillMaxHeight()
        if (fondo != null) FondoEsmerilado(fondo, forma, caja)
        Column(
            caja
                .then(if (fondo != null) Modifier else Modifier.cristal(forma))
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text("Cambiar fuente", fontSize = EscalaTv.TITULAR, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                if (anime) {
                    "Anime en japonés · 1080p o más · primero con subtítulos en español"
                } else {
                    "Solo castellano · 1080p o más · lo mejor para tu tele, primero"
                },
                fontSize = EscalaTv.PIE_2, color = Color.White.copy(alpha = 0.6f),
            )
            porQueNoEmpieza?.let { Text(it, fontSize = EscalaTv.PIE_1, color = Color.White) }
            // Arriba, a un paso de la primera fila: con listas largas, abajo quedaba a decenas de pulsaciones.
            if (onNoEsCastellano != null) {
                BotonCristal("Esta no está en castellano", onNoEsCastellano, relleno = PaddingValues(horizontal = 14.dp, vertical = 7.dp))
            }
            when {
                hayLineas -> LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    itemsIndexed(lineas, key = { i, l -> "${l.stream?.url}|${l.stream?.infoHash}|$i" }) { i, l ->
                        FilaPanel(
                            texto = l.principal,
                            secundario = l.detalle,
                            // Aquí sí va el códec, que sirve para elegir; y el tipo de copia, en su insignia.
                            piezas = l.formato.piezas(conCodec = true),
                            piezasSecundario = conSeparadores(listOfNotNull(l.tipo?.let(Pieza::Insignia)), listOf(Pieza.Texto(l.detalle))),
                            marca = marca(l.etiqueta),
                            etiqueta = "¿Castellano?".takeIf { l.etiqueta == EtiquetaFuente.DUDOSA },
                            elegida = l.etiqueta == EtiquetaFuente.REPRODUCIENDO,
                            onClick = { onElegir(l) },
                            modifier = if (i == 0) Modifier.focusRequester(foco) else Modifier,
                        )
                    }
                }
                buscando -> Text("Buscando fuentes…", fontSize = EscalaTv.PIE_1, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                else -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(vacio?.titulo ?: "No hay fuentes en castellano", fontSize = EscalaTv.CUERPO, fontWeight = FontWeight.Bold, color = Color.White)
                    vacio?.let { Text(it.texto, fontSize = EscalaTv.PIE_1, color = Color.White.copy(alpha = 0.7f)) }
                }
            }
            notaCaidas?.takeIf { hayLineas }?.let { Text(it, fontSize = EscalaTv.PIE_2, color = Color.White.copy(alpha = 0.55f)) }
        }
    }
}

/** El estado de la fuente en icono; la dudosa no lleva, lo dice con palabras («¿Castellano?»). */
private fun marca(e: EtiquetaFuente): Pieza.Icono? = when (e) {
    EtiquetaFuente.REPRODUCIENDO -> Pieza.Icono(Simbolo.REPRODUCIENDO, "Reproduciendo")
    EtiquetaFuente.LISTA -> Pieza.Icono(Simbolo.LISTA, "Lista")
    EtiquetaFuente.PREPARAR -> Pieza.Icono(Simbolo.PREPARAR, "Hay que prepararla")
    EtiquetaFuente.DUDOSA -> null
}

/** "Aún no hay versión en castellano" (tablero 22): versión original si la hay, o dejarlo para luego. */
@Composable
fun AvisoSinCastellano(aviso: Aviso, conVersionOriginal: Boolean, onVersionOriginal: () -> Unit, onAhoraNo: () -> Unit, modifier: Modifier = Modifier) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(500.dp).cristal(RoundedCornerShape(20.dp)).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(aviso.cabecera, fontSize = EscalaTv.PIE_2, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f))
            Text(aviso.titulo, fontSize = EscalaTv.TITULO_3, fontWeight = FontWeight.Bold, color = Color.White)
            Text(aviso.texto, fontSize = EscalaTv.PIE_1, color = Color.White.copy(alpha = 0.78f))
            androidx.compose.foundation.layout.Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (conVersionOriginal) {
                    BotonCristal(aviso.botonVO, onVersionOriginal, Modifier.focusRequester(foco), detalle = aviso.detalleVO, principal = true)
                    BotonCristal("Ahora no", onAhoraNo)
                } else {
                    BotonCristal("Ahora no", onAhoraNo, Modifier.focusRequester(foco), principal = true)
                }
            }
        }
    }
}
