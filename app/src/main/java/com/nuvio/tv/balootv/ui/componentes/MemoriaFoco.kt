package com.nuvio.tv.balootv.ui.componentes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/** Pide el foco cuando el elemento ya está dibujado: pedirlo antes falla en silencio. */
suspend fun FocusRequester.pedirTrasDibujar() {
    withFrameNanos { }
    withFrameNanos { }
    runCatching { requestFocus() }
}

/**
 * Qué se abrió desde una pantalla (fila y elemento) para devolverle el foco al volver de la ficha o del
 * reproductor, como hace tvOS. Vive en el ViewModel de la pantalla, que sobrevive a la navegación. Solo se activa
 * cuando la pantalla se ha cerrado de verdad ([RecordarAlSalir]); si no, se cumpliría al instante de abrir.
 */
class VueltaFoco {
    private var pendiente: Pair<String, String>? by mutableStateOf(null)
    private var armada by mutableStateOf(false)

    /** Hay que devolver el foco a algo (y no a la posición inicial de la pantalla). */
    val activa: Boolean get() = armada && pendiente != null

    fun abrir(fila: String, id: String) {
        pendiente = fila to id
        armada = false
    }

    fun salir() {
        if (pendiente != null) armada = true
    }

    /** El id a enfocar en [fila] al volver, o null si no toca. */
    fun en(fila: String): String? = pendiente?.takeIf { armada && it.first == fila }?.second

    fun cumplida() {
        pendiente = null
        armada = false
    }
}

/** Arma la [VueltaFoco] de una pantalla cuando esta deja de mostrarse. */
@Composable
fun RecordarAlSalir(vuelta: VueltaFoco) {
    DisposableEffect(vuelta) { onDispose { vuelta.salir() } }
}

/** Devuelve el foco a este elemento al componerse (una vez) y avisa para que no se repita. */
@Composable
fun Modifier.devolverFoco(activo: Boolean, onHecho: () -> Unit): Modifier {
    if (!activo) return this
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        foco.pedirTrasDibujar()
        onHecho()
    }
    return focusRequester(foco)
}
