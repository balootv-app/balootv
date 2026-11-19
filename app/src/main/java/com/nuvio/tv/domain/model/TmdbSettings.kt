package com.nuvio.tv.domain.model

data class TmdbSettings(
    // BalooTV: fichas completadas con TMDB por defecto, en castellano de España.
    val enabled: Boolean = true,
    val modernHomeEnabled: Boolean = false,
    val enrichContinueWatching: Boolean = true,
    val language: String = "es-ES",
    // Group: Artwork (logo, backdrop)
    val useArtwork: Boolean = true,
    // Group: Basic Info (description, genres, rating)
    val useBasicInfo: Boolean = true,
    // Group: Details (runtime, status, country, language)
    val useDetails: Boolean = true,
    // Group: Credits (cast with photos, director, writer)
    // Group: Production companies
    val useProductions: Boolean = true,
    // Group: Networks (logo)
    val useNetworks: Boolean = true,
    // Group: Episodes (episode titles, overviews, thumbnails)
    val useEpisodes: Boolean = true,
    // Group: Trailers (TMDB trailer candidates merged into detail metadata)
    val useTrailers: Boolean = true,
    // Group: Recommendations (more like this)
    val useMoreLikeThis: Boolean = true,
    // Group: Collections
    val useCollections: Boolean = true
)
