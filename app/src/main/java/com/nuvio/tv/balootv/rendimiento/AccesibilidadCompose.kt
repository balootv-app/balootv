package com.nuvio.tv.balootv.rendimiento

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.AbstractComposeView

/**
 * Evita que Compose recalcule el árbol de accesibilidad en cada cambio de foco.
 *
 * En la tele hay un servicio de accesibilidad activo (`com.gathin.tvhome/.InputKeyService`, el del launcher,
 * que solo intercepta el botón Input del mando). Compose considera "accesibilidad activa" en cuanto hay
 * cualquier servicio habilitado y entonces recorre la semántica de toda la pantalla en cada movimiento:
 * la traza del 21-10-2026 lo situó en ~35 % del tiempo del hilo principal al recorrer el Inicio.
 *
 * Compose guarda la lista de servicios en `_enabledServices` y solo la vuelve a pedir cuando cambia el
 * estado de accesibilidad del sistema. Se sustituye por una lista vacía en cada [aplicar] (en onResume),
 * así que BalooTV deja de exponerse a lectores de pantalla. Es intencionado: la app es para una sola tele
 * sin lector de pantalla. Las reglas de R8 que conservan estos nombres están en proguard-rules.pro.
 */
object AccesibilidadCompose {

    private const val TAG = "BalooTV/Accesibilidad"

    fun aplicar(raiz: View) {
        var vistas = 0
        // La vista interna de Compose (AndroidComposeView) es la hija de cada AbstractComposeView, que es API
        // pública: así no dependemos de su nombre de clase, que R8 puede cambiar.
        recorrer(raiz) { vista ->
            if (vista is AbstractComposeView) {
                for (i in 0 until vista.childCount) if (silenciar(vista.getChildAt(i))) vistas++
            }
        }
        Log.i(TAG, "Semántica de accesibilidad desactivada en $vistas vista(s) Compose")
    }

    private fun silenciar(vistaCompose: View): Boolean = runCatching {
        val delegado = vistaCompose.javaClass.getDeclaredField("composeAccessibilityDelegate")
            .apply { isAccessible = true }
            .get(vistaCompose) ?: return false
        delegado.javaClass.getDeclaredField("_enabledServices")
            .apply { isAccessible = true }
            .set(delegado, emptyList<Any>())
        true
    }.getOrElse { error ->
        // Si una versión futura de Compose cambia estos nombres, la app sigue funcionando, solo más lenta.
        Log.w(TAG, "No se pudo desactivar la semántica: ${error.message}")
        false
    }

    private fun recorrer(vista: View, accion: (View) -> Unit) {
        accion(vista)
        if (vista is ViewGroup) {
            for (i in 0 until vista.childCount) recorrer(vista.getChildAt(i), accion)
        }
    }
}
