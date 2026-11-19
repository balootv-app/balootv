package com.nuvio.tv.ui.screens.player

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.SeekParameters
import com.nuvio.tv.R
import com.nuvio.tv.core.tracking.TRACKING_SCROBBLE_DIAGNOSTIC_TAG
import com.nuvio.tv.core.tracking.TrackingMediaKind
import com.nuvio.tv.core.tracking.TrackingMediaReference
import com.nuvio.tv.core.tracking.TrackingScrobbleAction
import com.nuvio.tv.core.tracking.TrackingScrobbleEvent
import com.nuvio.tv.core.tracking.buildTrackingMediaReference
import com.nuvio.tv.core.tracking.scrobbleDiagnosticIdentity
import com.nuvio.tv.data.local.InternalPlayerEngine
import com.nuvio.tv.data.local.SubtitleStyleSettings
import com.nuvio.tv.data.repository.SkipInterval
import com.nuvio.tv.domain.model.WatchProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal const val AUDIO_AMPLIFICATION_MIN_DB = 0
internal const val AUDIO_AMPLIFICATION_MAX_DB = 10
internal const val CENTER_MIX_LEVEL_MIN_DB = -10
internal const val CENTER_MIX_LEVEL_MAX_DB = 30
internal const val AUDIO_DELAY_MIN_MS = -60000
internal const val AUDIO_DELAY_MAX_MS = 60000
internal const val AUDIO_DELAY_STEP_MS = 25
internal const val AUDIO_DELAY_HOLD_STEP_MS = 50
internal const val AUDIO_DELAY_HOLD_FAST_STEP_MS = 100
internal const val AUDIO_DELAY_HOLD_THRESHOLD_MS = 1000L
internal const val AUDIO_DELAY_HOLD_FAST_THRESHOLD_MS = 2000L
internal const val AUDIO_DELAY_HOLD_REPEAT_INTERVAL_MS = 100L
internal const val WATCH_PROGRESS_SAVE_INTERVAL_MS = 90_000L

internal fun PlayerRuntimeController.applyAudioDelay(
    delayMs: Int,
    persistForCurrentRoute: Boolean = true
) {
    val clampedDelayMs = delayMs.coerceIn(AUDIO_DELAY_MIN_MS, AUDIO_DELAY_MAX_MS)
    audioDelayUs.set(clampedDelayMs.toLong() * 1000L)
    _uiState.update { it.copy(audioDelayMs = clampedDelayMs) }
    if (isUsingMpvEngine()) {
        mpvView?.setAudioDelayMs(clampedDelayMs)
    }
    if (persistForCurrentRoute) {
        persistAudioDelayForCurrentRoute(clampedDelayMs)
    }
}

internal fun PlayerRuntimeController.skipActiveInterval(): Boolean {
    return skipInterval(_uiState.value.activeSkipInterval ?: return false)
}

internal fun PlayerRuntimeController.skipInterval(interval: SkipInterval): Boolean {
    if (interval.type == "post-credits") return false
    val duration = currentPlaybackDurationMs().takeIf { it > 0 } ?: Long.MAX_VALUE
    val postCredits = interval.followingPostCreditsScene(skipIntervals, currentPlaybackDurationMs())
    val targetTime = postCredits?.startTime ?: interval.endTime
    val seekMs = if (targetTime == Double.MAX_VALUE) {
        duration
    } else {
        (targetTime * 1000).toLong()
    }
    val seekParameters = if (postCredits != null || interval.type == "movie-credits") {
        SeekParameters.EXACT
    } else SeekParameters.NEXT_SYNC
    val requested = seekMs.coerceAtMost(duration)
    seekPlaybackTo(requested, seekParameters)
    scheduleProgressSyncAfterSeek()
    _uiState.update { it.copy(activeSkipInterval = null, skipIntervalDismissed = true) }
    return true
}

internal fun PlayerRuntimeController.applyAudioAmplification(db: Int) {
    val clampedDb = db.coerceIn(AUDIO_AMPLIFICATION_MIN_DB, AUDIO_AMPLIFICATION_MAX_DB)
    val isAudioAmplificationAvailable = isUsingMpvEngine() || _exoPlayer != null
    val wasActive = gainAudioProcessor.isGainEnabled()
    gainAudioProcessor.setGainDb(if (isAudioAmplificationAvailable) clampedDb else AUDIO_AMPLIFICATION_MIN_DB)
    val isActiveNow = gainAudioProcessor.isGainEnabled()

    if (wasActive != isActiveNow && !isUsingMpvEngine()) {
        playbackSpeedAwareAudioSink?.notifyAudioProcessingRequirementChanged()
        _exoPlayer?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().build()
        }
    }

    if (isUsingMpvEngine()) {
        mpvView?.applyAudioAmplificationDb(clampedDb)
    }
    _uiState.update {
        it.copy(
            audioAmplificationDb = clampedDb,
            isAudioAmplificationAvailable = isAudioAmplificationAvailable
        )
    }
}

internal fun PlayerRuntimeController.applyCenterMixLevel(db: Int) {
    val clampedDb = db.coerceIn(CENTER_MIX_LEVEL_MIN_DB, CENTER_MIX_LEVEL_MAX_DB)
    ffmpegAudioRenderer?.setCenterMixLevelDb(clampedDb)
    _uiState.update { state ->
        state.copy(centerMixLevelDb = clampedDb)
    }
}

internal fun PlayerRuntimeController.updateAudioControlAvailability(
    audioTracks: List<TrackInfo> = _uiState.value.audioTracks,
    selectedAudioIndex: Int = _uiState.value.selectedAudioTrackIndex
) {
    val selectedTrack = audioTracks.getOrNull(selectedAudioIndex)
    val isAudioAmplificationAvailable = isUsingMpvEngine() || _exoPlayer != null
    val isCenterMixAvailable =
        ffmpegAudioRenderer?.isCenterMixActive() == true && (selectedTrack?.channelCount ?: 0) > 2
    val clampedDb = _uiState.value.audioAmplificationDb
        .coerceIn(AUDIO_AMPLIFICATION_MIN_DB, AUDIO_AMPLIFICATION_MAX_DB)
    gainAudioProcessor.setGainDb(
        if (isAudioAmplificationAvailable) clampedDb else AUDIO_AMPLIFICATION_MIN_DB
    )
    _uiState.update { state ->
        state.copy(
            isAudioAmplificationAvailable = isAudioAmplificationAvailable,
            isCenterMixAvailable = isCenterMixAvailable
        )
    }
}

internal fun PlayerRuntimeController.resetPostPlayStateAfterPlaybackEnded() {
    if (!shouldResetPostPlayStateAfterPlaybackEnded(
            state = _uiState.value,
            hasInFlightNextEpisodeAutoPlay = nextEpisodeAutoPlayJob?.isActive == true
        )
    ) {
        return
    }

    // If auto-play is enabled and the user dismissed the card earlier,
    // still auto-play the next episode when playback ends naturally.
    val state = _uiState.value
    val autoPlayEligible = streamAutoPlayNextEpisodeEnabledSetting &&
        state.nextEpisode?.hasAired == true &&
        nextEpisodeVideo != null
    if (autoPlayEligible && (state.postPlayDismissedForCurrentEpisode || state.postPlayMode == null)) {
        playNextEpisode()
        return
    }

    resetPostPlayOverlayState(clearEpisode = false)
}

internal fun shouldResetPostPlayStateAfterPlaybackEnded(
    state: PlayerUiState,
    hasInFlightNextEpisodeAutoPlay: Boolean
): Boolean {
    if (state.postPlayMode?.blocksNaturalCompletion() == true) return false
    if (hasInFlightNextEpisodeAutoPlay) return false
    return true
}

/**
 * Whether an ENDED / near-end event should count as a real episode finish.
 *
 * Debrid cache-sync placeholders and unplayable source responses (e.g. RAR-only
 * torrents, "service unavailable" error clips) often report a short duration and
 * reach STATE_ENDED. Treating those as natural completion marks the episode watched
 * and chains auto-play next through an entire season. Mirror the external-player
 * guard in [com.nuvio.tv.core.player.ExternalPlaybackTracker].
 */
internal fun shouldTreatAsNaturalPlaybackCompletion(
    hasRenderedFirstFrame: Boolean,
    hasFatalError: Boolean,
    durationMs: Long
): Boolean {
    if (hasFatalError) return false
    if (!hasRenderedFirstFrame) return false
    if (isShortPlaceholderDuration(durationMs)) return false
    return true
}

/** Streams shorter than ~2:01 are treated as error/placeholder clips, not real episodes. */
internal fun isShortPlaceholderDuration(duration: Long): Boolean = duration in 1..120_999L

internal fun PlayerRuntimeController.startProgressUpdates() {
    progressJob?.cancel()
    progressJob = scope.launch {
        while (isActive) {
            if (isUsingMpvEngine()) {
                val view = mpvView
                if (view != null) {
                    val pos = view.currentPositionMs().coerceAtLeast(0L)
                    val playerDuration = view.durationMs().coerceAtLeast(0L)
                    applyPendingMpvSeekIfNeeded(
                        view = view,
                        currentPositionMs = pos,
                        durationMs = playerDuration
                    )
                    val playingNow = view.isPlayingNow()
                    val cacheBuffering = view.isPausedForCacheNow() || view.isCoreIdleNow()
                    var firstFrameReady = hasRenderedFirstFrame
                        if (!firstFrameReady) {
                            firstFrameReady = view.isPositionFromRequestedMedia() &&
                                (pos > 0L || (playingNow && !cacheBuffering && playerDuration > 0L))
                            if (firstFrameReady) {
                                hasRenderedFirstFrame = true
                                resetMpvStartupWatchdog()
                                scheduleMpvStableProgressReset()
                                if (_uiState.value.postPlayDismissedForCurrentEpisode) {
                                    _uiState.update { it.copy(postPlayDismissedForCurrentEpisode = false) }
                                }
                            }
                        }
                    maybeRunMpvStartupWatchdog(view)
                    if (playerDuration > lastKnownDuration) {
                        lastKnownDuration = playerDuration
                    }
                    val displayPosition = pendingPreviewSeekPosition ?: pos
                    val playingForWatchClock = playingNow && !cacheBuffering
                    publishPlaybackTimeline(
                        currentPosition = displayPosition,
                        duration = playerDuration,
                        bufferedPosition = (pos + (view.demuxerCacheDurationSec() * 1000.0).toLong())
                            .coerceAtLeast(displayPosition),
                        playerReportsLive = view.isLiveStreamNow(),
                        isPlaying = playingForWatchClock
                    )
                    // Prefer the largest known duration; MPV can report a shorter one transiently.
                    // The playerDuration check stays: lastKnownDuration can still hold the previous
                    // stream's value until it resets.
                    val effectiveDuration = maxOf(playerDuration, lastKnownDuration)
                    val nearEnd = endDetectionArmed && playerDuration > 0L &&
                        pos >= (effectiveDuration - PlayerNextEpisodeRules.NEAR_END_MS)
                    val eofNow = view.isEofReached()
                    if (!eofNow) mpvEofSeenClear = true
                    val mpvEofReached = mpvEofSeenClear && eofNow
                    val naturalEnded = !view.isLiveStreamNow() && (nearEnd || mpvEofReached) && shouldTreatAsNaturalPlaybackCompletion(
                        hasRenderedFirstFrame = firstFrameReady,
                        hasFatalError = !_uiState.value.error.isNullOrBlank(),
                        durationMs = effectiveDuration
                    )
                    val wasEnded = _uiState.value.playbackEnded
                    _uiState.update { state ->
                        state.copy(
                            isPlaying = playingNow,
                            isBuffering = if (naturalEnded) false else (!firstFrameReady || cacheBuffering),
                            showLoadingOverlay = if (state.loadingOverlayEnabled) !firstFrameReady else false,
                            // Snap the loading-logo fill to 100% once playback is
                            // ready so the logo finishes filling on dismissal.
                            loadingProgress = if (firstFrameReady && state.loadingProgress != null) 1f else state.loadingProgress,
                            playbackEnded = naturalEnded
                        )
                    }
                    updateMpvTracksOnTick()
                    updateActiveSkipInterval(pos)
                    if (!_playbackTimeline.value.isLive) {
                        evaluatePostPlayOverlayVisibility(
                            positionMs = pos,
                            durationMs = playerDuration
                        )
                    }
                    if (naturalEnded && !wasEnded) {
                        // Short placeholders never set naturalEnded, so they cannot mark
                        // watched or auto-advance (see #2819).
                        handleNaturalPlaybackEnded()
                    }
                }
                delay(500)
                continue
            }

            _exoPlayer?.let { player ->
                val pos = player.currentPosition.coerceAtLeast(0L)
                val playerDuration = player.duration
                if (playerDuration > lastKnownDuration) {
                    lastKnownDuration = playerDuration
                }
                val displayPosition = pendingPreviewSeekPosition ?: pos
                publishPlaybackTimeline(
                    currentPosition = displayPosition,
                    duration = playerDuration.coerceAtLeast(0L),
                    bufferedPosition = player.bufferedPosition.coerceAtLeast(displayPosition),
                    playerReportsLive = player.isCurrentMediaItemLive,
                    isPlaying = player.isPlaying
                )
                updateActiveSkipInterval(pos)
                vigilarTramoSubtitulos(pos)
                if (!_playbackTimeline.value.isLive) {
                    evaluatePostPlayOverlayVisibility(
                        positionMs = pos,
                        durationMs = playerDuration.coerceAtLeast(0L)
                    )
                }

                // BalooTV: varias búsquedas seguidas pueden colgar el decodificador de vídeo (reproduce, hay margen
                // cargado y la posición no se mueve, imagen en negro). Primero se busca; si no basta, se recrea el
                // reproductor en la misma posición, como en la recuperación de errores.
                val cargadoDelante = (player.bufferedPosition - pos).coerceAtLeast(0L)
                val accion = if (pendingPreviewSeekPosition != null) {
                    com.nuvio.tv.balootv.reproductor.VigilanteAtascos.Accion.NADA
                } else {
                    vigilanteAtascos.observar(android.os.SystemClock.elapsedRealtime(), player.isPlaying, pos, cargadoDelante)
                }
                when (accion) {
                    com.nuvio.tv.balootv.reproductor.VigilanteAtascos.Accion.BUSCAR -> {
                        Log.i("BalooTV/Atasco", "posición quieta en ${pos / 1000} s con ${cargadoDelante / 1000} s cargados: se vuelve a buscar")
                        player.seekTo(pos)
                    }
                    com.nuvio.tv.balootv.reproductor.VigilanteAtascos.Accion.REINICIAR -> {
                        Log.i("BalooTV/Atasco", "sigue quieta en ${pos / 1000} s: se recrea el reproductor")
                        val pausado = userPausedManually
                        releasePlayer(flushPlaybackState = false)
                        _uiState.update { it.copy(pendingSeekPosition = pos) }
                        initializePlayer(currentStreamUrl, currentHeaders, startPaused = pausado)
                        return@launch
                    }
                    com.nuvio.tv.balootv.reproductor.VigilanteAtascos.Accion.NADA -> Unit
                }

                if (player.isPlaying) {
                    val now = System.currentTimeMillis()
                    if (now - lastBufferLogTimeMs >= 10_000) {
                        lastBufferLogTimeMs = now
                        val bufAhead = (player.bufferedPosition - player.currentPosition) / 1000
                        val loading = player.isLoading
                        val runtime = Runtime.getRuntime()
                        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                        val maxMb = runtime.maxMemory() / (1024 * 1024)
                        Log.d(PlayerRuntimeController.TAG, "BUFFER: ahead=${bufAhead}s, loading=$loading, heap=$usedMb/${maxMb}MB, pos=${pos / 1000}s")
                        
                        if (NuvioExoPlayerPerformanceHelper.shouldLogMemoryFootprint()) {
                            val defaultAllocator = _loadControl?.allocator as? androidx.media3.exoplayer.upstream.DefaultAllocator
                            val totalFootprintBytes = defaultAllocator?.let { allocator ->
                                runCatching { allocator.memoryFootprint.toLong() }.getOrDefault(0L)
                            } ?: 0L
                            val totalActiveBytes = defaultAllocator?.totalBytesAllocated?.toLong() ?: 0L
                            val footprintMb = totalFootprintBytes / (1024 * 1024)
                            val activeMb = totalActiveBytes / (1024 * 1024)
                            Log.d("ExoMemory", "Off-heap OS ahead: $footprintMb MB, active: $activeMb MB")
                        }
                    }
                }
            }
            delay(500)
        }
    }
}

internal fun PlayerRuntimeController.stopProgressUpdates() {
    progressJob?.cancel()
    progressJob = null
}

internal fun PlayerRuntimeController.startWatchProgressSaving() {
    watchProgressSaveJob?.cancel()
    watchProgressSaveJob = scope.launch {
        while (isActive) {
            delay(WATCH_PROGRESS_SAVE_INTERVAL_MS)
            saveWatchProgressIfNeeded()
        }
    }
}

internal fun PlayerRuntimeController.stopWatchProgressSaving() {
    watchProgressSaveJob?.cancel()
    watchProgressSaveJob = null
}

internal fun PlayerRuntimeController.saveWatchProgressIfNeeded() {
    if (!hasRenderedFirstFrame) return
    val currentPosition = currentPlaybackPositionMs() ?: return
    val duration = getEffectiveDuration(currentPosition)
    // Don't save progress for very short streams (< 2:01) — these are
    // typically error/warning messages or "stream not ready" placeholders that
    // would incorrectly mark content as watched when the user exits.
    if (isShortPlaceholderDuration(duration)) return

    if (kotlin.math.abs(currentPosition - lastSavedPosition) >= saveThresholdMs) {
        lastSavedPosition = currentPosition
        saveWatchProgressInternal(currentPosition, duration, syncRemote = false)
    }
}

internal fun PlayerRuntimeController.saveWatchProgress() {
    if (!hasRenderedFirstFrame) return
    val currentPosition = currentPlaybackPositionMs() ?: return
    val duration = getEffectiveDuration(currentPosition)
    if (isShortPlaceholderDuration(duration)) return
    saveWatchProgressInternal(currentPosition, duration)
}

internal fun PlayerRuntimeController.getEffectiveDuration(position: Long): Long {
    val playerDuration = currentPlaybackDurationMs()
    val effectiveDuration = maxOf(playerDuration, lastKnownDuration)
    if (effectiveDuration <= 0L) return 0L

    val isEnded = if (isUsingMpvEngine()) {
        position >= (effectiveDuration - 500L)
    } else {
        _exoPlayer?.playbackState == Player.STATE_ENDED
    }
    if (!isEnded && effectiveDuration < position) return 0L

    return effectiveDuration
}

private fun PlayerRuntimeController.isShortPlaceholderStream(): Boolean {
    val position = currentPlaybackPositionMs() ?: return false
    return isShortPlaceholderDuration(getEffectiveDuration(position))
}

/**
 * Handles a natural end-of-playback event for ExoPlayer / MPV.
 *
 * Short debrid placeholders and fatal-error states must not mark the episode
 * watched or trigger auto-play next.
 */
internal fun PlayerRuntimeController.handleNaturalPlaybackEnded() {
    val position = currentPlaybackPositionMs() ?: 0L
    val duration = getEffectiveDuration(position)
    val hasFatalError = !_uiState.value.error.isNullOrBlank()
    if (!shouldTreatAsNaturalPlaybackCompletion(
            hasRenderedFirstFrame = hasRenderedFirstFrame,
            hasFatalError = hasFatalError,
            durationMs = duration
        )
    ) {
        Log.i(
            PlayerRuntimeController.TAG,
            "Ignoring non-natural ENDED: firstFrame=$hasRenderedFirstFrame " +
                "error=$hasFatalError durationMs=$duration positionMs=$position"
        )
        // Prevent PlayerScreen from dispatching onPlaybackEnded / next-episode navigation.
        _uiState.update { it.copy(playbackEnded = false) }
        nextEpisodeAutoPlayJob?.cancel()
        nextEpisodeAutoPlayJob = null
        return
    }

    emitCompletionScrobbleStop(progressPercent = 99.5f)
    if (contentType.equals("cloud", ignoreCase = true)) {
        saveCloudLibraryProgress(position, duration, completed = true)
    } else {
        saveWatchProgress()
    }
    resetPostPlayStateAfterPlaybackEnded()
}

/**
 * Cancels any in-flight next-episode auto-play / still-watching prompt when a
 * fatal player error is shown. Callers should also clear [PlayerUiState.playbackEnded]
 * and [PlayerUiState.postPlayMode] in the same state update as the error message.
 */
internal fun PlayerRuntimeController.cancelNextEpisodeAutoPlayOnFatalError() {
    nextEpisodeAutoPlayJob?.cancel()
    nextEpisodeAutoPlayJob = null
    stillWatchingPromptJob?.cancel()
    stillWatchingPromptJob = null
}

internal fun PlayerRuntimeController.currentWatchProgress(
    parentContentId: String,
    parentContentType: String,
    position: Long,
    duration: Long,
    lastWatched: Long
) = WatchProgress(
    contentId = parentContentId,
    contentType = parentContentType,
    name = contentName ?: title,
    poster = poster,
    backdrop = backdrop,
    logo = logo,
    videoId = currentVideoId ?: parentContentId,
    season = currentSeason,
    episode = currentEpisode,
    episodeTitle = currentEpisodeTitle,
    position = position,
    duration = duration,
    lastWatched = lastWatched
)

internal fun PlayerRuntimeController.saveWatchProgressInternal(position: Long, duration: Long, syncRemote: Boolean = true) {
    if (contentType.equals("cloud", ignoreCase = true)) {
        saveCloudLibraryProgress(position, duration, completed = false)
        return
    }
    val parentContentId = contentId?.takeIf { it.isNotEmpty() } ?: return
    val parentContentType = contentType?.takeIf { it.isNotEmpty() } ?: return

    if (position < 1000) return

    val fallbackPercent = if (duration <= 0L) 5f else null

    val progress = currentWatchProgress(parentContentId, parentContentType, position, duration, System.currentTimeMillis())
        .copy(progressPercent = fallbackPercent)

    scope.launch(kotlinx.coroutines.NonCancellable) {
        val effectiveContentId = watchProgressRepository.normalizeParentContentId(
            parentContentId = progress.contentId,
            videoId = progress.videoId,
            profileId = profileId
        )
        val normalizedProgress = progress.copy(contentId = effectiveContentId)
        if (normalizedProgress.isCompleted()) {
            if (!hasMarkedCurrentEpisodeCompleted) {
                hasMarkedCurrentEpisodeCompleted = true
                watchProgressRepository.markAsCompleted(
                    normalizedProgress,
                    profileId = profileId,
                    broadcastTrackingHistory = false
                )
            }
        } else if (!hasMarkedCurrentEpisodeCompleted) {
            // Only save in-progress when the episode has not already been
            // marked as completed during this playback session.  After
            // natural playback completion the player can report stale
            // position/duration values (e.g. duration=0 → fallbackPercent=5)
            // which would overwrite the completed entry in the mutation
            // store and push an incorrect low-progress value to remote.
            watchProgressRepository.saveProgress(
                normalizedProgress,
                profileId = profileId,
                syncRemote = syncRemote
            )
        }
    }
}

private fun PlayerRuntimeController.saveCloudLibraryProgress(
    position: Long,
    duration: Long,
    completed: Boolean
) {
    if (!completed && position < 1_000L) return
    val playbackContext = cloudPlaybackContext ?: return
    val file = playbackContext.fileForVideoId(currentVideoId) ?: return
    cloudPlaybackProgressStore.save(
        item = playbackContext.item,
        file = file,
        positionMs = position,
        durationMs = duration,
        completed = completed
    )
}

internal fun PlayerRuntimeController.currentPlaybackProgressPercent(): Float {
    if (!hasRenderedFirstFrame) return 0f
    val position = currentPlaybackPositionMs() ?: return 0f
    val duration = currentPlaybackDurationMs().takeIf { it > 0 } ?: lastKnownDuration
    if (duration <= 0L) return 0f
    return ((position.toFloat() / duration.toFloat()) * 100f).coerceIn(0f, 100f)
}

internal fun PlayerRuntimeController.refreshScrobbleItem() {
    currentScrobbleItem = buildScrobbleItem()
    hasSentScrobbleStartForCurrentItem = false
    hasRequestedScrobbleStartForCurrentItem = false
    scrobbleStartRequestGeneration++
    hasSentCompletionScrobbleForCurrentItem = false
    logScrobbleDiagnostic("item_refreshed")
}

internal fun PlayerRuntimeController.buildScrobbleItem(): TrackingMediaReference? {
    val parentMetaId = contentId ?: return null
    val reference = buildTrackingMediaReference(
        contentType = contentType ?: "movie",
        parentMetaId = parentMetaId,
        videoId = currentVideoId,
        title = contentName ?: title,
        releaseInfo = year,
        seasonNumber = currentSeason,
        episodeNumber = currentEpisode,
        episodeTitle = currentEpisodeTitle
    )
    return reference.takeIf { media ->
        media.hasResolvableIdentity &&
            (media.kind == TrackingMediaKind.MOVIE ||
                media.kind == TrackingMediaKind.ANIME ||
                media.episode != null)
    }
}

internal fun PlayerRuntimeController.emitScrobbleStart() {
    logScrobbleDiagnostic("start_evaluated")
    if (isShortPlaceholderStream()) {
        logScrobbleDiagnostic("start_skipped", "reason=short_placeholder")
        return
    }
    if (hasRequestedScrobbleStartForCurrentItem) {
        logScrobbleDiagnostic("start_skipped", "reason=already_requested")
        return
    }

    // Don't start a new Trakt scrobble session if playback resumes at ≥80%.
    // This avoids creating a duplicate history entry when the user continues
    // watching something already marked as watched. If the user seeks back
    // below 80%, the next progress update will re-trigger scrobble start.
    val currentProgress = currentPlaybackProgressPercent()
    if (currentProgress >= 80f) {
        logScrobbleDiagnostic("start_skipped", "reason=completion_threshold progress=$currentProgress")
        return
    }

    hasRequestedScrobbleStartForCurrentItem = true
    val requestGeneration = ++scrobbleStartRequestGeneration
    logScrobbleDiagnostic("start_queued", "requestGeneration=$requestGeneration")
    scope.launch {
        // Wait for the episode mapping to finish (with its own timeout) so that
        // the scrobble start is sent with the correct season/episode number.
        traktMappingJob?.join()
        currentScrobbleItem = buildScrobbleItem()
        val item = currentScrobbleItem
        if (item == null) {
            logScrobbleDiagnostic("start_cancelled", "reason=no_scrobble_item requestGeneration=$requestGeneration")
            return@launch
        }
        if (requestGeneration != scrobbleStartRequestGeneration || !hasRequestedScrobbleStartForCurrentItem) {
            logScrobbleDiagnostic("start_cancelled", "reason=stale_before_dispatch requestGeneration=$requestGeneration")
            return@launch
        }
        val progressPercent = currentPlaybackProgressPercent()
        logScrobbleDiagnostic("start_dispatching", "requestGeneration=$requestGeneration progress=$progressPercent")
        val failures = trackingScrobbleCoordinator.scrobble(
            action = TrackingScrobbleAction.START,
            event = TrackingScrobbleEvent(item, progressPercent.toDouble())
        )
        logScrobbleDiagnostic(
            "start_dispatched",
            "requestGeneration=$requestGeneration failures=${failures.map { it.providerId.storageId }}"
        )
        if (requestGeneration != scrobbleStartRequestGeneration || !hasRequestedScrobbleStartForCurrentItem) {
            logScrobbleDiagnostic("start_not_recorded", "reason=stale_after_dispatch requestGeneration=$requestGeneration")
            return@launch
        }
        hasSentScrobbleStartForCurrentItem = true
        logScrobbleDiagnostic("start_recorded", "requestGeneration=$requestGeneration")
    }
}

internal fun PlayerRuntimeController.emitScrobbleStop(progressPercent: Float? = null) {
    logScrobbleDiagnostic("stop_evaluated", "providedProgress=${progressPercent ?: "none"}")
    if (isShortPlaceholderStream()) {
        logScrobbleDiagnostic("stop_skipped", "reason=short_placeholder")
        return
    }
    val item = currentScrobbleItem
    if (item == null) {
        logScrobbleDiagnostic("stop_skipped", "reason=no_scrobble_item")
        return
    }

    val provided = progressPercent
    if (!hasRequestedScrobbleStartForCurrentItem && (provided ?: 0f) < 80f) {
        logScrobbleDiagnostic("stop_skipped", "reason=no_active_scrobble providedProgress=${provided ?: "none"}")
        return
    }

    val percent = provided ?: currentPlaybackProgressPercent()
    logScrobbleDiagnostic("stop_queued", "progress=$percent")
    scope.launch(kotlinx.coroutines.NonCancellable) {
        logScrobbleDiagnostic("stop_dispatching", "progress=$percent")
        val failures = trackingScrobbleCoordinator.scrobble(
            action = TrackingScrobbleAction.STOP,
            event = TrackingScrobbleEvent(item, percent.toDouble())
        )
        logScrobbleDiagnostic("stop_dispatched", "progress=$percent failures=${failures.map { it.providerId.storageId }}")
    }
    scrobbleStartRequestGeneration++
    hasRequestedScrobbleStartForCurrentItem = false
    hasSentScrobbleStartForCurrentItem = false
    logScrobbleDiagnostic("stop_state_reset", "progress=$percent")
}

internal fun PlayerRuntimeController.emitScrobblePause(progressPercent: Float? = null) {
    logScrobbleDiagnostic("pause_evaluated", "providedProgress=${progressPercent ?: "none"}")
    if (isShortPlaceholderStream()) {
        logScrobbleDiagnostic("pause_skipped", "reason=short_placeholder")
        return
    }
    val item = currentScrobbleItem
    if (item == null) {
        logScrobbleDiagnostic("pause_skipped", "reason=no_scrobble_item")
        return
    }

    val percent = progressPercent ?: currentPlaybackProgressPercent()
    if (!shouldSendPauseScrobble(hasRequestedScrobbleStartForCurrentItem, percent)) {
        logScrobbleDiagnostic(
            "pause_skipped",
            "reason=policy active=$hasRequestedScrobbleStartForCurrentItem progress=$percent"
        )
        return
    }
    logScrobbleDiagnostic("pause_queued", "progress=$percent")
    scope.launch(kotlinx.coroutines.NonCancellable) {
        logScrobbleDiagnostic("pause_dispatching", "progress=$percent")
        val failures = trackingScrobbleCoordinator.scrobble(
            action = TrackingScrobbleAction.PAUSE,
            event = TrackingScrobbleEvent(item, percent.toDouble())
        )
        logScrobbleDiagnostic("pause_dispatched", "progress=$percent failures=${failures.map { it.providerId.storageId }}")
    }
    scrobbleStartRequestGeneration++
    hasRequestedScrobbleStartForCurrentItem = false
    hasSentScrobbleStartForCurrentItem = false
    logScrobbleDiagnostic("pause_state_reset", "progress=$percent")
}

internal fun PlayerRuntimeController.emitCompletionScrobbleStop(progressPercent: Float) {
    if (progressPercent < 80f || hasSentCompletionScrobbleForCurrentItem) return
    hasSentCompletionScrobbleForCurrentItem = true
    emitScrobbleStop(progressPercent = progressPercent)
}

internal fun PlayerRuntimeController.emitStopScrobbleForCurrentProgress() {
    val progressPercent = currentPlaybackProgressPercent()
    if (!shouldSendStopScrobble(hasRequestedScrobbleStartForCurrentItem, progressPercent)) {
        logScrobbleDiagnostic(
            "stop_current_skipped",
            "reason=policy active=$hasRequestedScrobbleStartForCurrentItem progress=$progressPercent"
        )
        return
    }
    if (progressPercent < 80f) {
        emitScrobbleStop(progressPercent = progressPercent)
        return
    }
    emitCompletionScrobbleStop(progressPercent = progressPercent)
}

internal fun PlayerRuntimeController.emitPauseScrobbleForCurrentProgress() {
    emitScrobblePause(progressPercent = currentPlaybackProgressPercent())
}

internal fun PlayerRuntimeController.emitSeekScrobbleRestart(progressPercent: Float) {
    if (progressPercent < 1f || progressPercent >= 80f) return
    if (isShortPlaceholderStream()) return
    val item = currentScrobbleItem ?: return
    if (!hasRequestedScrobbleStartForCurrentItem) return
    scope.launch {
        trackingScrobbleCoordinator.scrobbleSeek(
            action = TrackingScrobbleAction.STOP,
            event = TrackingScrobbleEvent(item, progressPercent.toDouble())
        )
        if (isPlaybackCurrentlyPlaying()) {
            trackingScrobbleCoordinator.scrobbleSeek(
                action = TrackingScrobbleAction.START,
                event = TrackingScrobbleEvent(item, currentPlaybackProgressPercent().toDouble())
            )
        }
    }
}

internal fun PlayerRuntimeController.flushPlaybackSnapshotForSwitchOrExit() {
    logScrobbleDiagnostic("flush_switch_or_exit")
    emitStopScrobbleForCurrentProgress()
    saveWatchProgress()
}

internal fun PlayerRuntimeController.logScrobbleDiagnostic(
    stage: String,
    detail: String = ""
) {
    val item = currentScrobbleItem?.scrobbleDiagnosticIdentity() ?: "media=none"
    Log.d(
        TRACKING_SCROBBLE_DIAGNOSTIC_TAG,
        "player stage=$stage engine=$currentInternalPlayerEngine uiPlaying=${_uiState.value.isPlaying} " +
            "requested=$hasRequestedScrobbleStartForCurrentItem sent=$hasSentScrobbleStartForCurrentItem " +
            "generation=$scrobbleStartRequestGeneration $item $detail".trim()
    )
}

internal fun PlayerRuntimeController.scheduleProgressSyncAfterSeek() {
    seekProgressSyncJob?.cancel()
    seekProgressSyncJob = scope.launch {
        delay(seekProgressSyncDebounceMs)
        saveWatchProgress()

        val progressPercent = currentPlaybackProgressPercent()
        emitSeekScrobbleRestart(progressPercent = progressPercent)
    }
}

fun PlayerRuntimeController.scheduleHideControls() {
    hideControlsJob?.cancel()
    hideControlsJob = scope.launch {
        delay(3000)
        if (_uiState.value.isPlaying && !_uiState.value.showAudioOverlay &&
            !_uiState.value.showSubtitleOverlay && !_uiState.value.showSpeedDialog && !_uiState.value.showMoreDialog &&
            !_uiState.value.showEpisodesPanel && !_uiState.value.showSourcesPanel &&
            !_uiState.value.showStreamInfoOverlay) {
            _uiState.update { it.copy(showControls = false) }
        }
    }
}

/** BalooTV: el retraso de los subtítulos, desde la fila «Retraso» del panel de audio y subtítulos. */
internal fun PlayerRuntimeController.setSubtitleDelayMs(targetMs: Int) {
    val newDelayMs = targetMs.coerceIn(
        minimumValue = SUBTITLE_DELAY_MIN_MS,
        maximumValue = SUBTITLE_DELAY_MAX_MS
    )
    subtitleDelayUs.set(newDelayMs.toLong() * 1000L)
    if (isUsingMpvEngine()) {
        mpvView?.setSubtitleDelayMs(newDelayMs)
    }
    _uiState.update { it.copy(subtitleDelayMs = newDelayMs) }
    refreshActiveSubtitleTrackAfterTimingChange()
    // Remember the delay so it survives to the next session (issue #1063).
    persistTrackPreference()
}

internal fun PlayerRuntimeController.schedulePauseOverlay() {
    pauseOverlayJob?.cancel()

    if (!_uiState.value.pauseOverlayEnabled || !hasRenderedFirstFrame || !userPausedManually) {
        _uiState.update { it.copy(showPauseOverlay = false) }
        return
    }

    _uiState.update { it.copy(showPauseOverlay = false) }
    pauseOverlayJob = scope.launch {
        delay(pauseOverlayDelayMs)
        val s = _uiState.value
        val anyPanelOpen = s.showSubtitleOverlay || s.showSpeedDialog || s.showMoreDialog || s.showEpisodesPanel ||
            s.showSourcesPanel || s.showAudioOverlay || s.showStreamInfoOverlay
        if (!s.isPlaying && s.pauseOverlayEnabled && s.error == null && !anyPanelOpen) {
            _uiState.update { it.copy(showPauseOverlay = true, showControls = false) }
        }
    }
}

internal fun PlayerRuntimeController.cancelPauseOverlay() {
    pauseOverlayJob?.cancel()
    pauseOverlayJob = null
    _uiState.update { it.copy(showPauseOverlay = false) }
}

fun PlayerRuntimeController.onUserInteraction() {
    if (_uiState.value.showPauseOverlay) {
        cancelPauseOverlay()
        showControlsTemporarily()
    } else if (pauseOverlayJob != null && !_uiState.value.isPlaying && userPausedManually) {
        schedulePauseOverlay()
    }
}

fun PlayerRuntimeController.hideControls() {
    hideControlsJob?.cancel()
    _uiState.update { it.copy(showControls = false, showSeekOverlay = false, showMoreDialog = false) }
}

fun PlayerRuntimeController.onEvent(event: PlayerEvent) {
    if (event != PlayerEvent.OnParentalGuideHide) {
        onUserInteraction()
    }
    // BalooTV: quien toca el mando está ahí; «¿Sigues viendo?» vuelve a contar episodios desde cero.
    when (event) {
        PlayerEvent.OnPlayPause, PlayerEvent.OnToggleControls, is PlayerEvent.OnSeekBy,
        is PlayerEvent.OnPreviewSeekBy, is PlayerEvent.OnSeekTo -> consecutiveAutoPlayCount = 0
        else -> Unit
    }
    when (event) {
        PlayerEvent.OnPararAlAcabar -> {
            _uiState.update { it.copy(pararAlAcabar = !it.pararAlAcabar) }
        }
        PlayerEvent.OnIrAPoscreditos -> irAPoscreditos()
        // Como «¿Sigues viendo?» → Salir: guarda el punto y Trakt queda al día.
        PlayerEvent.OnSalirSinPoscreditos -> exitFromStillWatching()
        PlayerEvent.OnDescartarPoscreditos -> descartarPoscreditos()
        PlayerEvent.OnPlayPause -> {
            if (isUsingMpvEngine()) {
                val playing = isPlaybackCurrentlyPlaying()
                if (playing) {
                    userPausedManually = true
                    setPlaybackPaused(true)
                    stopProgressUpdates()
                    stopWatchProgressSaving()
                    emitPauseScrobbleForCurrentProgress()
                    schedulePauseOverlay()
                } else {
                    userPausedManually = false
                    cancelPauseOverlay()
                    setPlaybackPaused(false)
                    startProgressUpdates()
                    startWatchProgressSaving()
                    scheduleHideControls()
                    emitScrobbleStart()
                }
            } else {
                _exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        userPausedManually = true
                        player.pause()
                        schedulePauseOverlay()
                    } else {
                        userPausedManually = false
                        cancelPauseOverlay()
                        player.play()
                    }
                }
            }
            showControlsTemporarily()
        }
        PlayerEvent.OnSeekForward -> {
            if (_playbackTimeline.value.isLive) return
            onEvent(PlayerEvent.OnSeekBy(deltaMs = PlayerScrubRates.STEP_SHORT_MS))
        }
        PlayerEvent.OnSeekBackward -> {
            if (_playbackTimeline.value.isLive) return
            onEvent(PlayerEvent.OnSeekBy(deltaMs = -PlayerScrubRates.STEP_SHORT_MS))
        }
        is PlayerEvent.OnSeekBy -> {
            if (_playbackTimeline.value.isLive) return
            pendingPreviewSeekPosition = null
            val current = currentPlaybackPositionMs() ?: 0L
            val maxDuration = currentPlaybackDurationMs().takeIf { it >= 0 } ?: Long.MAX_VALUE
            val requested = (current + event.deltaMs)
                .coerceAtLeast(0L)
                .coerceAtMost(maxDuration)
            val target = requested
            if (target < current) subtitulosAlRetroceder(current, target)
            val seekParameters = if (event.deltaMs < 0L) {
                SeekParameters.PREVIOUS_SYNC
            } else {
                SeekParameters.NEXT_SYNC
            }
            seekPlaybackTo(target, seekParameters)
            updatePlaybackTimeline(currentPosition = target)
            scheduleProgressSyncAfterSeek()
            if (_uiState.value.showControls) {
                showControlsTemporarily()
            } else {
                showSeekOverlayTemporarily()
            }
        }
        is PlayerEvent.OnPreviewSeekBy -> {
            if (_playbackTimeline.value.isLive) return
            val maxDuration = currentPlaybackDurationMs().takeIf { it >= 0 } ?: Long.MAX_VALUE
            val basePosition = pendingPreviewSeekPosition ?: currentPlaybackPositionMs()?.coerceAtLeast(0L) ?: 0L
            val target = (basePosition + event.deltaMs)
                .coerceAtLeast(0L)
                .coerceAtMost(maxDuration)
            pendingPreviewSeekPosition = target
            updatePlaybackTimeline(currentPosition = target)
            if (_uiState.value.showControls) {
                showControlsTemporarily()
            } else {
                showSeekOverlayTemporarily()
            }
        }
        PlayerEvent.OnCommitPreviewSeek -> {
            if (_playbackTimeline.value.isLive) return
            val target = pendingPreviewSeekPosition
            if (target != null) {
                currentPlaybackPositionMs()?.let { desde -> if (target < desde) subtitulosAlRetroceder(desde, target) }
                seekPlaybackTo(target, SeekParameters.CLOSEST_SYNC)
                updatePlaybackTimeline(currentPosition = target)
                pendingPreviewSeekPosition = null
                scheduleProgressSyncAfterSeek()
                if (_uiState.value.showControls) {
                    showControlsTemporarily()
                } else {
                    showSeekOverlayTemporarily()
                }
            }
        }
        is PlayerEvent.OnSeekTo -> {
            if (_playbackTimeline.value.isLive) return
            pendingPreviewSeekPosition = null
            val target = event.position
            seekPlaybackTo(target, SeekParameters.CLOSEST_SYNC)
            updatePlaybackTimeline(currentPosition = target)
            scheduleProgressSyncAfterSeek()
            if (_uiState.value.showControls) {
                showControlsTemporarily()
            } else {
                showSeekOverlayTemporarily()
            }
        }
        is PlayerEvent.OnSelectAudioTrack -> {
            logSwitchTrace(
                stage = "event-select-audio",
                message = "index=${event.index}"
            )
            rememberAudioSelection(event.index)
            selectAudioTrack(event.index)
            _uiState.update {
                it.copy(
                    showAudioOverlay = false,
                )
            }
        }
        is PlayerEvent.OnSetAudioDelayMs -> {
            applyAudioDelay(event.delayMs)
        }
        is PlayerEvent.OnSetAudioAmplificationDb -> {
            val clampedDb = event.db.coerceIn(AUDIO_AMPLIFICATION_MIN_DB, AUDIO_AMPLIFICATION_MAX_DB)
            applyAudioAmplification(clampedDb)
            if (_uiState.value.persistAudioAmplification) {
                scope.launch {
                    playerSettingsDataStore.setAudioAmplificationDb(clampedDb)
                }
            }
        }
        is PlayerEvent.OnSetPersistAudioAmplification -> {
            val currentDb = _uiState.value.audioAmplificationDb
            val currentCenterMixDb = _uiState.value.centerMixLevelDb
            _uiState.update { it.copy(persistAudioAmplification = event.enabled) }
            scope.launch {
                playerSettingsDataStore.setPersistAudioAmplification(
                    enabled = event.enabled,
                    dbToPersist = if (event.enabled) currentDb else null,
                    centerMixDbToPersist = if (event.enabled) currentCenterMixDb else null
                )
            }
        }
        is PlayerEvent.OnSetCenterMixLevelDb -> {
            val clampedDb = event.db.coerceIn(CENTER_MIX_LEVEL_MIN_DB, CENTER_MIX_LEVEL_MAX_DB)
            applyCenterMixLevel(clampedDb)
            if (_uiState.value.persistAudioAmplification) {
                scope.launch {
                    playerSettingsDataStore.setCenterMixLevelDb(clampedDb)
                }
            }
        }
        is PlayerEvent.OnSelectSubtitleTrack -> {
            logSwitchTrace(
                stage = "event-select-subtitle-internal",
                message = "index=${event.index}"
            )
            tramoSubtitulos = null
            autoSubtitleSelected = true
            pendingAddonSubtitleLanguage = null
            pendingAddonSubtitleTrackId = null
            pendingAudioSelectionAfterSubtitleRefresh = null
            rememberInternalSubtitleSelection(event.index)
            selectSubtitleTrack(event.index)
            _uiState.update {
                it.copy(
                    showSubtitleOverlay = true,
                    showControls = true,
                    selectedAddonSubtitle = null
                )
            }
        }
        PlayerEvent.OnDisableSubtitles -> {
            logSwitchTrace(
                stage = "event-disable-subtitles",
                message = "selectedSubtitleIndex=${_uiState.value.selectedSubtitleTrackIndex}"
            )
            tramoSubtitulos = null
            autoSubtitleSelected = true
            pendingAddonSubtitleLanguage = null
            pendingAddonSubtitleTrackId = null
            pendingAudioSelectionAfterSubtitleRefresh = null
            rememberSubtitleDisabled()
            disableSubtitles()
            _uiState.update {
                it.copy(
                    showSubtitleOverlay = true,
                    showControls = true,
                    selectedAddonSubtitle = null,
                    selectedSubtitleTrackIndex = -1
                )
            }
        }
        is PlayerEvent.OnSelectAddonSubtitle -> {
            logSwitchTrace(
                stage = "event-select-subtitle-addon",
                message = "addonId=${event.subtitle.id} addonLang=${event.subtitle.lang} addonName=${event.subtitle.addonName}"
            )
            tramoSubtitulos = null
            autoSubtitleSelected = true
            rememberAddonSubtitleSelection(event.subtitle)
            selectAddonSubtitle(event.subtitle)
            _uiState.update {
                it.copy(
                    showSubtitleOverlay = true,
                    showControls = true
                )
            }
        }
        is PlayerEvent.OnSetPlaybackSpeed -> {
            if (isUsingMpvEngine()) {
                setPlaybackSpeedInternal(event.speed)
            } else {
                _exoPlayer?.let { player ->
                    player.setPlaybackSpeed(event.speed)
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .build()
                }
            }
            _uiState.update {
                it.copy(
                    playbackSpeed = event.speed,
                    showSpeedDialog = false,
                )
            }
            contentId?.takeIf { it.isNotBlank() }?.let { id ->
                scope.launch { trackPreferenceDataStore.savePlaybackSpeed(id, event.speed) }
            }
        }
        PlayerEvent.OnToggleControls -> {
            val shouldShowControls = !_uiState.value.showControls
            _uiState.update {
                it.copy(
                    showControls = shouldShowControls,
                    showSeekOverlay = false,
                    showMoreDialog = if (shouldShowControls) it.showMoreDialog else false
                )
            }
            if (shouldShowControls) {
                scheduleHideControls()
            }
        }
        PlayerEvent.OnShowAudioOverlay -> {
            _uiState.update {
                it.copy(
                    showAudioOverlay = true,
                    showSubtitleOverlay = false,
                    showMoreDialog = false,
                    showControls = true
                )
            }
        }
        PlayerEvent.OnShowSubtitleOverlay -> {
            _uiState.update {
                it.copy(
                    showSubtitleOverlay = true,
                    showAudioOverlay = false,
                    showMoreDialog = false,
                    showControls = true
                )
            }
        }
        is PlayerEvent.OnSetSubtitleDelay -> {
            setSubtitleDelayMs(event.delayMs)
        }
        PlayerEvent.OnShowSpeedDialog -> {
            val state = _uiState.value
            if (state.tunnelingEnabled) {
                _uiState.update {
                    it.copy(
                        showAspectRatioIndicator = true,
                        aspectRatioIndicatorText = context.getString(R.string.player_aspect_tunneling_unavailable)
                    )
                }
                hideAspectRatioIndicatorJob?.cancel()
                hideAspectRatioIndicatorJob = scope.launch {
                    delay(1500)
                    _uiState.update { it.copy(showAspectRatioIndicator = false) }
                }
                return
            }
            _uiState.update {
                it.copy(
                    showSpeedDialog = true,
                    showAudioOverlay = false,
                    showSubtitleOverlay = false,
                    showMoreDialog = false,
                    showControls = true
                )
            }
        }
        PlayerEvent.OnShowMoreDialog -> {
            _uiState.update {
                it.copy(
                    showMoreDialog = true,
                    showAudioOverlay = false,
                    showSubtitleOverlay = false,
                    showSpeedDialog = false,
                    showControls = true
                )
            }
        }
        PlayerEvent.OnDismissMoreDialog -> {
            _uiState.update { it.copy(showMoreDialog = false) }
            scheduleHideControls()
        }
        PlayerEvent.OnShowEpisodesPanel -> {
            showEpisodesPanel()
        }
        PlayerEvent.OnDismissEpisodesPanel -> {
            dismissEpisodesPanel()
        }
        PlayerEvent.OnBackFromEpisodeStreams -> {
            _uiState.update {
                it.copy(
                    showEpisodeStreams = false,
                    isLoadingEpisodeStreams = false
                )
            }
        }
        is PlayerEvent.OnEpisodeSeasonSelected -> {
            selectEpisodesSeason(event.season)
        }
        is PlayerEvent.OnEpisodeSelected -> {
            loadStreamsForEpisode(event.video)
        }
        PlayerEvent.OnReloadEpisodeStreams -> {
            reloadEpisodeStreams()
        }
        is PlayerEvent.OnEpisodeAddonFilterSelected -> {
            filterEpisodeStreamsByAddon(event.addonName)
        }
        is PlayerEvent.OnEpisodeStreamSelected -> {
            switchToEpisodeStream(event.stream)
        }
        PlayerEvent.OnShowSourcesPanel -> {
            showSourcesPanel()
        }
        PlayerEvent.OnDismissSourcesPanel -> {
            dismissSourcesPanel()
        }
        PlayerEvent.OnReloadSourceStreams -> {
            loadSourceStreams(forceRefresh = true)
        }
        is PlayerEvent.OnSourceAddonFilterSelected -> {
            filterSourceStreamsByAddon(event.addonName)
        }
        is PlayerEvent.OnSourceStreamSelected -> {
            switchToSourceStream(event.stream)
        }
        PlayerEvent.OnDismissTransientOverlay -> {
            _uiState.update {
                it.copy(
                    showAudioOverlay = false,
                    showSubtitleOverlay = false,
                    showSpeedDialog = false,
                    showMoreDialog = false
                )
            }
            scheduleHideControls()
        }
        PlayerEvent.OnRetry -> {
            hasRenderedFirstFrame = false
            endDetectionArmed = false
            mpvEofSeenClear = false
            hasRetriedCurrentStreamAfter416 = false
            resetErrorRetryState()
            clearPendingEngineSwitchTrackPreference()
            resetPostPlayOverlayState(clearEpisode = false)
            _uiState.update { state ->
                state.copy(
                    error = null,
                    showLoadingOverlay = state.loadingOverlayEnabled,
                )
            }
            releasePlayer()
            initializePlayer(currentStreamUrl, currentHeaders)
        }
        PlayerEvent.OnParentalGuideHide -> {
            _uiState.update { it.copy(showParentalGuide = false) }
        }
        is PlayerEvent.OnShowDisplayModeInfo -> {
            _uiState.update {
                it.copy(
                    displayModeInfo = event.info,
                    showDisplayModeInfo = true
                )
            }
        }
        PlayerEvent.OnHideDisplayModeInfo -> {
            _uiState.update { it.copy(showDisplayModeInfo = false) }
        }
        PlayerEvent.OnDismissPauseOverlay -> {
            cancelPauseOverlay()
        }
        PlayerEvent.OnSkipIntro -> {
            skipActiveInterval()
        }
        PlayerEvent.OnDismissSkipIntro -> {
            _uiState.update { it.copy(skipIntervalDismissed = true) }
        }
        PlayerEvent.OnPlayNextEpisode -> {
            playNextEpisode(userInitiated = true)
        }
        PlayerEvent.OnDismissNextEpisodeCard -> {
            nextEpisodeAutoPlayJob?.cancel()
            nextEpisodeAutoPlayJob = null
            _uiState.update {
                it.copy(
                    postPlayMode = null,
                    postPlayDismissedForCurrentEpisode = true,
                )
            }
        }
        PlayerEvent.OnStillWatchingContinue -> onStillWatchingContinue()
        PlayerEvent.OnDismissStillWatchingPrompt -> onDismissStillWatchingPrompt()
        PlayerEvent.OnToggleAspectRatio -> {
            val state = _uiState.value
            if (state.tunnelingEnabled) {
                val fill = !state.tunneledSurfaceFill
                val label = PlayerDisplayModeUtils.resizeModeLabel(
                    PlayerDisplayModeUtils.exoSurfaceResizeMode(
                        tunnelingEnabled = true,
                        tunneledSurfaceFill = fill
                    ),
                    context
                )
                Log.d(
                    PlayerRuntimeController.TAG,
                    "Tunneled surface resize toggled: fill=$fill ($label)"
                )
                _uiState.update {
                    it.copy(
                        tunneledSurfaceFill = fill,
                        showAspectRatioIndicator = true,
                        aspectRatioIndicatorText = label
                    )
                }
                scope.launch {
                    deviceLocalPlayerPreferences.setTunneledSurfaceFill(fill)
                }
                hideAspectRatioIndicatorJob?.cancel()
                hideAspectRatioIndicatorJob = scope.launch {
                    delay(1500)
                    _uiState.update { it.copy(showAspectRatioIndicator = false) }
                }
                return
            }
            val newMode = nextAspectMode(state.aspectMode)
            val label = aspectModeLabel(newMode, context::getString)
            Log.d(PlayerRuntimeController.TAG, "Aspect mode toggled by user: ${state.aspectMode} -> $newMode ($label)")
            _uiState.update {
                it.copy(
                    aspectMode = newMode,
                    showAspectRatioIndicator = true,
                    aspectRatioIndicatorText = label
                )
            }
            scope.launch {
                Log.d(PlayerRuntimeController.TAG, "Persisting aspect mode: $newMode")
                deviceLocalPlayerPreferences.setAspectMode(newMode)
            }
            hideAspectRatioIndicatorJob?.cancel()
            hideAspectRatioIndicatorJob = scope.launch {
                delay(1500)
                _uiState.update { it.copy(showAspectRatioIndicator = false) }
            }
        }
        PlayerEvent.OnSwitchInternalPlayerEngine -> {
            logSwitchTrace(
                stage = "event-switch-engine",
                message = "requestedByUser=true"
            )
            switchInternalPlayerEngineManually()
        }
        PlayerEvent.OnSwitchToMpvPlayer -> {
            logSwitchTrace(
                stage = "event-switch-to-mpv",
                message = "requestedByUser=true"
            )
            switchToInternalPlayerEngine(InternalPlayerEngine.MVP_PLAYER, reason = "user-error-dialog-switch-to-mpv")
        }
        PlayerEvent.OnShowStreamInfo -> {
            val info = buildStreamInfoData()
            _uiState.update {
                it.copy(
                    showStreamInfoOverlay = true,
                    streamInfoData = info,
                    showControls = true
                )
            }
        }
        PlayerEvent.OnDismissStreamInfo -> {
            _uiState.update { it.copy(showStreamInfoOverlay = false) }
        }
        PlayerEvent.OnTogglePlayerStatsHud -> {
            val currentState = _uiState.value
            if (currentState.playerStatsHudButtonAvailable) {
                val newActive = !currentState.playerStatsHudEnabled
                scope.launch {
                    deviceLocalPlayerPreferences.setPlayerStatsHudActive(newActive)
                }
            }
        }
    }
}

internal fun PlayerRuntimeController.buildStreamInfoData(): StreamInfoData {
    val state = _uiState.value
    val selectedAudio = state.audioTracks.firstOrNull { it.isSelected }
    val selectedSubtitle = state.subtitleTracks.firstOrNull { it.isSelected }
    val addonSub = state.selectedAddonSubtitle

    val activeVideoFormat = _exoPlayer?.videoFormat
    val matchedFormat = _exoPlayer?.currentTracks?.groups
        ?.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && it.isSelected }
        ?.let { group ->
            (0 until group.length)
                .map { group.getTrackFormat(it) }
                .firstOrNull { it.id == activeVideoFormat?.id || (it.bitrate > 0 && it.bitrate == activeVideoFormat?.bitrate) }
        }

    val videoWidth = matchedFormat?.width?.takeIf { it > 0 } ?: activeVideoFormat?.width?.takeIf { it > 0 } ?: currentVideoWidth
    val videoHeight = matchedFormat?.height?.takeIf { it > 0 } ?: activeVideoFormat?.height?.takeIf { it > 0 } ?: currentVideoHeight
    val videoBitrate = activeVideoFormat?.bitrate?.takeIf { it > 0 } ?: currentVideoBitrate
    val videoCodec = activeVideoFormat?.let { format ->
        CustomDefaultTrackNameProvider.formatNameFromMime(format.sampleMimeType)
            ?: CustomDefaultTrackNameProvider.formatNameFromMime(format.codecs)
    } ?: currentVideoCodec

    return StreamInfoData(
        addonName = currentAddonName,
        addonLogo = currentAddonLogo,
        streamName = state.currentStreamName,
        streamDescription = currentStreamDescription,
        filename = currentFilename,
        fileSize = currentVideoSize,
        videoCodec = videoCodec,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        videoFrameRate = state.detectedFrameRate.takeIf { it > 0f },
        videoBitrate = videoBitrate,
        fileBitrate = PlayerBitrateEstimator.fileBitrateBps(
            currentVideoSize,
            playbackTimeline.value.duration
        ),
        audioCodec = selectedAudio?.codec,
        audioChannels = selectedAudio?.channelCount?.let {
            CustomDefaultTrackNameProvider.getChannelLayoutName(it)
        },
        audioSampleRate = selectedAudio?.sampleRate,
        audioLanguage = selectedAudio?.language,
        subtitleName = selectedSubtitle?.name ?: addonSub?.lang,
        subtitleCodec = selectedSubtitle?.codec,
        subtitleLanguage = selectedSubtitle?.language ?: addonSub?.lang,
        subtitleSource = when {
            addonSub != null -> context.getString(R.string.stream_info_subtitle_source_addon)
            selectedSubtitle != null -> context.getString(R.string.stream_info_subtitle_source_embedded)
            else -> null
        },
        playerEngine = when (currentInternalPlayerEngine) {
            com.nuvio.tv.data.local.InternalPlayerEngine.EXOPLAYER -> context.getString(R.string.playback_engine_exoplayer)
            com.nuvio.tv.data.local.InternalPlayerEngine.MVP_PLAYER -> context.getString(R.string.playback_engine_mvplayer)
            com.nuvio.tv.data.local.InternalPlayerEngine.AUTO -> null
        }
    )
}
