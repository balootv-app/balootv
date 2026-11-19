package com.nuvio.tv.core.tracking

import com.nuvio.tv.data.local.WatchProgressSource
import com.nuvio.tv.domain.model.LibrarySourceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackingSourcesTest {
    @Test
    fun `legacy source names retain their stored meaning`() {
        assertEquals(WatchProgressSource.TRAKT, WatchProgressSource.fromStorage("TRAKT"))
        assertEquals(WatchProgressSource.NUVIO_SYNC, WatchProgressSource.fromStorage("NUVIO_SYNC"))
        assertEquals(LibrarySourceMode.TRAKT, LibrarySourceMode.valueOf("TRAKT"))
    }

    @Test
    fun `removed providers stored by older versions fall back to Trakt`() {
        assertEquals(WatchProgressSource.TRAKT, WatchProgressSource.fromStorage("SIMKL"))
        assertEquals(WatchProgressSource.TRAKT, WatchProgressSource.fromStorage("MDBLIST"))
    }

    @Test
    fun `remote watch source falls back to Nuvio Sync when disconnected`() {
        assertEquals(
            WatchProgressSource.NUVIO_SYNC,
            effectiveWatchProgressSource(WatchProgressSource.TRAKT) { false }
        )
        assertEquals(
            WatchProgressSource.TRAKT,
            effectiveWatchProgressSource(WatchProgressSource.TRAKT) { it == TrackingProviderId.TRAKT }
        )
    }

    @Test
    fun `remote library source falls back to local when disconnected`() {
        assertEquals(
            LibrarySourceMode.LOCAL,
            effectiveLibrarySourceMode(LibrarySourceMode.TRAKT) { false }
        )
        assertEquals(
            LibrarySourceMode.TRAKT,
            effectiveLibrarySourceMode(LibrarySourceMode.TRAKT) { it == TrackingProviderId.TRAKT }
        )
    }

    @Test
    fun `local sources do not map to remote providers`() {
        assertNull(WatchProgressSource.NUVIO_SYNC.providerId)
        assertNull(LibrarySourceMode.LOCAL.providerId)
    }

    @Test
    fun `source reconciliation follows the Trakt connection`() {
        val requested = TrackingSourceSelection(
            watchProgressSource = WatchProgressSource.TRAKT,
            librarySourceMode = LibrarySourceMode.TRAKT
        )

        assertEquals(
            TrackingSourceSelection(
                WatchProgressSource.NUVIO_SYNC,
                LibrarySourceMode.LOCAL
            ),
            effectiveTrackingSourceSelection(requested, emptySet())
        )
        assertEquals(
            requested,
            effectiveTrackingSourceSelection(requested, setOf(TrackingProviderId.TRAKT))
        )
    }

    @Test
    fun `pickers expose Trakt only while connected`() {
        assertEquals(
            listOf(WatchProgressSource.NUVIO_SYNC),
            availableWatchProgressSources(emptySet())
        )
        assertEquals(
            listOf(LibrarySourceMode.LOCAL),
            availableLibrarySourceModes(emptySet())
        )
        assertEquals(
            listOf(WatchProgressSource.NUVIO_SYNC, WatchProgressSource.TRAKT),
            availableWatchProgressSources(setOf(TrackingProviderId.TRAKT))
        )
        assertEquals(
            listOf(LibrarySourceMode.LOCAL, LibrarySourceMode.TRAKT),
            availableLibrarySourceModes(setOf(TrackingProviderId.TRAKT))
        )
    }
}
