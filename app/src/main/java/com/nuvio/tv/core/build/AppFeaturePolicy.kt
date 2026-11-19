package com.nuvio.tv.core.build

// BalooTV: un solo sabor. Sin plugins ni actualizador de NuvioTV. Los tráileres se abren en SmartTube,
// el cliente de YouTube de la tele (balootv/trailer/TrailerSmartTube.kt).

object AppFeaturePolicy {
    val inAppTrailerPlaybackEnabled: Boolean = false
    val externalTrailerPlaybackEnabled: Boolean = true
    val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.EXTERNAL
    val imdbRatingLogoEnabled: Boolean = true
}
