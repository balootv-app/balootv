package com.nuvio.tv.balootv.ui.movimiento

import android.view.KeyEvent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect

/**
 * Los movimientos de BalooTV en un solo sitio: muelles (interrumpibles y que conservan la velocidad al cambiar de
 * objetivo, como los de tvOS) en lugar de curvas con duración fija. La tele tiene las animaciones del sistema a 0,5x,
 * así que todos van el doble de rápido que en un emulador.
 */
object Movimiento {
    /** Desplazar filas y listas al mover el foco, paseando. */
    val desplazamiento: AnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f)

    /** La ficha al abrirse: crece de tarjeta a pantalla completa o llega deslizándose, sin rebote. */
    val aperturaFicha: AnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f)

    /** Desplazar en ráfaga: más rígido, para que la fila no se quede atrás del foco. */
    val desplazamientoRafaga: AnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1_400f)
}

/** Si se está navegando en ráfaga. Se lee dentro de las capas de dibujo o de los efectos, no al componer. */
@Stable
class EstadoRitmo {
    var rafaga by mutableStateOf(false)
        internal set

    /** Sin tocar el mando desde hace [REPOSO_PANTALLA_MS]: se atenúa todo menos lo enfocado, como en el Apple TV. */
    var inactivo by mutableStateOf(false)
        internal set
}

const val REPOSO_PANTALLA_MS = 12_000L

val LocalRitmo = staticCompositionLocalOf { EstadoRitmo() }

private val FLECHAS = setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)

/**
 * Raíz de una pantalla con ritmo de navegación: mide las flechas antes que nadie, descarta las repeticiones de más de la
 * tecla mantenida y comparte el ritmo con lo que hay dentro (carteles, cabecera, desplazamientos).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConRitmo(modifier: Modifier = Modifier, contenido: @Composable BoxScope.() -> Unit) {
    val estado = remember { EstadoRitmo() }
    val ritmo = remember { RitmoNavegacion() }
    val ambito = rememberCoroutineScope()
    val reposo = remember { arrayOfNulls<Job>(2) }
    val dormir: () -> Unit = {
        reposo[1]?.cancel()
        reposo[1] = ambito.launch {
            delay(REPOSO_PANTALLA_MS)
            estado.inactivo = true
        }
    }
    LaunchedEffect(Unit) { dormir() }
    val base = LocalBringIntoViewSpec.current
    val desplazamiento = remember(base, estado) { desplazamientoConRitmo(base, estado) }
    CompositionLocalProvider(LocalRitmo provides estado, LocalBringIntoViewSpec provides desplazamiento) {
        Box(
            modifier.onPreviewKeyEvent { e ->
                val nativo = e.nativeKeyEvent
                if (nativo.action == KeyEvent.ACTION_DOWN) {
                    if (estado.inactivo) estado.inactivo = false
                    dormir()
                }
                if (nativo.keyCode !in FLECHAS || nativo.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val ahora = nativo.eventTime
                val pasa = ritmo.pulsar(ahora, nativo.repeatCount)
                if (estado.rafaga != ritmo.rafaga) estado.rafaga = ritmo.rafaga
                reposo[0]?.cancel()
                reposo[0] = ambito.launch {
                    delay(RitmoNavegacion.REPOSO_MS)
                    estado.rafaga = false
                }
                !pasa
            },
            content = contenido,
        )
    }
}

/** El desplazamiento de la pantalla con el muelle que toca según el ritmo; la distancia la sigue decidiendo [base]. */
@OptIn(ExperimentalFoundationApi::class)
fun desplazamientoConRitmo(base: BringIntoViewSpec, estado: EstadoRitmo): BringIntoViewSpec = object : BringIntoViewSpec {
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float>
        get() = if (estado.rafaga) Movimiento.desplazamientoRafaga else Movimiento.desplazamiento

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        base.calculateScrollDistance(offset, size, containerSize)
}
