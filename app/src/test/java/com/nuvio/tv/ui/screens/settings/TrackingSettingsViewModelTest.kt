package com.nuvio.tv.ui.screens.settings

import com.nuvio.tv.MainDispatcherRule
import com.nuvio.tv.core.tracking.TrackingProviderId
import com.nuvio.tv.core.tracking.TrackingSourceController
import com.nuvio.tv.core.tracking.TrackingSourceSelection
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.data.local.TraktAuthState
import com.nuvio.tv.data.local.WatchProgressSource
import com.nuvio.tv.domain.model.LibrarySourceMode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TrackingSettingsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `sources follow the Trakt connection`() = runTest {
        val traktState = MutableStateFlow(TraktAuthState())
        val reconciliations = mutableListOf<Set<TrackingProviderId>>()
        val controller = mockk<TrackingSourceController> {
            every { watchProgressSource } returns MutableStateFlow(WatchProgressSource.TRAKT)
            every { librarySourceMode } returns MutableStateFlow(LibrarySourceMode.TRAKT)
            coEvery { reconcileConnectedProviders(any()) } coAnswers {
                reconciliations += firstArg<Set<TrackingProviderId>>()
                TrackingSourceSelection(WatchProgressSource.TRAKT, LibrarySourceMode.TRAKT)
            }
        }
        val trakt = mockk<TraktAuthDataStore> { every { state } returns traktState }
        val viewModel = TrackingSettingsViewModel(controller, trakt)

        runCurrent()
        assertTrue(viewModel.uiState.value.isReady)
        assertEquals(emptySet<TrackingProviderId>(), viewModel.uiState.value.connectedProviderIds)
        assertEquals(WatchProgressSource.NUVIO_SYNC, viewModel.uiState.value.watchProgressSource)
        assertEquals(LibrarySourceMode.LOCAL, viewModel.uiState.value.librarySourceMode)

        traktState.value = TraktAuthState(accessToken = "a", refreshToken = "r")
        runCurrent()
        assertEquals(setOf(TrackingProviderId.TRAKT), viewModel.uiState.value.connectedProviderIds)
        assertEquals(WatchProgressSource.TRAKT, viewModel.uiState.value.watchProgressSource)
        assertEquals(LibrarySourceMode.TRAKT, viewModel.uiState.value.librarySourceMode)
        assertEquals(setOf(TrackingProviderId.TRAKT), reconciliations.last())
    }
}
