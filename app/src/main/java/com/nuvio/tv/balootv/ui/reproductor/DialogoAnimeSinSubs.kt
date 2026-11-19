package com.nuvio.tv.balootv.ui.reproductor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.nuvio.tv.balootv.reproductor.AvisoAnimeSinSubs
import com.nuvio.tv.balootv.ui.componentes.BotonCristal
import com.nuvio.tv.balootv.ui.componentes.cristal
import com.nuvio.tv.balootv.ui.componentes.EscalaTv

/** "No hay subtítulos en castellano" del anime (tablero 23), antes de empezar a reproducir. */
@Composable
fun DialogoAnimeSinSubs(
    aviso: AvisoAnimeSinSubs,
    onLatinos: () -> Unit,
    onSinSubtitulos: () -> Unit,
    onAhoraNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(aviso) { runCatching { foco.requestFocus() } }
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(530.dp).cristal(RoundedCornerShape(20.dp)).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(aviso.cabecera, fontSize = EscalaTv.PIE_2, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f))
            Text(aviso.titulo, fontSize = EscalaTv.TITULO_3, fontWeight = FontWeight.Bold, color = Color.White)
            Text(aviso.texto, fontSize = EscalaTv.PIE_1, color = Color.White.copy(alpha = 0.78f))
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (aviso.hayLatinos) {
                    BotonCristal("Con subtítulos latinos", onLatinos, Modifier.focusRequester(foco), principal = true)
                    BotonCristal("Sin subtítulos", onSinSubtitulos)
                } else {
                    BotonCristal("Sin subtítulos", onSinSubtitulos, Modifier.focusRequester(foco), principal = true)
                }
                BotonCristal("Ahora no", onAhoraNo)
            }
        }
    }
}
