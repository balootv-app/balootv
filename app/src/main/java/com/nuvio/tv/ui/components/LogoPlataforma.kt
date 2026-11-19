package com.nuvio.tv.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.tv.domain.model.InsigniaPlataforma

private val FORMA = RoundedCornerShape(4.dp)

/** BalooTV: logo pequeño de la plataforma de España donde está el título (esquina de la carátula). */
@Composable
fun LogoPlataforma(insignia: InsigniaPlataforma, modifier: Modifier = Modifier) {
    AsyncImage(
        model = insignia.logo,
        contentDescription = insignia.nombre,
        modifier = modifier.size(22.dp).clip(FORMA),
    )
}
