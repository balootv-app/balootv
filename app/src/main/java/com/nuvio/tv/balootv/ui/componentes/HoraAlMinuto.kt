package com.nuvio.tv.balootv.ui.componentes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalTime
import kotlinx.coroutines.delay

/** La hora de ahora, que cambia al cambiar de minuto (con el reloj real: la tele tiene las animaciones a 0,5x). */
@Composable
fun rememberHoraAlMinuto(): LocalTime {
    var ahora by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            ahora = LocalTime.now()
            delay(((60 - ahora.second) * 1000L).coerceAtLeast(1_000L))
        }
    }
    return ahora
}
