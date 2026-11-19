package com.nuvio.tv.ui.screens.player

import kotlinx.coroutines.flow.update

internal fun PlayerRuntimeController.setLoadingStatus(
    message: String?,
    progress: Float? = null,
    showOverlay: Boolean? = null
) {
    _uiState.update { state ->
        state.copy(
            showLoadingOverlay = showOverlay ?: state.showLoadingOverlay,
            loadingMessage = message,
            loadingProgress = progress
        )
    }
}
