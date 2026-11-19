package com.nuvio.tv.core.tmdb

import android.util.Log
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.TmdbSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MetaPreviewEnricher"
private const val ENRICHMENT_CONCURRENCY = 4

@Singleton
class MetaPreviewEnricher @Inject constructor(
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService
) {
    suspend fun enrich(
        items: List<MetaPreview>,
        settings: TmdbSettings
    ): List<MetaPreview> {
        if (items.isEmpty()) return items
        return coroutineScope {
            val semaphore = Semaphore(ENRICHMENT_CONCURRENCY)
            items.map { item ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            enrichItem(item, settings)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Enrichment failed for ${item.id}: ${e.message}")
                            item
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun enrichItem(
        item: MetaPreview,
        settings: TmdbSettings
    ): MetaPreview {
        val enrichment = run {
            val tmdbId = tmdbService.ensureTmdbId(item.id, item.apiType) ?: return@run null
            tmdbId.toIntOrNull()?.let { numericId ->
                runCatching { tmdbService.tmdbToImdb(numericId, item.apiType) }
            }
            tmdbMetadataService.fetchEnrichment(
                tmdbId = tmdbId,
                contentType = item.type,
                language = settings.language
            )
        }

        var enriched = item
        if (enrichment == null) return enriched

        if (settings.useArtwork) {
            enriched = enriched.copy(
                background = enrichment.backdrop ?: enriched.background,
                logo = enrichment.logo ?: enriched.logo,
                poster = enrichment.poster ?: enriched.poster
            )
        }

        if (settings.useBasicInfo) {
            enriched = enriched.copy(
                name = enrichment.localizedTitle ?: enriched.name,
                description = enrichment.description ?: enriched.description,
                genres = if (enrichment.genres.isNotEmpty()) enrichment.genres else enriched.genres,
                imdbRating = enriched.imdbRating
            )
        }

        if (settings.useDetails) {
            enriched = enriched.copy(
                runtime = enrichment.runtimeMinutes?.toString() ?: enriched.runtime,
                status = enrichment.status ?: enriched.status,
                ageRating = enrichment.ageRating ?: enriched.ageRating,
                country = enrichment.countries?.joinToString(", ") ?: enriched.country,
                language = enrichment.language ?: enriched.language
            )
        }

        return enriched
    }
}
