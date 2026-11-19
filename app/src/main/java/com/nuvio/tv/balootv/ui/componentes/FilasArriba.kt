package com.nuvio.tv.balootv.ui.componentes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Desplazamiento vertical como en el Apple TV: el cartel enfocado sube hasta arriba dejando a la vista solo el
 * título de su fila, y la fila anterior desaparece entera (con el desplazamiento mínimo asomaba cortada bajo
 * la cabecera). Solo para la lista vertical: cada fila restaura el comportamiento normal con [FilaNormal].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberFilasArriba(margen: Dp = HUECO_SOBRE_FILA + CARTEL_EN_FILA): BringIntoViewSpec {
    val base = LocalBringIntoViewSpec.current
    val px = with(LocalDensity.current) { margen.toPx() }
    return remember(base, px) {
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        object : BringIntoViewSpec {
            // Se lee en cada desplazamiento: el muelle cambia con el ritmo de navegación (ConRitmo).
            override val scrollAnimationSpec: AnimationSpec<Float> get() = base.scrollAnimationSpec
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - px
        }
    }
}

/**
 * Hueco al final de una lista con [rememberFilasArriba]: sin él, con pocas filas la lista se queda sin recorrido, la
 * última no llega arriba y la de encima asoma cortada (Mi lista con dos filas, 31-10-2026).
 */
fun LazyListScope.huecoFinal() {
    item(key = "hueco-final") { Spacer(Modifier.fillParentMaxHeight()) }
}

/** Lo que queda por encima de la fila enfocada al subirla (el cartel queda a este hueco más [CARTEL_EN_FILA]). */
val HUECO_SOBRE_FILA = 4.dp

/** Devuelve a una fila horizontal el desplazamiento normal dentro de una lista con [rememberFilasArriba]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilaNormal(normal: BringIntoViewSpec, contenido: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides normal, content = contenido)
}

/** Lista que no se desplaza sola al mover el foco: quien la usa la mueve por zonas enteras (la ficha). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberListaQuieta(): BringIntoViewSpec {
    val base = LocalBringIntoViewSpec.current
    return remember(base) {
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        object : BringIntoViewSpec {
            // Se lee en cada desplazamiento: el muelle cambia con el ritmo de navegación (ConRitmo).
            override val scrollAnimationSpec: AnimationSpec<Float> get() = base.scrollAnimationSpec
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
        }
    }
}
