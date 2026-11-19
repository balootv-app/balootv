package com.nuvio.tv.ui.screens.player

import android.os.SystemClock
import com.nuvio.tv.balootv.fuentes.Candidato
import com.nuvio.tv.balootv.fuentes.LineasFuente
import com.nuvio.tv.balootv.fuentes.Puntuado
import com.nuvio.tv.balootv.reproductor.SaltoFuente
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.data.local.StreamAutoPlayMode
import com.nuvio.tv.domain.model.AddonStreams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// BalooTV (spec F4 §3): si la fuente falla al abrir o se corta al principio, se prueba sola la siguiente buena
// con un aviso discreto, sin volver a la lista.

private const val AVISO_MS = 5_000L
private const val BUSQUEDA_MS = 20_000L
private const val TAG_SALTO = "BalooTV/Salto"

internal val PlayerRuntimeController.modoBalooTV: Boolean
    get() = streamAutoPlayModeSetting == StreamAutoPlayMode.BALOOTV

/** Al empezar a cargar una fuente: si a los 20 s no hay imagen, se salta. El reloj solo se reinicia con otra URL. */
internal fun PlayerRuntimeController.vigilarPrimeraImagen(url: String) {
    if (urlPrimeraImagen != url) {
        urlPrimeraImagen = url
        primeraImagenEnMs = 0L
    }
    vigilanciaImagenJob?.cancel()
    if (!modoBalooTV) return
    vigilanciaImagenJob = scope.launch {
        delay(SaltoFuente.SIN_IMAGEN_MS)
        android.util.Log.i(TAG_SALTO, "sin imagen a los ${SaltoFuente.SIN_IMAGEN_MS / 1000} s: primeraImagen=$hasRenderedFirstFrame pausa=$userPausedManually")
        if (!hasRenderedFirstFrame && !userPausedManually) intentarSaltoDeFuente(mensajeError = null)
    }
}

internal fun PlayerRuntimeController.anotarPrimeraImagen() {
    vigilanciaImagenJob?.cancel()
    if (primeraImagenEnMs == 0L) primeraImagenEnMs = SystemClock.elapsedRealtime()
}

/** Al cambiar de episodio las fuentes fallidas y los saltos vuelven a cero. */
internal fun PlayerRuntimeController.reiniciarSaltosDeFuente() {
    fuentesFallidas.clear()
    saltosFuente = 0
}

/**
 * true si se encarga BalooTV (busca o prueba otra fuente); false para seguir con el error de siempre.
 * Sin otra fuente buena, muestra [mensajeError] como el reproductor de siempre.
 */
internal fun PlayerRuntimeController.intentarSaltoDeFuente(
    mensajeError: String?,
    motivo: String = "Esa fuente falla",
): Boolean {
    if (saltoFuenteJob?.isActive == true) return true
    val enSesion = if (primeraImagenEnMs == 0L) 0L else SystemClock.elapsedRealtime() - primeraImagenEnMs
    if (!SaltoFuente.debeSaltar(enSesion, saltosFuente, modoBalooTV)) {
        android.util.Log.i(TAG_SALTO, "no se salta: enSesion=${enSesion}ms saltos=$saltosFuente modoBalooTV=$modoBalooTV")
        return false
    }
    saltosFuente++
    fuentesFallidas += currentStreamUrl
    vigilanciaImagenJob?.cancel()
    saltoFuenteJob = scope.launch {
        val siguiente = buscarFuenteSiguiente()
        val stream = siguiente?.candidato?.stream
        android.util.Log.i(TAG_SALTO, "salto ${saltosFuente}: ${if (stream == null) "sin otra fuente" else LineasFuente.de(siguiente).principal}")
        if (siguiente == null || stream == null) {
            _uiState.update {
                it.copy(
                    error = mensajeError ?: "No hay otra fuente en castellano que funcione",
                    avisoFuente = null,
                    showLoadingOverlay = false,
                    showPauseOverlay = false
                )
            }
            return@launch
        }
        val aviso = "$motivo. Probando la siguiente: " + LineasFuente.de(siguiente).principal
        _uiState.update { it.copy(avisoFuente = aviso, error = null) }
        switchToSourceStream(stream)
        delay(AVISO_MS)
        _uiState.update { if (it.avisoFuente == aviso) it.copy(avisoFuente = null) else it }
    }
    return true
}

private suspend fun PlayerRuntimeController.buscarFuenteSiguiente(): Puntuado? {
    val request = currentSourceStreamsRequestForBalooTV() ?: return null
    val grupos: List<AddonStreams> = streamRepository.getCachedStreamsFromAllAddons(
        request.type, request.videoId, request.season, request.episode
    ) ?: withTimeoutOrNull(BUSQUEDA_MS) {
        streamRepository.getStreamsFromAllAddons(request.type, request.videoId, request.season, request.episode)
            .filterIsInstance<NetworkResult.Success<List<AddonStreams>>>()
            .toList()
            .lastOrNull()
            ?.data
    } ?: return null
    val peticion = proveedorTitulosTmdb.repositorio.peticion(
        imdbId = com.nuvio.tv.balootv.fuentes.TitulosTmdbRepositorio.idDe(contentId ?: request.videoId),
        esSerie = request.season != null,
        anio = year?.take(4)?.toIntOrNull(),
        temporada = request.season,
        episodio = request.episode
    )
    return withContext(Dispatchers.Default) {
        SaltoFuente.siguiente(grupos.flatMap { it.streams }.map(Candidato::desde), peticion, fuentesFallidas.toSet())
    }
}

/** Líneas del motor para el panel "Cambiar fuente" del reproductor (en cualquier modo de elegir fuente). */
internal suspend fun PlayerRuntimeController.actualizarLineasFuente(enlaces: List<com.nuvio.tv.domain.model.Stream>) {
    val request = currentSourceStreamsRequestForBalooTV() ?: return
    val peticion = proveedorTitulosTmdb.repositorio.peticion(
        imdbId = com.nuvio.tv.balootv.fuentes.TitulosTmdbRepositorio.idDe(contentId ?: request.videoId),
        esSerie = request.season != null,
        anio = year?.take(4)?.toIntOrNull(),
        temporada = request.season,
        episodio = request.episode
    )
    val lineas = withContext(Dispatchers.Default) {
        val eleccion = com.nuvio.tv.balootv.fuentes.SelectorBalooTV.elegir(enlaces.map(Candidato::desde), peticion)
        LineasFuente.de(eleccion, urlActual = currentStreamUrl)
    }
    _uiState.update { it.copy(lineasFuente = lineas) }
}

/** "Esta no está en castellano": se apunta en la lista negra y se salta a la siguiente buena. */
internal fun PlayerRuntimeController.marcarNoCastellano() {
    val actual = _uiState.value.lineasFuente?.firstOrNull { it.stream?.url == currentStreamUrl }?.stream
    if (actual != null) almacenListaNegra.marcar(Candidato.desde(actual))
    _uiState.update { it.copy(showSourcesPanel = false, lineasFuente = null) }
    saltosFuente = 0
    primeraImagenEnMs = 0L
    if (!intentarSaltoDeFuente(mensajeError = null, motivo = "Apuntada como no castellano")) {
        _uiState.update { it.copy(showSourcesPanel = true) }
    }
}

/** ¿Es anime lo que se va a reproducir? Lo dice la ficha de TMDB (ya en caché casi siempre). */
internal suspend fun PlayerRuntimeController.averiguarAnime(): Boolean {
    val imdbId = com.nuvio.tv.balootv.fuentes.TitulosTmdbRepositorio.idDe(contentId ?: currentVideoId ?: return false)
    if (!imdbId.startsWith("tt") && !imdbId.startsWith("tmdb:")) return false
    return runCatching {
        proveedorTitulosTmdb.repositorio.peticion(imdbId, esSerie = currentSeason != null, anio = null, temporada = null, episodio = null).anime
    }.getOrDefault(false)
}
