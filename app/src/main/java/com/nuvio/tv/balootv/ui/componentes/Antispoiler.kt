package com.nuvio.tv.balootv.ui.componentes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import coil3.request.transformations
import com.nuvio.tv.ui.util.BlurTransformation

/**
 * La imagen de un episodio que aún no se ha visto, difuminada para que no destripe nada. Se difumina al decodificarla,
 * fuera del hilo principal y una sola vez (Coil guarda el resultado): en Android 10 `Modifier.blur` no hace nada.
 */
@Composable
fun imagenSinVer(url: String?, sinVer: Boolean): Any? {
    val contexto = LocalContext.current
    return remember(url, sinVer) {
        if (url == null || !sinVer) url else ImageRequest.Builder(contexto).data(url).transformations(BlurTransformation()).build()
    }
}
