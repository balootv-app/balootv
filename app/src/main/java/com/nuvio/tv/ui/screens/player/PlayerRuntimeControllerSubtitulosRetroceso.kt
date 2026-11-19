package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.balootv.reproductor.EleccionSubtitulos
import com.nuvio.tv.balootv.reproductor.SubtitulosAlRetroceder
import com.nuvio.tv.balootv.reproductor.SubtitulosCastellano
import kotlinx.coroutines.flow.update

// BalooTV: al retroceder para repetir una frase, subtítulos completos en castellano hasta volver al punto de partida,
// como en tvOS. El cambio de pista va en el mismo paso que el salto, que ya recarga: no añade espera.

/** Se llama justo antes de buscar hacia atrás de [desdeMs] a [aMs]. */
internal fun PlayerRuntimeController.subtitulosAlRetroceder(desdeMs: Long, aMs: Long) {
    // Con mpv la posición tarda en llegar tras el salto y el tramo se cerraba nada más abrirse.
    if (isUsingMpvEngine()) return
    val state = _uiState.value
    val opciones = SubtitulosCastellano.opciones(state.subtitleTracks, state.addonSubtitles)
    val actual = SubtitulosCastellano.eleccion(
        opciones, state.subtitleTracks, state.selectedSubtitleTrackIndex, conAddon = state.selectedAddonSubtitle != null,
    )
    val anterior = tramoSubtitulos
    val completos = opciones.completoInterno
    val nuevo = SubtitulosAlRetroceder.alRetroceder(desdeMs, aMs, actual, hayCompletos = completos != null, tramo = anterior)
    tramoSubtitulos = nuevo
    when {
        anterior == null && nuevo != null && completos != null -> ponerPistaSubtitulos(completos.index)
        anterior != null && nuevo == null -> dejarSubtitulosComoAntes(anterior)
    }
}

/** En cada lectura de la posición: al llegar al punto de partida, se deja lo que había. */
internal fun PlayerRuntimeController.vigilarTramoSubtitulos(posicionMs: Long) {
    val tramo = tramoSubtitulos ?: return
    if (!SubtitulosAlRetroceder.terminado(posicionMs, tramo)) return
    tramoSubtitulos = null
    dejarSubtitulosComoAntes(tramo)
}

private fun PlayerRuntimeController.dejarSubtitulosComoAntes(tramo: SubtitulosAlRetroceder.Tramo) {
    val state = _uiState.value
    val forzados = SubtitulosCastellano.opciones(state.subtitleTracks, state.addonSubtitles).forzados
    if (tramo.antes == EleccionSubtitulos.FORZADOS && forzados != null) {
        ponerPistaSubtitulos(forzados.index)
    } else {
        disableSubtitles()
        _uiState.update { it.copy(selectedSubtitleTrackIndex = -1, selectedAddonSubtitle = null) }
    }
}

private fun PlayerRuntimeController.ponerPistaSubtitulos(indice: Int) {
    selectSubtitleTrack(indice)
    _uiState.update { it.copy(selectedSubtitleTrackIndex = indice, selectedAddonSubtitle = null) }
}
