package com.nuvio.tv.core.tracking

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingScrobbleCoordinatorTest {
    @Test
    fun `fanout isolates a provider failure`() = runTest {
        val successful = FakeScrobbler(TrackingProviderId.TRAKT)
        val failing = FakeScrobbler(TrackingProviderId.TRAKT, IllegalStateException("offline"))
        val other = FakeScrobbler(TrackingProviderId.TRAKT)
        val event = TrackingScrobbleEvent(
            media = TrackingMediaReference(
                kind = TrackingMediaKind.MOVIE,
                ids = TrackingExternalIds(imdb = "tt0111161")
            ),
            progressPercent = 42.5
        )

        val failures = dispatchTrackingScrobble(
            scrobblers = listOf(successful, failing, other),
            action = TrackingScrobbleAction.PAUSE,
            event = event
        )

        assertEquals(1, successful.callCount)
        assertEquals(1, failing.callCount)
        assertEquals(listOf(TrackingScrobbleAction.PAUSE), other.actions)
        assertEquals(listOf(TrackingScrobbleAction.PAUSE), successful.actions)
        assertEquals(listOf(TrackingScrobbleAction.PAUSE), failing.actions)
        assertEquals(1, failures.size)
    }

    @Test
    fun `seek fanout targets only providers that restart sessions`() = runTest {
        val trakt = FakeScrobbler(
            providerId = TrackingProviderId.TRAKT,
            seekPolicy = TrackingSeekScrobblePolicy.STOP_AND_RESTART
        )
        val withoutRestart = FakeScrobbler(TrackingProviderId.TRAKT)
        val event = TrackingScrobbleEvent(
            media = TrackingMediaReference(
                kind = TrackingMediaKind.MOVIE,
                ids = TrackingExternalIds(imdb = "tt0111161")
            ),
            progressPercent = 55.0
        )

        dispatchTrackingSeekScrobble(
            scrobblers = listOf(trakt, withoutRestart),
            action = TrackingScrobbleAction.STOP,
            event = event
        )

        assertEquals(1, trakt.callCount)
        assertEquals(0, withoutRestart.callCount)
    }

    private class FakeScrobbler(
        override val providerId: TrackingProviderId,
        private val failure: Throwable? = null,
        private val seekPolicy: TrackingSeekScrobblePolicy = TrackingSeekScrobblePolicy.NONE
    ) : TrackingScrobbler {
        var callCount = 0
        val actions = mutableListOf<TrackingScrobbleAction>()
        override val seekScrobblePolicy = seekPolicy

        override suspend fun scrobble(action: TrackingScrobbleAction, event: TrackingScrobbleEvent) {
            callCount += 1
            actions += action
            failure?.let { throw it }
        }
    }
}
