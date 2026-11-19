package com.nuvio.tv.ui.screens.home

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun HomeViewModel.observeLibraryState() {
    viewModelScope.launch {
        libraryRepository.sourceMode
            .distinctUntilChanged()
            .collectLatest { sourceMode ->
                _uiState.update { state ->
                    if (state.librarySourceMode == sourceMode) state else state.copy(librarySourceMode = sourceMode)
                }
            }
    }

    viewModelScope.launch {
        libraryRepository.membershipListTabs
            .distinctUntilChanged()
            .collectLatest { tabs ->
                _uiState.update { state ->
                    if (state.libraryListTabs == tabs) state else state.copy(libraryListTabs = tabs)
                }
            }
    }
}
