package com.nuvio.tv.data.repository

import android.content.Context
import com.nuvio.tv.core.debrid.DebridStreamPresentation
import com.nuvio.tv.core.debrid.LocalDebridAvailabilityService
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.DebridSettingsDataStore
import com.nuvio.tv.data.remote.api.AddonApi
import com.nuvio.tv.data.remote.dto.StreamDto
import com.nuvio.tv.data.remote.dto.StreamResponseDto
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.model.AddonStreams
import com.nuvio.tv.domain.model.DebridSettings
import com.nuvio.tv.domain.repository.AddonRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class StreamRepositoryAddonsTest {
    @Test
    fun `search finishes when no addon serves streams`() = runTest {
        val harness = newHarness(addons = emptyList())

        val results = harness.repository.getStreamsFromAllAddons(
            type = "movie",
            videoId = "tt1341338",
            season = null,
            episode = null
        ).toList()

        assertTrue(results.last() !is NetworkResult.Loading)
        coVerify(exactly = 0) { harness.api.getStreams(any()) }
    }

    /**
     * The stream path must read addon display info from the installed [Addon] it was given, not
     * refetch the manifest. fetchAddon is deliberately left stubbed on the harness so this asserts
     * the call is available and still not made, rather than merely un-stubbed.
     */
    @Test
    fun `stream lookup issues no manifest request`() = runTest {
        val harness = newHarness()

        val results = harness.repository.getStreamsFromAllAddons(
            type = "movie",
            videoId = "tt1341338",
            season = null,
            episode = null
        ).toList()

        val success = results.last() as NetworkResult.Success
        assertEquals(listOf("Fast Addon"), success.data.map { it.addonName })
        // Pins the URL too: the regression is not merely "one call happened", it is that the
        // stream request is the only request, built from the addon's own baseUrl.
        coVerify(exactly = 1) { harness.api.getStreams("https://addon.example/stream/movie/tt1341338.json") }
        coVerify(exactly = 0) { harness.addonRepository.fetchAddon(any()) }
    }

    @Test
    fun `streams carry the installed addon name and logo`() = runTest {
        val harness = newHarness()

        val results = harness.repository.getStreamsFromAllAddons(
            type = "movie",
            videoId = "tt1341338",
            season = null,
            episode = null
        ).toList()

        val success = results.last() as NetworkResult.Success
        val groups = success.data
        assertEquals(listOf("Fast Addon"), groups.map { it.addonName })
        assertEquals(listOf(ADDON_LOGO), groups.map { it.addonLogo })
        assertTrue(groups.single().streams.all { it.addonName == "Fast Addon" })
        assertTrue(groups.single().streams.all { it.addonLogo == ADDON_LOGO })
        coVerify(exactly = 0) { harness.addonRepository.fetchAddon(any()) }
    }

    private fun newHarness(addons: List<Addon> = listOf(compatibleAddon())): Harness {
        val addon = compatibleAddon()
        val api = mockk<AddonApi>()
        coEvery { api.getStreams(any()) } returns Response.success(
            StreamResponseDto(
                streams = listOf(
                    StreamDto(
                        name = "Fast Stream",
                        url = "https://stream.example/video.m3u8"
                    )
                )
            )
        )

        val addonRepository = mockk<AddonRepository>()
        every { addonRepository.getInstalledAddons() } returns flowOf(addons)
        coEvery { addonRepository.fetchAddon(addon.baseUrl) } returns NetworkResult.Success(addon)

        val profileManager = mockk<ProfileManager>(relaxed = true)
        every { profileManager.activeProfileId } returns MutableStateFlow(1)

        val debridSettingsDataStore = mockk<DebridSettingsDataStore>()
        every { debridSettingsDataStore.settings } returns flowOf(DebridSettings())
        val presentation = mockk<DebridStreamPresentation>()
        every { presentation.apply(any(), any<DebridSettings>(), any(), any()) } answers {
            firstArg<List<AddonStreams>>()
        }
        val availability = mockk<LocalDebridAvailabilityService>()
        coEvery { availability.markChecking(any()) } coAnswers {
            firstArg<List<AddonStreams>>()
        }
        coEvery { availability.annotateCachedAvailability(any()) } coAnswers {
            firstArg<List<AddonStreams>>()
        }

        return Harness(
            repository = StreamRepositoryImpl(
                context = mockk<Context>(relaxed = true),
                api = api,
                addonRepository = addonRepository,
                profileManager = profileManager,
                debridSettingsDataStore = debridSettingsDataStore,
                debridStreamPresentation = presentation,
                localDebridAvailabilityService = availability
            ),
            api = api,
            addonRepository = addonRepository
        )
    }

    private fun compatibleAddon(): Addon = Addon(
        id = "fast-addon",
        name = "Fast Addon",
        version = "1.0.0",
        description = null,
        logo = ADDON_LOGO,
        baseUrl = "https://addon.example",
        catalogs = emptyList(),
        types = emptyList(),
        resources = listOf(
            AddonResource(
                name = "stream",
                types = listOf("movie"),
                idPrefixes = listOf("tt")
            )
        )
    )

    private companion object {
        const val ADDON_LOGO = "https://addon.example/logo.png"
    }

    private data class Harness(
        val repository: StreamRepositoryImpl,
        val api: AddonApi,
        val addonRepository: AddonRepository
    )
}
