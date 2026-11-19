package com.nuvio.tv.ui.screens.ambient

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.AmbientModeDataStore
import com.nuvio.tv.domain.model.AmbientModeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PRELOAD_DELAY_MS = 20_000L
private const val REFRESH_INTERVAL_MS = 30L * 60_000L
private const val MAX_SLIDES = 30

@HiltViewModel
class AmbientModeViewModel @Inject constructor(
    dataStore: AmbientModeDataStore,
    private val loader: AmbientSlideLoader
) : ViewModel() {

    val settings: StateFlow<AmbientModeSettings> = dataStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AmbientModeSettings())

    private val _slides = MutableStateFlow<List<AmbientSlide>>(emptyList())
    val slides: StateFlow<List<AmbientSlide>> = _slides.asStateFlow()

    /** Todas las sugerencias del día; cada vez que sale el salvapantallas se enseñan [MAX_SLIDES] al azar. */
    private var todas: List<AmbientSlide> = emptyList()
    private var loadJob: Job? = null
    private var loadedAt = 0L

    init {
        viewModelScope.launch {
            settings.first { it.enabled }
            delay(PRELOAD_DELAY_MS)
            refresh()
        }
    }

    fun onShown() {
        val stale = SystemClock.elapsedRealtime() - loadedAt > REFRESH_INTERVAL_MS
        if (stale || todas.isEmpty()) refresh() else _slides.value = elegir()
    }

    private fun elegir() = todas.shuffled().take(MAX_SLIDES)

    private fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val nuevas = try {
                loader.load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (nuevas.isNotEmpty()) todas = nuevas
            _slides.value = elegir()
            loadedAt = SystemClock.elapsedRealtime()
        }
    }
}
