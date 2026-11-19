package com.nuvio.tv.balootv.ui.componentes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush

/**
 * Velo negro que funde una imagen de fondo por la izquierda y por abajo en UNA sola pasada de GPU.
 *
 * Con dos `Box` con degradado, cada píxel del fondo se pinta dos veces más; la GPU de la tele va justa de
 * relleno a 1080p y el Inicio perdía fotogramas por eso (traza del 23-oct: ~16 ms de GPU por fotograma).
 * `ComposeShader` en SRC_OVER da exactamente el mismo resultado que apilar los dos degradados.
 */
fun veloDoble(
    horizontal: Array<Pair<Float, Color>>,
    vertical: Array<Pair<Float, Color>>,
): ShaderBrush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val h = LinearGradientShader(
            from = Offset.Zero, to = Offset(size.width, 0f),
            colors = horizontal.map { it.second }, colorStops = horizontal.map { it.first },
        )
        val v = LinearGradientShader(
            from = Offset.Zero, to = Offset(0f, size.height),
            colors = vertical.map { it.second }, colorStops = vertical.map { it.first },
        )
        return android.graphics.ComposeShader(v, h, android.graphics.PorterDuff.Mode.SRC_OVER)
    }
}
