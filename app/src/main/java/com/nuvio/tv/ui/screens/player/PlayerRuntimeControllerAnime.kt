package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.balootv.reproductor.AvisoAnime
import com.nuvio.tv.balootv.reproductor.LatinoElegido
import com.nuvio.tv.balootv.reproductor.SubtitulosCastellano
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// BalooTV (spec F4 §5): el anime se ve en japonés con subtítulos en castellano. Si no los hay, se avisa antes de
// reproducir (tablero 23): el anime arranca en pausa hasta saber qué subtítulos hay, como mucho 10 s.

private const val ESPERA_SUBTITULOS_MS = 10_000L

/** Al crear el reproductor de un anime: en pausa hasta comprobar los subtítulos (una vez por episodio). */
internal fun PlayerRuntimeController.retenerAnimeSiHaceFalta() {
    val clave = currentVideoId ?: contentId
    if (!esAnime || animeComprobadoPara == clave) return
    animeComprobadoPara = clave
    comprobandoSubsAnime = true
    userPausedManually = true
    shouldEnforceAutoplayOnFirstReady = false
    comprobacionAnimeJob?.cancel()
    comprobacionAnimeJob = scope.launch {
        delay(ESPERA_SUBTITULOS_MS)
        decidirSubtitulosAnime(forzar = true)
    }
}

/** Se llama cada vez que cambian las pistas o llegan los subtítulos externos. */
internal fun PlayerRuntimeController.decidirSubtitulosAnime(forzar: Boolean) {
    if (!comprobandoSubsAnime) return
    val state = _uiState.value
    val listo = hasScannedTextTracksOnce && !state.isLoadingAddonSubtitles
    if (!listo && !forzar) return
    comprobandoSubsAnime = false
    comprobacionAnimeJob?.cancel()
    val internas = state.subtitleTracks
    if (SubtitulosCastellano.opciones(internas, state.addonSubtitles).hayCastellano) {
        reanudarAnime()
        return
    }
    val esEpisodio = state.currentSeason != null && state.currentEpisode != null
    val cabecera = if (esEpisodio) {
        "${state.contentName ?: state.title} · T${state.currentSeason} · E${state.currentEpisode}"
    } else {
        state.title
    }
    val hayLatinos = AvisoAnime.latino(internas, state.addonSubtitles) != null
    _uiState.update { it.copy(avisoAnimeSinSubs = AvisoAnime.de(cabecera, hayLatinos, esEpisodio), showControls = false) }
}

internal fun PlayerRuntimeController.animeConSubtitulosLatinos() {
    val state = _uiState.value
    when (val latino = AvisoAnime.latino(state.subtitleTracks, state.addonSubtitles)) {
        is LatinoElegido.Interno -> {
            selectSubtitleTrack(latino.pista.index)
            _uiState.update { it.copy(selectedSubtitleTrackIndex = latino.pista.index, selectedAddonSubtitle = null) }
        }
        is LatinoElegido.Externo -> selectAddonSubtitle(latino.subtitulo)
        null -> Unit
    }
    autoSubtitleSelected = true
    _uiState.update { it.copy(avisoAnimeSinSubs = null) }
    reanudarAnime()
}

internal fun PlayerRuntimeController.animeSinSubtitulos() {
    disableSubtitles()
    autoSubtitleSelected = true
    _uiState.update { it.copy(avisoAnimeSinSubs = null, selectedSubtitleTrackIndex = -1, selectedAddonSubtitle = null) }
    reanudarAnime()
}

private fun PlayerRuntimeController.reanudarAnime() {
    userPausedManually = false
    if (isUsingMpvEngine()) setPlaybackPaused(false) else _exoPlayer?.play()
}
