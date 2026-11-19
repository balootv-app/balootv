package com.nuvio.tv.balootv.ui

import androidx.compose.runtime.Immutable
import com.nuvio.tv.balootv.ficha.TerminaALas
import com.nuvio.tv.balootv.iconos.Pieza
import com.nuvio.tv.balootv.iconos.Simbolo
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem

/**
 * Lo que enseña una tarjeta de "Seguir viendo" (tablero 12). `progreso` va de 0 a 1. `imagenes` son las
 * candidatas por orden de preferencia, sin vacías ni repetidas: si una no carga se pasa a la siguiente (la
 * miniatura de episodio de DAN DA DAN llegaba vacía y dejaba la tarjeta en negro). `fondo` es el del Inicio con la
 * tarjeta enfocada: la imagen apaisada de la serie o la película, nunca la del episodio ni el cartel.
 * El detalle sigue el contrato C7 con el móvil (casos en compartido/seguir.json); en pantalla va [corto], como en el
 * móvil: sin «Quedan», que ya lo dice la barra, y el siguiente episodio con su icono y sin barra vacía ([siguiente]).
 */
@Immutable
data class TarjetaSeguir(
    val titulo: String,
    val detalle: String,
    val imagenes: List<String>,
    val progreso: Float,
    val fondo: String? = null,
    val corto: List<Pieza> = emptyList(),
    val siguiente: Boolean = false,
) {
    val imagen: String? get() = imagenes.firstOrNull()

    companion object {
        /** C7: a medias es del 2 al 90 %, venga de donde venga el progreso. Lo usa la fila de HomeViewModel. */
        fun aMedias(p: WatchProgress): Boolean = p.isInProgress()

        fun de(item: ContinueWatchingItem): TarjetaSeguir = when (item) {
            is ContinueWatchingItem.InProgress -> {
                val p = item.progress
                val fraccion = if (p.duration > 0) p.position.toFloat() / p.duration else (p.progressPercent ?: 0f) / 100f
                val minutos = if (p.duration > 0) TerminaALas.minutosQueda(p.position, p.duration) else null
                val queda = minutos?.let(TerminaALas::queda)
                val episodio = if (p.season != null && p.episode != null) "T${p.season} · E${p.episode}" else null
                val corto = listOfNotNull(episodio, minutos?.let(TerminaALas::corto)).joinToString(" · ")
                TarjetaSeguir(
                    titulo = p.name,
                    detalle = listOfNotNull(episodio, queda).joinToString(" · "),
                    corto = listOfNotNull(corto.ifEmpty { null }?.let(Pieza::Texto)),
                    imagenes = candidatas(item.episodeThumbnail, p.backdrop, item.customLandscapePoster, p.poster, item.originalPoster),
                    progreso = fraccion.coerceIn(0f, 1f),
                    fondo = candidatas(p.backdrop, item.customLandscapePoster).firstOrNull(),
                )
            }
            // La imagen del episodio que aún no se ha visto puede destripar algo: la de la serie.
            is ContinueWatchingItem.NextUp -> TarjetaSeguir(
                titulo = item.info.name,
                detalle = "T${item.info.season} · E${item.info.episode} · Siguiente episodio",
                corto = listOf(Pieza.Icono(Simbolo.SIGUIENTE, "Siguiente episodio"), Pieza.Texto("T${item.info.season} · E${item.info.episode}")),
                siguiente = true,
                imagenes = candidatas(item.info.backdrop, item.customLandscapePoster, item.info.poster, item.originalPoster),
                progreso = 0f,
                fondo = candidatas(item.info.backdrop, item.customLandscapePoster).firstOrNull(),
            )
        }

        private fun candidatas(vararg urls: String?): List<String> = urls.mapNotNull { it?.trim()?.ifEmpty { null } }.distinct()
    }
}
