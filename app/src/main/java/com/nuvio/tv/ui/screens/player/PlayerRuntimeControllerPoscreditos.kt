package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.balootv.reproductor.Poscreditos
import com.nuvio.tv.data.repository.SkipInterval
import kotlinx.coroutines.flow.update

// BalooTV (C6): la escena poscréditos en los créditos de una película. Qué se enseña lo decide
// balootv/reproductor/Poscreditos; aquí solo se lleva al estado y se ejecuta.

/** Con el tramo activo (o ninguno): tarjeta o aviso de la escena, salvo que ya se haya descartado en estos créditos. */
internal fun PlayerRuntimeController.actualizarPoscreditos(activo: SkipInterval?) {
    // Salir de los créditos (o rebobinar antes) los deja listos para volver a avisar.
    if (activo == null) poscreditosDescartado = false
    val aviso = if (activo == null || poscreditosDescartado) null else Poscreditos.alEmpezarLosCreditos(
        activo, skipIntervals, currentPlaybackDurationMs(), _uiState.value.escenaCreditosTmdb,
    )
    if (_uiState.value.poscreditos != aviso) _uiState.update { it.copy(poscreditos = aviso) }
}

/** OK en la tarjeta, o fin de su cuenta atrás: a la escena, con el mismo salto que «Ir a la escena poscréditos». */
internal fun PlayerRuntimeController.irAPoscreditos() {
    val creditos = _uiState.value.activeSkipInterval ?: return
    descartarPoscreditos()
    skipInterval(creditos)
}

/** Atrás en la tarjeta: se quita y se siguen viendo los créditos. */
internal fun PlayerRuntimeController.descartarPoscreditos() {
    poscreditosDescartado = true
    _uiState.update { it.copy(poscreditos = null) }
}

/**
 * Atrás para salir de una película sin créditos de IntroDB que según TMDB tiene escena: la primera vez, en el último
 * 10 %, avisa en vez de salir. Devuelve si ha avisado.
 */
internal fun PlayerRuntimeController.avisarPoscreditosAlSalir(): Boolean {
    val escena = _uiState.value.escenaCreditosTmdb ?: return false
    if (poscreditosAvisadoAlSalir) return false
    val posicion = currentPlaybackPositionMs() ?: return false
    if (!Poscreditos.avisarAlSalir(posicion, currentPlaybackDurationMs(), skipIntervals, escena)) return false
    poscreditosAvisadoAlSalir = true
    _uiState.update { it.copy(avisoPoscreditosAlSalir = Poscreditos.textoAlSalir(escena)) }
    return true
}
