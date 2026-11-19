package com.nuvio.tv.balootv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import com.nuvio.tv.balootv.ui.componentes.Text
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.ui.componentes.ELEGIDO

/** Pestañas de BalooTV (F4 §1, tablero 7 opción A). `ruta` es la de la navegación. */
enum class Pestana(val titulo: String, val ruta: String) {
    INICIO("Inicio", "home"),
    PELICULAS("Películas", "peliculas"),
    SERIES("Series", "series_portada"),
    ANIME("Anime", "anime"),
    MI_LISTA("Mi lista", "library"),
}

enum class AccionAtras { SUBIR_A_BARRA, SALIR, NADA }

object Pestanas {
    const val RUTA_BUSCAR = "search"
    const val RUTA_AJUSTES = "settings"

    fun deRuta(ruta: String?): Pestana? = Pestana.entries.firstOrNull { it.ruta == ruta }

    /** Atrás en una portada: primero sube a la barra; con la barra enfocada, sale de la app (como tvOS). */
    fun alPulsarAtras(enRaiz: Boolean, focoEnBarra: Boolean): AccionAtras = when {
        !enRaiz -> AccionAtras.NADA
        focoEnBarra -> AccionAtras.SALIR
        else -> AccionAtras.SUBIR_A_BARRA
    }
}

/** Cómo sale la app (con la confirmación que ya tenía MainActivity). */
val LocalSalirDeLaApp = staticCompositionLocalOf<() -> Unit> { {} }

private val CRISTAL = Color(0x8C28282C)
private val BORDE = Color.White.copy(alpha = 0.18f)
private val PILDORA = RoundedCornerShape(20.dp)

/**
 * Barra de pestañas de cristal (tablero 12): va como primera fila de cada portada, así se marcha con el
 * scroll al bajar, igual que en tvOS. Gestiona también el botón Atrás de la portada.
 */
@Composable
fun BarraPestanas(
    actual: Pestana?,
    onIr: (String) -> Unit,
    foco: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var focoEnBarra by remember { mutableStateOf(false) }
    val salir = LocalSalirDeLaApp.current
    BackHandler(enabled = actual != null) {
        when (Pestanas.alPulsarAtras(enRaiz = true, focoEnBarra = focoEnBarra)) {
            AccionAtras.SUBIR_A_BARRA -> runCatching { foco.requestFocus() }
            AccionAtras.SALIR -> salir()
            AccionAtras.NADA -> Unit
        }
    }
    Box(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .onFocusChanged { focoEnBarra = it.hasFocus }
                // Al subir desde la portada se cae en la pestaña abierta, no en la que quede más cerca (Inicio
                // desde la izquierda), como en tvOS.
                .focusProperties { onEnter = { runCatching { foco.requestFocus() } } }
                .focusGroup()
                .background(CRISTAL, PILDORA)
                .border(0.5.dp, BORDE, PILDORA)
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Pestana.entries.forEach { p ->
                ElementoBarra(
                    texto = p.titulo,
                    seleccionada = p == actual,
                    modifier = if (p == actual || (actual == null && p == Pestana.INICIO)) Modifier.focusRequester(foco) else Modifier,
                ) { if (p != actual) onIr(p.ruta) }
            }
            Box(Modifier.padding(horizontal = 3.dp).width(0.5.dp).height(14.dp).background(Color.White.copy(alpha = 0.2f)))
            Icono(Icons.Default.Search, "Buscar") { onIr(Pestanas.RUTA_BUSCAR) }
            Icono(Icons.Default.Settings, "Ajustes") { onIr(Pestanas.RUTA_AJUSTES) }
        }
    }
}

@Composable
private fun ElementoBarra(texto: String, seleccionada: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(PILDORA),
        colors = ClickableSurfaceDefaults.colors(
            // Blanca solo la enfocada; la pestaña en la que estás, en cristal claro (si no, parecían dos enfocadas).
            containerColor = if (seleccionada) ELEGIDO else Color.Transparent,
            contentColor = if (seleccionada) Color.White else Color.White.copy(alpha = 0.82f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Text(texto, Modifier.padding(horizontal = 14.dp, vertical = 6.5.dp), fontSize = EscalaTv.PIE_1,
            fontWeight = if (seleccionada) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun Icono(icono: ImageVector, descripcion: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(25.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, contentColor = Color.White.copy(alpha = 0.85f),
            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
        ),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(0.dp, Color.Transparent))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Box(Modifier.size(25.dp), contentAlignment = Alignment.Center) {
            Icon(icono, contentDescription = descripcion, modifier = Modifier.size(13.dp))
        }
    }
    Spacer(Modifier.width(1.dp))
}
