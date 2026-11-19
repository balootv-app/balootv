@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.ui.theme.NuvioMotion

import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.theme.accentBrush

import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RawRes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.core.player.PlayerWindowBackdrop
import com.nuvio.tv.ui.util.localizeEpisodeTitle
import com.nuvio.tv.data.local.InternalPlayerEngine
import com.nuvio.tv.data.local.LibassRenderType
import com.nuvio.tv.data.local.SubtitleStyleSettings
import com.nuvio.tv.data.local.StreamAutoPlayMode
import com.nuvio.tv.domain.model.Subtitle
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.ui.components.LoadingIndicator
import android.text.format.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.media3.exoplayer.ExoPlayer
import io.github.peerless2012.ass.media.widget.AssSubtitleView
import kotlin.math.abs

private fun PlayerUiState.returnFocusSeasonEpisode(completed: Boolean): Pair<Int?, Int?> {
    val next = nextEpisode?.takeIf {
        it.hasAired && !(it.released.isNullOrBlank() && it.available == false)
    }
    return if (completed && next != null) {
        next.season to next.episode
    } else {
        currentSeason to currentEpisode
    }
}

internal fun playerBackTreatsSkipAsAutoPlay(
    streamAutoPlayMode: StreamAutoPlayMode,
    preferBingeGroupForNextEpisode: Boolean
): Boolean = streamAutoPlayMode != StreamAutoPlayMode.MANUAL || preferBingeGroupForNextEpisode

internal fun playerBackOpensCurrentEpisodeStreams(
    episodeChangedInPlace: Boolean,
    autoPlayEnabled: Boolean
): Boolean = episodeChangedInPlace && !autoPlayEnabled

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onBackPress: (currentVideoId: String?, currentSeason: Int?, currentEpisode: Int?, autoPlayEnabled: Boolean, playbackCompleted: Boolean) -> Unit,
    onPlaybackErrorBack: () -> Unit = { onBackPress(null, null, null, false, false) },
    onPlaybackEnded: ((nextVideoId: String?, nextSeason: Int?, nextEpisode: Int?, exitReason: PlayerExitReason?) -> Unit)? = null,
    onPlayRecommendation: (PostPlayRecommendation, manualSelection: Boolean) -> Unit = { _, _ -> },
    onOpenRecommendationDetails: (PostPlayRecommendation) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val postPlayRecommendationState by viewModel.postPlayRecommendationUiState.collectAsState()
    val effectiveAutoplayEnabled by viewModel.effectiveAutoplayEnabled.collectAsState(initial = false)
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val containerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val progressBarFocusRequester = remember { FocusRequester() }
    val episodesFocusRequester = remember { FocusRequester() }
    val streamsFocusRequester = remember { FocusRequester() }
    val sourceStreamsFocusRequester = remember { FocusRequester() }
    val skipIntroFocusRequester = remember { FocusRequester() }
    val streamInfoFocusRequester = remember { FocusRequester() }
    val postPlayRecommendationFocusRequester = remember { FocusRequester() }
    val postPlayRecommendationPlayerWindowFocusRequester = remember { FocusRequester() }
    var skipButtonActuallyVisible by remember { mutableStateOf(false) }
    var restoreStreamInfoFocus by remember { mutableStateOf(false) }
    var focusPlayAfterMoreBack by remember { mutableStateOf(false) }
    var hideControlsAfterStreamInfo by remember { mutableStateOf(false) }
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    val poscreditosFocusRequester = remember { FocusRequester() }
    var exitDispatched by remember { mutableStateOf(false) }
    var externalHandoffInProgress by remember { mutableStateOf(false) }
    val autoPlayEnabledForBack = playerBackTreatsSkipAsAutoPlay(
        streamAutoPlayMode = uiState.streamAutoPlayMode,
        preferBingeGroupForNextEpisode = uiState.streamAutoPlayPreferBingeGroupForNextEpisode
    )

    val exitPlayer: () -> Unit = exitPlayer@{
        if (exitDispatched) return@exitPlayer
        exitDispatched = true
        val timeline = viewModel.playbackTimeline.value
        viewModel.stopAndRelease()
        val completed = postPlayRecommendationState.isVisible || uiState.playbackEnded ||
            (!timeline.isLive &&
                timeline.duration > 0L &&
                (timeline.currentPosition.toFloat() / timeline.duration.toFloat()) >= WatchProgress.COMPLETED_THRESHOLD)
        val (focusSeason, focusEpisode) = uiState.returnFocusSeasonEpisode(completed)
        onBackPress(
            uiState.currentVideoId,
            focusSeason,
            focusEpisode,
            autoPlayEnabledForBack,
            completed
        )
    }
    val exitPlayerFromError: () -> Unit = exitPlayerFromError@{
        if (exitDispatched) return@exitPlayerFromError
        exitDispatched = true
        viewModel.stopAndRelease()
        onPlaybackErrorBack()
    }
    val dismissStreamInfoOverlay = {
        viewModel.onEvent(PlayerEvent.OnDismissStreamInfo)
    }
    val returnToPlayerFromPostPlay = {
        viewModel.returnToPlayerFromPostPlay()
    }

    val currentOnPlaybackEnded by rememberUpdatedState(onPlaybackEnded)
    val currentOnBackPress by rememberUpdatedState(onBackPress)
    val currentOnPlayRecommendation by rememberUpdatedState(onPlayRecommendation)
    val currentOnOpenRecommendationDetails by rememberUpdatedState(onOpenRecommendationDetails)
    val nextEpisodeForEndPrompt = uiState.nextEpisode?.takeIf {
        it.hasAired && !(it.released.isNullOrBlank() && it.available == false)
    }
    val shouldConfirmNextEpisodeOnEnd =
        uiState.playbackEnded &&
            uiState.error == null &&
            (uiState.streamAutoPlayMode != StreamAutoPlayMode.MANUAL ||
                uiState.streamAutoPlayPreferBingeGroupForNextEpisode) &&
            !uiState.streamAutoPlayNextEpisodeEnabled &&
            nextEpisodeForEndPrompt != null
    val returnToDetailsFromEndPrompt = {
        viewModel.stopAndRelease()
        val (focusSeason, focusEpisode) = uiState.returnFocusSeasonEpisode(completed = true)
        currentOnBackPress(
            uiState.currentVideoId,
            focusSeason,
            focusEpisode,
            true,
            true
        )
    }
    val continueToNextEpisodeFromEndPrompt = {
        val next = nextEpisodeForEndPrompt
        if (next != null) {
            viewModel.stopAndRelease()
            val cb = currentOnPlaybackEnded
            if (cb != null) {
                cb(next.videoId, next.season, next.episode, null)
            } else {
                val (focusSeason, focusEpisode) = uiState.returnFocusSeasonEpisode(completed = true)
                currentOnBackPress(
                    uiState.currentVideoId,
                    focusSeason,
                    focusEpisode,
                    false,
                    true
                )
            }
        }
    }

    // BalooTV (C6): la tarjeta de la escena poscréditos a la vista (ir a ella o salir), si nada la tapa.
    val tarjetaPoscreditos = uiState.poscreditos?.takeIf {
        (it is com.nuvio.tv.balootv.reproductor.Poscreditos.Aviso.IrALaEscena ||
            it == com.nuvio.tv.balootv.reproductor.Poscreditos.Aviso.SinEscena) &&
            !uiState.showPauseOverlay && !uiState.showLoadingOverlay && !postPlayRecommendationState.isVisible
    }

    val handleBackPress = handleBackPress@{
        if (externalHandoffInProgress) return@handleBackPress
        if (postPlayRecommendationState.canReturnToPlayer && !uiState.playbackEnded) {
            returnToPlayerFromPostPlay()
            viewModel.hideControls()
        } else if (postPlayRecommendationState.isVisible || postPlayRecommendationState.isLoadingRecommendation) {
            exitPlayer()
        } else if (shouldConfirmNextEpisodeOnEnd) {
            returnToDetailsFromEndPrompt()
        } else if (uiState.error != null) {
            exitPlayerFromError()
        } else if (uiState.showAudioOverlay || uiState.showSubtitleOverlay) {
            viewModel.onEvent(PlayerEvent.OnDismissTransientOverlay)
        } else if (uiState.showStreamInfoOverlay) {
            dismissStreamInfoOverlay()
            if (hideControlsAfterStreamInfo) {
                viewModel.hideControls()
            } else if (uiState.showControls) {
                restoreStreamInfoFocus = true
                viewModel.onEvent(PlayerEvent.OnShowMoreDialog)
            }
            hideControlsAfterStreamInfo = false
        } else if (uiState.showPauseOverlay) {
            viewModel.onEvent(PlayerEvent.OnDismissPauseOverlay)
        } else if (uiState.showMoreDialog) {
            focusPlayAfterMoreBack = true
            viewModel.onEvent(PlayerEvent.OnDismissMoreDialog)
        } else if (uiState.showSourcesPanel) {
            if (uiState.currentStreamUrl.isNullOrBlank()) {
                exitPlayer()
            } else {
                viewModel.onEvent(PlayerEvent.OnDismissSourcesPanel)
            }
        } else if (uiState.showEpisodesPanel) {
            if (uiState.showEpisodeStreams) {
                viewModel.onEvent(PlayerEvent.OnBackFromEpisodeStreams)
            } else {
                viewModel.onEvent(PlayerEvent.OnDismissEpisodesPanel)
            }
        } else if (uiState.postPlayMode is PostPlayMode.AutoPlay &&
            postPlayRecommendationState.recommendation == null
        ) {
            viewModel.onEvent(PlayerEvent.OnDismissNextEpisodeCard)
            if (skipButtonActuallyVisible) {
                runCatching { skipIntroFocusRequester.requestFocus() }
            } else {
                runCatching { containerFocusRequester.requestFocus() }
            }
        } else if (tarjetaPoscreditos != null && !uiState.showControls) {
            viewModel.onEvent(PlayerEvent.OnDescartarPoscreditos)
            runCatching { containerFocusRequester.requestFocus() }
        } else if (skipButtonActuallyVisible && !uiState.showControls) {
            viewModel.onEvent(PlayerEvent.OnDismissSkipIntro)
        } else if (uiState.postPlayMode is PostPlayMode.StillWatching) {
            viewModel.onEvent(PlayerEvent.OnDismissStillWatchingPrompt)
        } else if (uiState.showControls) {
            viewModel.hideControls()
        } else if (!viewModel.avisarPoscreditosAlSalir()) {
            exitPlayer()
        }
    }

    BackHandler {
        handleBackPress()
    }

    // Un «reproduce esto» del móvil con este título en marcha: se sale por el mismo camino que con Atrás.
    val salirActual by rememberUpdatedState(exitPlayer)
    LaunchedEffect(Unit) {
        com.nuvio.tv.balootv.reproductor.EnlaceDelMovil.cerrarReproductor.collect { salirActual() }
    }

    LaunchedEffect(
        uiState.playbackEnded,
        uiState.error,
        uiState.pendingExitReason,
        shouldConfirmNextEpisodeOnEnd,
        postPlayRecommendationState.blocksNaturalCompletion
    ) {
        val explicitReason = uiState.pendingExitReason
        val shouldDispatchNatural = uiState.playbackEnded &&
            uiState.error == null &&
            uiState.postPlayMode?.blocksNaturalCompletion() != true &&
            !postPlayRecommendationState.blocksNaturalCompletion &&
            !shouldConfirmNextEpisodeOnEnd &&
            explicitReason == null
        when {
            explicitReason == PlayerExitReason.StillWatchingPrompt -> {
                viewModel.stopAndRelease()
                val cb = currentOnPlaybackEnded
                if (cb != null) {
                    cb(null, null, null, PlayerExitReason.StillWatchingPrompt)
                } else {
                    val (focusSeason, focusEpisode) = uiState.returnFocusSeasonEpisode(completed = true)
                    currentOnBackPress(
                        uiState.currentVideoId,
                        focusSeason,
                        focusEpisode,
                        autoPlayEnabledForBack,
                        true
                    )
                }
                viewModel.consumePendingExitReason()
            }
            shouldDispatchNatural -> {
                viewModel.stopAndRelease()
                val next = uiState.nextEpisode?.takeIf {
                    it.hasAired && !(it.released.isNullOrBlank() && it.available == false)
                }
                val cb = currentOnPlaybackEnded
                if (cb != null) {
                    cb(next?.videoId, next?.season, next?.episode, null)
                } else {
                    val (focusSeason, focusEpisode) = uiState.returnFocusSeasonEpisode(completed = true)
                    currentOnBackPress(
                        uiState.currentVideoId,
                        focusSeason,
                        focusEpisode,
                        autoPlayEnabledForBack,
                        true
                    )
                }
            }
        }
    }

    // Handle lifecycle events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.pauseForLifecycle()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Re-create the MediaSession so media controls work in foreground.
                    // Don't auto-resume playback — let the user press play.
                    viewModel.resumeForLifecycle()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Bump UI thread priority to THREAD_PRIORITY_DISPLAY (-4) while the player is active.
    // The Linux scheduler favors the thread under CPU pressure (background addon prefetch,
    // Trakt sync, image decode), reducing dropped frames at scene cuts and during decoder
    // spin-up. Restored on dispose so non-player screens stay at default priority.
    DisposableEffect(Unit) {
        val tid = android.os.Process.myTid()
        val previousPriority = runCatching { android.os.Process.getThreadPriority(tid) }.getOrDefault(0)
        runCatching {
            android.os.Process.setThreadPriority(tid, android.os.Process.THREAD_PRIORITY_DISPLAY)
        }
        onDispose {
            runCatching {
                android.os.Process.setThreadPriority(tid, previousPriority)
            }
        }
    }

    // Frame rate matching lifecycle.
    val activity = LocalContext.current as? android.app.Activity
    LaunchedEffect(activity) {
        viewModel.attachHostActivity(activity)
        viewModel.startInitialPlaybackIfNeeded()
    }
    DisposableEffect(activity) {
        onDispose {
            viewModel.attachHostActivity(null)
        }
    }
    LaunchedEffect(uiState.frameRateMatchingMode) {
        if (activity != null &&
            uiState.frameRateMatchingMode == com.nuvio.tv.data.local.FrameRateMatchingMode.OFF
        ) {
            com.nuvio.tv.core.player.FrameRateUtils.restoreOriginalDisplayMode(activity)
        }
    }
    // Restore original display mode when leaving the player
    val currentFrameRateMatchingMode by rememberUpdatedState(uiState.frameRateMatchingMode)
    DisposableEffect(activity) {
        onDispose {
            if (activity != null) {
                if (currentFrameRateMatchingMode == com.nuvio.tv.data.local.FrameRateMatchingMode.START_STOP) {
                    com.nuvio.tv.core.player.FrameRateUtils.restoreOriginalDisplayMode(activity)
                } else {
                    com.nuvio.tv.core.player.FrameRateUtils.cleanupDisplayListener()
                    com.nuvio.tv.core.player.FrameRateUtils.clearOriginalDisplayMode()
                }
            }
        }
    }

    // Request focus for key events when controls visibility or panel state changes
    LaunchedEffect(
        uiState.showControls,
        uiState.showEpisodesPanel,
        uiState.showSourcesPanel,
        uiState.showAudioOverlay,
        uiState.showSubtitleOverlay,
        uiState.showSpeedDialog,
        shouldConfirmNextEpisodeOnEnd,
        postPlayRecommendationState.isVisible,
    ) {
        if (shouldConfirmNextEpisodeOnEnd || postPlayRecommendationState.isVisible) return@LaunchedEffect
        if (uiState.error != null) return@LaunchedEffect
        if (uiState.showControls && !uiState.showEpisodesPanel && !uiState.showSourcesPanel &&
            !uiState.showAudioOverlay && !uiState.showSubtitleOverlay &&
            !uiState.showSpeedDialog
        ) {
            // Wait for AnimatedVisibility animation to complete before focusing play/pause button
            kotlinx.coroutines.delay(250)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus requester may not be ready yet
            }
        } else if (!uiState.showControls) {
            // When controls are hidden, let skip intro button take focus if visible
            val skipVisible = uiState.activeSkipInterval != null && !uiState.skipIntervalDismissed
            val nextEpisodeVisible = uiState.postPlayMode is PostPlayMode.AutoPlay
            if (!skipVisible && !nextEpisodeVisible) {
                try {
                    containerFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Focus requester may not be ready yet
                }
            }
            // If skip or next episode card is visible, their own LaunchedEffect will request focus
        }
    }

    // Initial focus on container - the LaunchedEffect above will handle focusing controls
    LaunchedEffect(Unit) {
        if (uiState.error == null) {
            containerFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(uiState.showStreamInfoOverlay, uiState.showControls, uiState.showMoreDialog) {
        if (!uiState.showStreamInfoOverlay && uiState.showControls && uiState.showMoreDialog && restoreStreamInfoFocus) {
            delay(250)
            runCatching { streamInfoFocusRequester.requestFocus() }
            restoreStreamInfoFocus = false
        }
    }
    val moreDialogOpen by rememberUpdatedState(uiState.showMoreDialog)
    val controlsVisibleForMoreBack by rememberUpdatedState(uiState.showControls)
    val playerHasError by rememberUpdatedState(uiState.error != null)
    LaunchedEffect(focusPlayAfterMoreBack) {
        if (!focusPlayAfterMoreBack) return@LaunchedEffect
        runCatching { playPauseFocusRequester.requestFocus() }
        delay(200)
        if (!moreDialogOpen && controlsVisibleForMoreBack && !playerHasError) {
            playPauseFocusRequester.requestFocusAfterFrames(frames = 0)
        }
        focusPlayAfterMoreBack = false
    }

    val transparentLetterbox = uiState.transparentLetterbox &&
        uiState.internalPlayerEngine != InternalPlayerEngine.MVP_PLAYER
    DisposableEffect(transparentLetterbox) {
        if (!transparentLetterbox) {
            return@DisposableEffect onDispose {}
        }
        PlayerWindowBackdrop.acquireTransparent()
        onDispose { PlayerWindowBackdrop.releaseTransparent() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (transparentLetterbox) Modifier else Modifier.background(Color.Black))
            .focusRequester(containerFocusRequester)
            .focusable(enabled = uiState.error == null)
            .onPreviewKeyEvent { keyEvent ->
                val postPlayHandlesBack = postPlayRecommendationState.isVisible ||
                    postPlayRecommendationState.isLoadingRecommendation ||
                    postPlayRecommendationState.isTrailerPlaying
                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE ||
                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK && !postPlayHandlesBack)
                ) {
                    return@onPreviewKeyEvent when (keyEvent.nativeKeyEvent.action) {
                        KeyEvent.ACTION_DOWN -> true
                        KeyEvent.ACTION_UP -> {
                            handleBackPress()
                            true
                        }
                        else -> true
                    }
                }

                if (keyEvent.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_CAPTIONS) {
                    return@onPreviewKeyEvent false
                }

                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_UP) {
                    return@onPreviewKeyEvent true
                }

                if (
                    !uiState.showEpisodesPanel &&
                    !uiState.showSourcesPanel &&
                    !uiState.showAudioOverlay &&
                    !uiState.showSubtitleOverlay &&
                    !uiState.showSpeedDialog
                ) {
                    viewModel.onEvent(PlayerEvent.OnShowSubtitleOverlay)
                }
                true
            }
            .onKeyEvent { keyEvent ->
                // When a side panel or dialog is open, let it handle all keys
                val panelOrDialogOpen = uiState.showEpisodesPanel || uiState.showSourcesPanel ||
                        uiState.showAudioOverlay || uiState.showSubtitleOverlay ||
                        uiState.showSpeedDialog ||
                        uiState.showMoreDialog ||
                        shouldConfirmNextEpisodeOnEnd ||
                        uiState.postPlayMode is PostPlayMode.StillWatching ||
                        postPlayRecommendationState.isVisible ||
                        uiState.avisoAnimeSinSubs != null ||
                        uiState.error != null
                if (panelOrDialogOpen) return@onKeyEvent false

                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!uiState.showControls) {
                                viewModel.onEvent(PlayerEvent.OnCommitPreviewSeek)
                                return@onKeyEvent true
                            }
                        }
                    }
                    return@onKeyEvent false
                }

                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    if (uiState.showPauseOverlay) {
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                // Resume directly from pause overlay in one click.
                                viewModel.onEvent(PlayerEvent.OnPlayPause)
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE,
                            KeyEvent.KEYCODE_MEDIA_STOP -> {
                            }
                            else -> {
                                viewModel.onEvent(PlayerEvent.OnDismissPauseOverlay)
                            }
                        }
                        return@onKeyEvent true
                    }
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (!uiState.showControls) {
                                // BalooTV: como en tvOS, con los controles ocultos OK ejecuta la acción contextual
                                // que esté a la vista aunque el foco no haya llegado a ella; si no hay, pausa.
                                val siguiente = uiState.postPlayMode as? PostPlayMode.AutoPlay
                                when {
                                    tarjetaPoscreditos is com.nuvio.tv.balootv.reproductor.Poscreditos.Aviso.IrALaEscena ->
                                        viewModel.onEvent(PlayerEvent.OnIrAPoscreditos)
                                    tarjetaPoscreditos != null -> viewModel.onEvent(PlayerEvent.OnSalirSinPoscreditos)
                                    skipButtonActuallyVisible -> viewModel.onEvent(PlayerEvent.OnSkipIntro)
                                    siguiente != null && siguiente.nextEpisode.hasAired &&
                                        postPlayRecommendationState.recommendation == null ->
                                        viewModel.onEvent(PlayerEvent.OnPlayNextEpisode)
                                    else -> viewModel.onEvent(PlayerEvent.OnPlayPause)
                                }
                                true
                            } else {
                                // Let the focused button handle it
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val overlayButtonsCoexist = skipButtonActuallyVisible &&
                                uiState.postPlayMode is PostPlayMode.AutoPlay
                            if (!uiState.showControls && !overlayButtonsCoexist) {
                                val isLeft =
                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                                val deltaMs = PlayerScrubRates.deltaMsForKeyRepeat(
                                    repeatCount = keyEvent.nativeKeyEvent.repeatCount,
                                    forward = !isLeft
                                )
                                viewModel.onEvent(PlayerEvent.OnPreviewSeekBy(deltaMs))
                                true
                            } else {
                                // Let focus system handle navigation when controls are visible
                                // or both skip and next-episode buttons are on screen
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                                if (!uiState.showControls) {
                                    viewModel.onEvent(PlayerEvent.OnToggleControls)
                                } else {
                                    try {
                                        progressBarFocusRequester.requestFocus()
                                    } catch (_: Exception) {
                                        val skipVisible = skipButtonActuallyVisible
                                        if (skipVisible) {
                                            try {
                                                skipIntroFocusRequester.requestFocus()
                                            } catch (_: Exception) {
                                            }
                                        } else if (uiState.postPlayMode is PostPlayMode.AutoPlay) {
                                            try {
                                                nextEpisodeFocusRequester.requestFocus()
                                            } catch (_: Exception) {
                                            }
                                        } else {
                                            viewModel.hideControls()
                                        }
                                    }
                                }
                                true
                            }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!uiState.showControls) {
                                viewModel.onEvent(PlayerEvent.OnToggleControls)
                                true
                            } else {
                                // Let focus system handle navigation when controls are visible
                                false
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            viewModel.onEvent(PlayerEvent.OnPlayPause)
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            if (!uiState.isPlaying) {
                                viewModel.onEvent(PlayerEvent.OnPlayPause)
                            }
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            if (uiState.isPlaying) {
                                viewModel.onEvent(PlayerEvent.OnPlayPause)
                            }
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_STOP -> {
                            viewModel.onEvent(PlayerEvent.OnPlayPause)
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            viewModel.onEvent(
                                PlayerEvent.OnSeekBy(
                                    PlayerScrubRates.deltaMsForKeyRepeat(
                                        repeatCount = keyEvent.nativeKeyEvent.repeatCount,
                                        forward = true
                                    )
                                )
                            )
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            viewModel.onEvent(
                                PlayerEvent.OnSeekBy(
                                    PlayerScrubRates.deltaMsForKeyRepeat(
                                        repeatCount = keyEvent.nativeKeyEvent.repeatCount,
                                        forward = false
                                    )
                                )
                            )
                            true
                        }
                        KeyEvent.KEYCODE_INFO -> {
                            if (uiState.showStreamInfoOverlay) {
                                viewModel.onEvent(PlayerEvent.OnDismissStreamInfo)
                                if (hideControlsAfterStreamInfo) {
                                    viewModel.hideControls()
                                } else if (uiState.showControls) {
                                    restoreStreamInfoFocus = true
                                    viewModel.onEvent(PlayerEvent.OnShowMoreDialog)
                                }
                                hideControlsAfterStreamInfo = false
                            } else {
                                hideControlsAfterStreamInfo = !uiState.showControls
                                viewModel.onEvent(PlayerEvent.OnShowStreamInfo)
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        val postPlayRecommendationPlayerWidth by animateFloatAsState(
            targetValue = if (postPlayRecommendationState.isVisible) 0.32f else 1f,
            animationSpec = tween(durationMillis = POST_PLAY_RECOMMENDATION_TRANSITION_MS),
            label = "postPlayRecommendationPlayerWidth"
        )
        val postPlayRecommendationPlayerPadding by animateDpAsState(
            targetValue = if (postPlayRecommendationState.isVisible) NuvioTheme.spacing.xxl else 0.dp,
            animationSpec = tween(durationMillis = POST_PLAY_RECOMMENDATION_TRANSITION_MS),
            label = "postPlayRecommendationPlayerPadding"
        )
        val postPlayRecommendationPlayerCornerRadius by animateDpAsState(
            targetValue = if (postPlayRecommendationState.isVisible) 12.dp else 0.dp,
            animationSpec = tween(durationMillis = POST_PLAY_RECOMMENDATION_TRANSITION_MS),
            label = "postPlayRecommendationPlayerCornerRadius"
        )
        val postPlayRecommendationPlayerBorderAlpha by animateFloatAsState(
            targetValue = if (postPlayRecommendationState.isVisible) 0.2f else 0f,
            animationSpec = tween(durationMillis = POST_PLAY_RECOMMENDATION_TRANSITION_MS),
            label = "postPlayRecommendationPlayerBorderAlpha"
        )
        val playerSurfaceShape = RoundedCornerShape(postPlayRecommendationPlayerCornerRadius)
        val playerSurfaceIsFullscreen = !postPlayRecommendationState.isVisible &&
            postPlayRecommendationPlayerWidth >= 0.999f
        var subtitleControlsClearancePx by remember { mutableFloatStateOf(0f) }
        var skipClearancePx by remember { mutableFloatStateOf(0f) }
        var postPlayClearancePx by remember { mutableFloatStateOf(0f) }
        var seekClearancePx by remember { mutableFloatStateOf(0f) }
        val subtitleClearanceGapPx = with(LocalDensity.current) { SUBTITLE_CONTROLS_GAP.toPx() }
        val postPlayOverlayMode = uiState.postPlayMode.takeIf {
            uiState.error == null &&
                !postPlayRecommendationState.isVisible &&
                postPlayRecommendationState.recommendation == null &&
                !shouldConfirmNextEpisodeOnEnd &&
                !uiState.showLoadingOverlay &&
                !uiState.showPauseOverlay &&
                !uiState.showStreamInfoOverlay &&
                !uiState.showEpisodesPanel &&
                !uiState.showSourcesPanel &&
                !uiState.showAudioOverlay &&
                !uiState.showSubtitleOverlay &&
                !uiState.showSpeedDialog &&
                !uiState.showMoreDialog
        }
        val seekOverlayVisible = uiState.showSeekOverlay && !uiState.showControls && uiState.error == null &&
            !uiState.showLoadingOverlay && !uiState.showPauseOverlay &&
            !uiState.showMoreDialog &&
            !uiState.isLive
        val playerControlsVisible = uiState.showControls && uiState.error == null &&
            !uiState.showLoadingOverlay && !uiState.showPauseOverlay &&
            !uiState.showStreamInfoOverlay &&
            !uiState.showEpisodesPanel &&
            !uiState.showSourcesPanel &&
            !uiState.showAudioOverlay &&
            !uiState.showSubtitleOverlay &&
            !uiState.showSpeedDialog &&
            !postPlayRecommendationState.isVisible &&
            uiState.postPlayMode !is PostPlayMode.StillWatching
        val fallbackSubtitleLiftPx = with(LocalDensity.current) { SUBTITLE_CONTROLS_LIFT.toPx() }
        val playerSurfaceModifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = postPlayRecommendationPlayerPadding, top = postPlayRecommendationPlayerPadding)
            .fillMaxWidth(postPlayRecommendationPlayerWidth)
            .aspectRatio(16f / 9f)
            .clip(playerSurfaceShape)
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = postPlayRecommendationPlayerBorderAlpha)),
                playerSurfaceShape
            )
            .then(
                if (transparentLetterbox && playerSurfaceIsFullscreen) Modifier
                else Modifier.background(Color.Black)
            )
            .zIndex(
                if (postPlayRecommendationState.isVisible || postPlayRecommendationPlayerWidth < 0.999f) {
                    2.2f
                } else {
                    0f
                }
            )

        if (!exitDispatched &&
            !postPlayRecommendationState.isTrailerPlaying &&
            (!postPlayRecommendationState.isVisible || !postPlayRecommendationState.hasAutoPlayedTrailer)
        ) {
            LaunchedEffect(postPlayRecommendationState.isVisible) {
                val playerView = viewModel.controller.exoPlayerView
                val vis = if (postPlayRecommendationState.isVisible) {
                    android.view.View.GONE
                } else {
                    android.view.View.VISIBLE
                }
                playerView?.subtitleView?.visibility = vis
                playerView?.setAssOverlayVisibility(vis)
            }

            Box(modifier = playerSurfaceModifier) {
                val subtitleLiftPx = computeSubtitleLiftPx(
                    previewConfiguredPosition = uiState.showSubtitleOverlay,
                    controlsVisible = playerControlsVisible,
                    controlsClearancePx = subtitleControlsClearancePx,
                    fallbackControlsLiftPx = fallbackSubtitleLiftPx,
                    // La tarjeta de la escena poscréditos ocupa el sitio del botón de saltar, que se esconde con ella.
                    skipVisible = skipButtonActuallyVisible || tarjetaPoscreditos != null,
                    skipClearancePx = skipClearancePx,
                    postPlayVisible = postPlayOverlayMode != null,
                    postPlayClearancePx = postPlayClearancePx,
                    seekVisible = seekOverlayVisible,
                    seekClearancePx = seekClearancePx
                )
                if (uiState.internalPlayerEngine == InternalPlayerEngine.MVP_PLAYER) {
                    MpvPlayerSurface(
                        viewModel = viewModel,
                        isPlaying = uiState.isPlaying,
                        isBuffering = uiState.isBuffering,
                        aspectMode = uiState.aspectMode,
                        subtitleStyle = uiState.subtitleStyle,
                        subtitleLiftPx = subtitleLiftPx,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    viewModel.exoPlayer?.let { player ->
                        ExoPlayerSurface(
                            player = player,
                            controller = viewModel.controller,
                            isPlaying = uiState.isPlaying,
                            isBuffering = uiState.isBuffering,
                            aspectMode = uiState.aspectMode,
                            tunnelingEnabled = uiState.tunnelingEnabled,
                            tunneledSurfaceFill = uiState.tunneledSurfaceFill,
                            useLibass = uiState.useLibass,
                            libassRenderType = uiState.libassRenderType,
                            subtitleStyle = uiState.subtitleStyle,
                            subtitleLiftPx = subtitleLiftPx,
                            onBindSubtitleView = viewModel::bindExoSubtitleView,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                if (postPlayRecommendationState.canReturnToPlayer) {
                    PostPlayRecommendationPlayerWindow(
                        focusRequester = postPlayRecommendationPlayerWindowFocusRequester,
                        downFocusRequester = postPlayRecommendationFocusRequester,
                        onBack = handleBackPress,
                        onClick = {
                            returnToPlayerFromPostPlay()
                            if (!uiState.showControls) {
                                viewModel.onEvent(PlayerEvent.OnToggleControls)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (!exitDispatched) {
            PostPlayRecommendationOverlay(
                state = postPlayRecommendationState,
                currentTitle = uiState.contentName ?: uiState.title,
                showManualPlayOption = effectiveAutoplayEnabled,
                playFocusRequester = postPlayRecommendationFocusRequester,
                playerWindowFocusRequester = postPlayRecommendationPlayerWindowFocusRequester,
                onBack = handleBackPress,
                onStopTrailer = viewModel::onPostPlayTrailerEnded,
                onPlay = { recommendation ->
                    if (!exitDispatched) {
                        exitDispatched = true
                        viewModel.stopAndRelease()
                        currentOnPlayRecommendation(recommendation, false)
                    }
                },
                onPlayManually = { recommendation ->
                    if (!exitDispatched) {
                        exitDispatched = true
                        viewModel.stopAndRelease()
                        currentOnPlayRecommendation(recommendation, true)
                    }
                },
                onOpenDetails = { recommendation ->
                    if (!exitDispatched) {
                        exitDispatched = true
                        viewModel.stopAndRelease()
                        currentOnOpenRecommendationDetails(recommendation)
                    }
                },
                onPlayTrailer = viewModel::playPostPlayTrailer,
                onTrailerEnded = viewModel::onPostPlayTrailerEnded,
                onPreviousRecommendation = viewModel::showPreviousPostPlayRecommendation,
                onNextRecommendation = viewModel::showNextPostPlayRecommendation,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
            )
        }

        LoadingOverlay(
            visible = uiState.showLoadingOverlay && uiState.error == null && !postPlayRecommendationState.isVisible,
            backdropUrl = uiState.backdrop,
            logoUrl = uiState.logo,
            title = uiState.title,
            message = uiState.loadingMessage.takeIf { uiState.showPlayerLoadingStatus },
            progress = uiState.loadingProgress,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
        )

        // BalooTV: pantalla de pausa propia (la imagen con un velo, la hora y qué estás viendo).
        com.nuvio.tv.balootv.ui.reproductor.PantallaPausa(
            visible = uiState.showPauseOverlay && uiState.error == null &&
                !uiState.showLoadingOverlay && !postPlayRecommendationState.isVisible,
            titulo = uiState.title,
            logo = uiState.logo,
            temporada = uiState.currentSeason.takeIf { uiState.contentType in listOf("series", "tv") },
            episodio = uiState.currentEpisode.takeIf { uiState.contentType in listOf("series", "tv") },
            tituloEpisodio = uiState.currentEpisodeTitle,
            anio = uiState.releaseYear,
            sinopsis = uiState.description,
            queda = {
                val t = viewModel.playbackTimeline.value
                if (uiState.isLive) "" else com.nuvio.tv.balootv.reproductor.TiemposReproductor.enPausa(
                    t.currentPosition, t.duration, java.time.LocalTime.now()
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2.5f)
        )

        StreamInfoOverlay(
            visible = uiState.showStreamInfoOverlay && uiState.error == null &&
                !uiState.showLoadingOverlay && !postPlayRecommendationState.isVisible,
            onClose = dismissStreamInfoOverlay,
            data = uiState.streamInfoData,
            hudEnabled = uiState.playerStatsHudEnabled,
            hudButtonShown = uiState.playerStatsHudButtonAvailable,
            onToggleHud = { viewModel.onEvent(PlayerEvent.OnTogglePlayerStatsHud) },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2.6f)
        )

        if (uiState.playerStatsHudEnabled && uiState.playerStatsHudButtonAvailable && uiState.error == null) {
            PlayerDebugStatsOverlay(
                viewModel = viewModel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = NuvioTheme.spacing.xl, top = NuvioTheme.spacing.xl)
                    .zIndex(2.75f)
            )
        }

        // Buffering indicator — isolated in its own composable scope so that
        // isBuffering state changes only recompose this small subtree instead
        // of the entire PlayerScreen.
        PlayerBufferingIndicator(
            isBuffering = uiState.isBuffering && !postPlayRecommendationState.isVisible,
            showLoadingOverlay = uiState.showLoadingOverlay
        )

        // Error state
        if (uiState.error != null) {
            ErrorOverlay(
                message = uiState.error!!,
                showSwitchToMpvAction = uiState.showSwitchToMpvErrorAction &&
                    uiState.internalPlayerEngine != InternalPlayerEngine.MVP_PLAYER,
                onSwitchToMpv = { viewModel.onEvent(PlayerEvent.OnSwitchToMpvPlayer) },
                onBack = exitPlayerFromError
            )
        }

        val endPromptEpisode = nextEpisodeForEndPrompt.takeIf { shouldConfirmNextEpisodeOnEnd }
        if (endPromptEpisode != null) {
            NextEpisodeEndPromptOverlay(
                nextEpisode = endPromptEpisode,
                onContinue = continueToNextEpisodeFromEndPrompt,
                onReturnToDetails = returnToDetailsFromEndPrompt
            )
        }

        // BalooTV: acciones contextuales propias (saltar y siguiente episodio) abajo a la derecha, sin pisar el
        // título; con los controles a la vista suben por encima de ellos.
        val accionesAbajo by animateDpAsState(
            targetValue = if (uiState.showControls) 150.dp else 40.dp,
            animationSpec = tween(durationMillis = NuvioMotion.tokens.durations.fast),
            label = "accionesAbajo"
        )
        val skipIntroCanFocus = isSkipIntroCanFocus(
            subtitleOverlayVisible = uiState.showSubtitleOverlay,
        )
        // En los créditos manda la tarjeta de siguiente episodio: no se muestran las dos a la vez.
        val tarjetaSiguienteActiva = uiState.postPlayMode != null && postPlayRecommendationState.recommendation == null
        com.nuvio.tv.balootv.ui.reproductor.BotonSaltar(
            tramo = if (uiState.showPauseOverlay || uiState.showLoadingOverlay || postPlayRecommendationState.isVisible || tarjetaSiguienteActiva ||
                com.nuvio.tv.balootv.reproductor.Poscreditos.ocultaSaltar(uiState.poscreditos, uiState.activeSkipTargetsPostCredits)
            ) {
                null
            } else {
                uiState.activeSkipInterval
            },
            descartado = uiState.skipIntervalDismissed,
            aPoscreditos = uiState.activeSkipTargetsPostCredits,
            controlesVisibles = uiState.showControls,
            reproduciendo = uiState.isPlaying && !uiState.isBuffering,
            puedeEnfocar = skipIntroCanFocus,
            foco = skipIntroFocusRequester,
            focoAbajo = if (uiState.showControls) progressBarFocusRequester else null,
            onSaltar = {
                if (uiState.showControls) {
                    runCatching { playPauseFocusRequester.requestFocus() }
                }
                viewModel.onEvent(PlayerEvent.OnSkipIntro)
            },
            onVisible = { skipButtonActuallyVisible = it },
            onEnfocado = { viewModel.scheduleHideControls() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 50.dp, bottom = accionesAbajo)
                .zIndex(2.1f)
                .reportSubtitleClearance(subtitleClearanceGapPx) { skipClearancePx = it }
        )
        com.nuvio.tv.balootv.ui.reproductor.TarjetaSiguienteEpisodio(
            modo = postPlayOverlayMode,
            controlesVisibles = uiState.showControls,
            foco = nextEpisodeFocusRequester,
            focoAbajo = if (uiState.showControls) progressBarFocusRequester else null,
            onReproducir = { viewModel.onEvent(PlayerEvent.OnPlayNextEpisode) },
            onSeguirViendo = { viewModel.onEvent(PlayerEvent.OnStillWatchingContinue) },
            onSalir = { viewModel.onEvent(PlayerEvent.OnDismissStillWatchingPrompt) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 50.dp, bottom = accionesAbajo)
                .zIndex(2.1f)
                .reportSubtitleClearance(subtitleClearanceGapPx) { postPlayClearancePx = it },
        )
        // BalooTV (C6): en los créditos de una película, la escena poscréditos.
        com.nuvio.tv.balootv.ui.reproductor.TarjetaPoscreditos(
            aviso = tarjetaPoscreditos,
            reproduciendo = uiState.isPlaying && !uiState.isBuffering,
            controlesVisibles = uiState.showControls,
            foco = poscreditosFocusRequester,
            focoAbajo = if (uiState.showControls) progressBarFocusRequester else null,
            onIr = {
                viewModel.onEvent(PlayerEvent.OnIrAPoscreditos)
                // La tarjeta se va: el foco vuelve al reproductor para que el mando siga respondiendo.
                runCatching { containerFocusRequester.requestFocus() }
            },
            onSalir = { viewModel.onEvent(PlayerEvent.OnSalirSinPoscreditos) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 50.dp, bottom = accionesAbajo)
                .zIndex(2.1f)
                .reportSubtitleClearance(subtitleClearanceGapPx) { skipClearancePx = it },
        )
        com.nuvio.tv.balootv.ui.reproductor.AvisoPoscreditos(
            texto = uiState.avisoPoscreditosAlSalir
                ?: (uiState.poscreditos as? com.nuvio.tv.balootv.reproductor.Poscreditos.Aviso.NoLoQuites)?.texto,
            modifier = Modifier.zIndex(3f),
        )

        // Parental guide overlay (shows when video first starts playing)
        ParentalGuideOverlay(
            warnings = uiState.parentalWarnings,
            isVisible = uiState.showParentalGuide,
            onAnimationComplete = {
                viewModel.onEvent(PlayerEvent.OnParentalGuideHide)
            },
            modifier = Modifier.align(Alignment.TopStart)
        )

        DisplayModeOverlay(
            info = uiState.displayModeInfo,
            isVisible = uiState.showDisplayModeInfo,
            onAnimationComplete = {
                viewModel.onEvent(PlayerEvent.OnHideDisplayModeInfo)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(2.2f)
        )

        val showClockOverlay = uiState.showControls &&
            uiState.osdClockEnabled &&
            uiState.error == null &&
            !uiState.showLoadingOverlay &&
            !uiState.showPauseOverlay &&
            !uiState.showEpisodesPanel &&
            !uiState.showSourcesPanel &&
            !uiState.showAudioOverlay &&
            !uiState.showSubtitleOverlay &&
            !uiState.showSpeedDialog &&
            !uiState.showMoreDialog &&
            !uiState.showDisplayModeInfo &&
            !postPlayRecommendationState.isVisible

        // BalooTV: sin reloj arriba; la hora de fin ya va en la capa ("termina a las 23:18").

        uiState.avisoAnimeSinSubs?.let { aviso ->
            com.nuvio.tv.balootv.ui.reproductor.DialogoAnimeSinSubs(
                aviso = aviso,
                onLatinos = { viewModel.controller.animeConSubtitulosLatinos() },
                onSinSubtitulos = { viewModel.controller.animeSinSubtitulos() },
                onAhoraNo = { exitPlayer() },
                modifier = Modifier.zIndex(3.5f)
            )
        }

        // BalooTV: el formato y el idioma, unos segundos al empezar cada fuente.
        com.nuvio.tv.balootv.ui.reproductor.InsigniaFormato(
            formato = remember(uiState.currentStreamName, uiState.currentStreamUrl) {
                com.nuvio.tv.balootv.fuentes.LineasFuente.formatoDe(uiState.currentStreamName, uiState.currentStreamUrl)
            },
            reproduciendo = uiState.isPlaying,
            controlesVisibles = uiState.showControls,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 34.dp, end = 50.dp).zIndex(2.2f),
        )

        uiState.avisoFuente?.let { aviso ->
            com.nuvio.tv.balootv.ui.reproductor.AvisoFuente(aviso, Modifier.zIndex(3f))
        }

        // Controls overlay
        AnimatedVisibility(
            visible = playerControlsVisible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            // BalooTV: capa de cristal del tablero 21 en lugar de los controles heredados.
            val etiquetaFuente = remember(uiState.currentStreamName, uiState.currentStreamUrl) {
                com.nuvio.tv.balootv.fuentes.LineasFuente.formatoDe(uiState.currentStreamName, uiState.currentStreamUrl)
            }
            val esSerieCapa = uiState.currentSeason != null && uiState.currentEpisode != null
            // BalooTV: como el Apple TV, el episodio en pequeño encima y la serie en grande.
            val tituloCapa = if (esSerieCapa) uiState.contentName ?: uiState.title else uiState.title
            val episodioCapa = if (esSerieCapa) {
                com.nuvio.tv.balootv.reproductor.AccionesContextuales.episodio(
                    uiState.currentSeason!!, uiState.currentEpisode!!, uiState.currentEpisodeTitle
                )
            } else null
            val reiniciarOcultar = {
                viewModel.scheduleHideControls()
                viewModel.onUserInteraction()
            }
            com.nuvio.tv.balootv.ui.reproductor.CapaReproductor(
                titulo = tituloCapa,
                episodio = episodioCapa,
                etiqueta = etiquetaFuente,
                reproduciendo = uiState.isPlaying,
                esSerie = esSerieCapa,
                haySiguiente = uiState.nextEpisode?.hasAired == true,
                pararAlAcabar = uiState.pararAlAcabar,
                focoBotones = playPauseFocusRequester,
                onPlayPause = { viewModel.onEvent(PlayerEvent.OnPlayPause) },
                onAudioSubtitulos = { viewModel.onEvent(PlayerEvent.OnShowAudioOverlay) },
                onCambiarFuente = { viewModel.onEvent(PlayerEvent.OnShowSourcesPanel) },
                onEpisodios = { viewModel.onEvent(PlayerEvent.OnShowEpisodesPanel) },
                onSiguiente = { viewModel.onEvent(PlayerEvent.OnPlayNextEpisode) },
                onPararAlAcabar = { viewModel.onEvent(PlayerEvent.OnPararAlAcabar) },
                onFoco = reiniciarOcultar,
                bloqueInferior = Modifier.reportSubtitleClearance(subtitleClearanceGapPx) { subtitleControlsClearancePx = it },
                barra = {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        PlayerControlsProgressBarHost(
                            viewModel = viewModel,
                            focusRequester = progressBarFocusRequester,
                            downFocusRequester = playPauseFocusRequester,
                            // BalooTV: de la barra se sube a la acción contextual si la hay; si no, se esconden los controles.
                            onUpKey = {
                                when {
                                    skipButtonActuallyVisible -> runCatching { skipIntroFocusRequester.requestFocus() }
                                    postPlayOverlayMode != null -> runCatching { nextEpisodeFocusRequester.requestFocus() }
                                    else -> viewModel.hideControls()
                                }
                            },
                            onFocused = reiniciarOcultar,
                            onClick = { viewModel.onEvent(PlayerEvent.OnPlayPause) }
                        )
                    }
                },
                tiempos = { TiemposCapaHost(viewModel) }
            )
        }

        // Aspect ratio indicator (floating pill)
        AnimatedVisibility(
            visible = uiState.showAspectRatioIndicator,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            AspectRatioIndicator(text = uiState.aspectRatioIndicatorText)
        }

        AnimatedVisibility(
            visible = uiState.showStreamSourceIndicator,
            enter = fadeIn(animationSpec = tween(NuvioMotion.tokens.durations.fast)),
            exit = fadeOut(animationSpec = tween(NuvioMotion.tokens.durations.fast)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 128.dp)
        ) {
            StreamSourceIndicator(text = uiState.streamSourceIndicatorText)
        }

        AnimatedVisibility(
            visible = uiState.showPlayerEngineSwitchInfo && uiState.error == null,
            enter = fadeIn(animationSpec = tween(NuvioMotion.tokens.durations.fast)),
            exit = fadeOut(animationSpec = tween(NuvioMotion.tokens.durations.fast)),
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(2.35f)
        ) {
            PlayerEngineSwitchIndicator(
                title = stringResource(R.string.player_engine_switching_title),
                message = uiState.playerEngineSwitchInfoText
            )
        }

        AnimatedVisibility(
            visible = seekOverlayVisible,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .reportSubtitleClearance(subtitleClearanceGapPx) { seekClearancePx = it }
        ) {
            SeekOverlayHost(viewModel = viewModel)
        }

        // Episodes/streams side panel (slides in from right)
        AnimatedVisibility(
            visible = uiState.showEpisodesPanel && uiState.error == null,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(120))
        ) {
            // Scrim (fades in/out, no slide)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }

        // Panel itself (slides in from right)
        AnimatedVisibility(
            visible = uiState.showEpisodesPanel && uiState.error == null,
            enter = slideInHorizontally(
                animationSpec = tween(220),
                initialOffsetX = { it }
            ),
            exit = slideOutHorizontally(
                animationSpec = tween(220),
                targetOffsetX = { it }
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                EpisodesSidePanel(
                    uiState = uiState,
                    episodesFocusRequester = episodesFocusRequester,
                    streamsFocusRequester = streamsFocusRequester,
                    onClose = { viewModel.onEvent(PlayerEvent.OnDismissEpisodesPanel) },
                    onBackToEpisodes = { viewModel.onEvent(PlayerEvent.OnBackFromEpisodeStreams) },
                    onReloadEpisodeStreams = { viewModel.onEvent(PlayerEvent.OnReloadEpisodeStreams) },
                    onSeasonSelected = { viewModel.onEvent(PlayerEvent.OnEpisodeSeasonSelected(it)) },
                    onAddonFilterSelected = { viewModel.onEvent(PlayerEvent.OnEpisodeAddonFilterSelected(it)) },
                    onEpisodeSelected = { viewModel.onEvent(PlayerEvent.OnEpisodeSelected(it)) },
                    onStreamSelected = { viewModel.onEvent(PlayerEvent.OnEpisodeStreamSelected(it)) },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

        // Sources panel scrim
        AnimatedVisibility(
            visible = uiState.showSourcesPanel && uiState.error == null,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(120))
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }

        // Sources panel (slides in from right)
        AnimatedVisibility(
            visible = uiState.showSourcesPanel && uiState.error == null,
            enter = slideInHorizontally(
                animationSpec = tween(220),
                initialOffsetX = { it }
            ),
            exit = slideOutHorizontally(
                animationSpec = tween(220),
                targetOffsetX = { it }
            )
        ) {
            // BalooTV: panel del tablero 20, con la que suena marcada y "Esta no está en castellano".
            com.nuvio.tv.balootv.ui.fuentes.PanelFuentes(
                lineas = uiState.lineasFuente.orEmpty(),
                buscando = uiState.isLoadingSourceStreams,
                onElegir = { linea -> linea.stream?.let { viewModel.onEvent(PlayerEvent.OnSourceStreamSelected(it)) } },
                onNoEsCastellano = if (uiState.currentStreamUrl.isNullOrBlank()) null else {
                    { viewModel.controller.marcarNoCastellano() }
                },
                anime = viewModel.controller.esAnime
            )
        }

        // BalooTV: un solo panel de cristal de audio y subtítulos (solo castellano), tablero 21.
        if (uiState.showAudioOverlay || uiState.showSubtitleOverlay) {
            val pistasAudio = uiState.audioTracks
            val pistasSub = uiState.subtitleTracks
            val opcionesSub = com.nuvio.tv.balootv.reproductor.SubtitulosCastellano.opciones(pistasSub, uiState.addonSubtitles)
            val subActual = com.nuvio.tv.balootv.reproductor.SubtitulosCastellano.eleccion(
                opcionesSub, pistasSub, uiState.selectedSubtitleTrackIndex, conAddon = uiState.selectedAddonSubtitle != null,
            )
            com.nuvio.tv.balootv.ui.reproductor.PanelAudioSubtitulos(
                audio = pistasAudio,
                textoFuente = listOfNotNull(uiState.currentStreamName, uiState.currentStreamUrl).joinToString(" / "),
                audioElegido = uiState.selectedAudioTrackIndex,
                opciones = opcionesSub,
                subtituloActual = subActual,
                onAudio = { viewModel.onEvent(PlayerEvent.OnSelectAudioTrack(it)) },
                onSubtitulos = { eleccion ->
                    when (eleccion) {
                        com.nuvio.tv.balootv.reproductor.EleccionSubtitulos.FORZADOS ->
                            opcionesSub.forzados?.let { viewModel.onEvent(PlayerEvent.OnSelectSubtitleTrack(it.index)) }
                        com.nuvio.tv.balootv.reproductor.EleccionSubtitulos.CASTELLANO ->
                            opcionesSub.completoInterno?.let { viewModel.onEvent(PlayerEvent.OnSelectSubtitleTrack(it.index)) }
                                ?: opcionesSub.completoExterno?.let { viewModel.onEvent(PlayerEvent.OnSelectAddonSubtitle(it)) }
                        com.nuvio.tv.balootv.reproductor.EleccionSubtitulos.DESACTIVADOS ->
                            viewModel.onEvent(PlayerEvent.OnDisableSubtitles)
                    }
                },
                retrasoMs = uiState.subtitleDelayMs,
                onRetraso = { viewModel.onEvent(PlayerEvent.OnSetSubtitleDelay(it)) },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2.6f)
            )
        }

        if (uiState.showSpeedDialog) {
            SpeedSelectionDialog(
                currentSpeed = uiState.playbackSpeed,
                onSpeedSelected = { viewModel.onEvent(PlayerEvent.OnSetPlaybackSpeed(it)) },
                onDismiss = { viewModel.onEvent(PlayerEvent.OnDismissTransientOverlay) }
            )
        }
    }
}

@Composable
private fun MpvPlayerSurface(
    viewModel: PlayerViewModel,
    isPlaying: Boolean,
    isBuffering: Boolean,
    aspectMode: AspectMode,
    subtitleStyle: SubtitleStyleSettings,
    subtitleLiftPx: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestAspectMode by rememberUpdatedState(aspectMode)
    val mpvView = remember(context) {
        NuvioMpvSurfaceView(context).apply {
            isFocusable = false
            isFocusableInTouchMode = false
        }
    }

    AndroidView(
        factory = { mpvView },
        modifier = modifier.focusProperties { canFocus = false }
    )

    DisposableEffect(viewModel, mpvView) {
        viewModel.attachMpvView(mpvView)
        onDispose {
            viewModel.attachMpvView(null)
        }
    }

    DisposableEffect(mpvView) {
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            mpvView.applyAspectMode(latestAspectMode)
        }
        mpvView.addOnLayoutChangeListener(listener)
        onDispose {
            mpvView.removeOnLayoutChangeListener(listener)
        }
    }

    LaunchedEffect(mpvView, isPlaying, isBuffering) {
        val shouldKeepScreenOn = isPlaying || isBuffering
        if (mpvView.keepScreenOn != shouldKeepScreenOn) {
            mpvView.keepScreenOn = shouldKeepScreenOn
        }
    }

    LaunchedEffect(mpvView, aspectMode) {
        mpvView.applyAspectMode(aspectMode)
    }

    LaunchedEffect(mpvView, subtitleStyle, subtitleLiftPx) {
        val height = mpvView.height
        mpvView.subtitleLiftFraction = if (subtitleLiftPx > 0f && height > 0) {
            (subtitleLiftPx / height).toDouble()
        } else {
            0.0
        }
        mpvView.applySubtitleStyle(subtitleStyle)
    }
}

@Composable
private fun ExoPlayerSurface(
    player: ExoPlayer,
    controller: PlayerRuntimeController,
    isPlaying: Boolean,
    isBuffering: Boolean,
    aspectMode: AspectMode,
    tunnelingEnabled: Boolean,
    tunneledSurfaceFill: Boolean,
    useLibass: Boolean,
    libassRenderType: LibassRenderType,
    subtitleStyle: SubtitleStyleSettings,
    subtitleLiftPx: Float,
    onBindSubtitleView: (androidx.media3.ui.SubtitleView?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestAspectMode by rememberUpdatedState(aspectMode)
    val latestTunnelingEnabled by rememberUpdatedState(tunnelingEnabled)
    val latestTunneledSurfaceFill by rememberUpdatedState(tunneledSurfaceFill)
    val latestBindSubtitleView by rememberUpdatedState(onBindSubtitleView)
    val latestSubtitleStyle by rememberUpdatedState(subtitleStyle)
    val latestSubtitleLiftPx by rememberUpdatedState(subtitleLiftPx)
    val playerView = remember(context, player) {
        PlayerView(context).apply {
            useController = false
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            keepScreenOn = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            enableComposeSurfaceSyncWorkaroundIfAvailable()
            this.player = player
        }
    }

    AndroidView(
        factory = { playerView },
        modifier = modifier.focusProperties { canFocus = false },
        update = {
            it.syncLibassOverlay(
                player = player,
                enabled = useLibass,
                renderType = libassRenderType
            )
            latestBindSubtitleView(it.subtitleView)
        }
    )

    DisposableEffect(playerView, player) {
        if (playerView.player !== player) {
            playerView.player = player
        }
        latestBindSubtitleView(playerView.subtitleView)
        onDispose {
            latestBindSubtitleView(null)
            if (playerView.player === player) {
                playerView.player = null
            }
        }
    }

    DisposableEffect(playerView) {
        controller.exoPlayerView = playerView
        onDispose {
            if (controller.exoPlayerView === playerView) {
                controller.exoPlayerView = null
            }
        }
    }

    DisposableEffect(player, playerView) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                controller.videoAspectRatio = if (videoSize.width > 0 && videoSize.height > 0) {
                    videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio / videoSize.height.toFloat()
                } else {
                    0f
                }
                playerView.post {
                    playerView.syncExoSurfaceLayout(
                        tunnelingEnabled = latestTunnelingEnabled,
                        tunneledSurfaceFill = latestTunneledSurfaceFill,
                        aspectMode = latestAspectMode
                    )
                }
            }

            override fun onRenderedFirstFrame() {
                playerView.post {
                    playerView.syncExoSurfaceLayout(
                        tunnelingEnabled = latestTunnelingEnabled,
                        tunneledSurfaceFill = latestTunneledSurfaceFill,
                        aspectMode = latestAspectMode
                    )
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                // Re-apply subtitle style when tracks change so style is applied
                // even when subtitles are enabled after initial player setup.
                playerView.post {
                    playerView.applySubtitleStyleIfNeeded(latestSubtitleStyle, latestSubtitleLiftPx, force = true)
                }
            }
        }
        player.addListener(listener)
        playerView.post {
            playerView.syncExoSurfaceLayout(
                tunnelingEnabled = latestTunnelingEnabled,
                tunneledSurfaceFill = latestTunneledSurfaceFill,
                aspectMode = latestAspectMode
            )
        }
        onDispose {
            player.removeListener(listener)
        }
    }

    DisposableEffect(playerView) {
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            playerView.post {
                playerView.syncExoSurfaceLayout(
                    tunnelingEnabled = latestTunnelingEnabled,
                    tunneledSurfaceFill = latestTunneledSurfaceFill,
                    aspectMode = latestAspectMode
                )
            }
        }
        val removeListener = addExoAspectLayoutChangeListener(playerView, listener)
        onDispose {
            removeListener()
        }
    }

    LaunchedEffect(playerView, isPlaying, isBuffering) {
        val shouldKeepScreenOn = isPlaying || isBuffering
        if (playerView.keepScreenOn != shouldKeepScreenOn) {
            playerView.keepScreenOn = shouldKeepScreenOn
        }
    }

    LaunchedEffect(playerView, aspectMode, tunnelingEnabled, tunneledSurfaceFill) {
        playerView.syncExoSurfaceLayout(
            tunnelingEnabled = tunnelingEnabled,
            tunneledSurfaceFill = tunneledSurfaceFill,
            aspectMode = aspectMode
        )
    }

    LaunchedEffect(playerView, player, useLibass, libassRenderType) {
        playerView.syncLibassOverlay(
            player = player,
            enabled = useLibass,
            renderType = libassRenderType
        )
    }

    LaunchedEffect(playerView, subtitleStyle, subtitleLiftPx) {
        playerView.applySubtitleStyleIfNeeded(subtitleStyle, subtitleLiftPx)
    }
}

private fun PlayerView.enableComposeSurfaceSyncWorkaroundIfAvailable() {
    runCatching {
        javaClass
            .getMethod("setEnableComposeSurfaceSyncWorkaround", java.lang.Boolean.TYPE)
            .invoke(this, false)
    }
}

private fun PlayerView.applyExoAspectMode(mode: AspectMode) {
    setTag(R.id.player_view_aspect_mode_tag, mode)
    applyExoAspectMode(this, mode)
}

private fun PlayerView.syncExoSurfaceLayout(
    tunnelingEnabled: Boolean,
    tunneledSurfaceFill: Boolean,
    aspectMode: AspectMode
) {
    val targetResizeMode = PlayerDisplayModeUtils.exoSurfaceResizeMode(
        tunnelingEnabled = tunnelingEnabled,
        tunneledSurfaceFill = tunneledSurfaceFill
    )
    if (resizeMode != targetResizeMode) {
        resizeMode = targetResizeMode
    }
    applyExoAspectMode(aspectModeAppliedToExoSurface(tunnelingEnabled, aspectMode))
}

private data class SubtitleAppliedConfig(
    val style: SubtitleStyleSettings,
    val isAss: Boolean,
    val liftPx: Float
)

private val SUBTITLE_CONTROLS_LIFT = 130.dp
private val SUBTITLE_CONTROLS_GAP = 8.dp

private fun Modifier.reportSubtitleClearance(gapPx: Float, onClearance: (Float) -> Unit): Modifier =
    onGloballyPositioned { coordinates ->
        val rootHeight = coordinates.findRootCoordinates().size.height
        if (rootHeight > 0 && coordinates.size.height > 0) {
            onClearance((rootHeight - coordinates.boundsInRoot().top + gapPx).coerceAtLeast(0f))
        }
    }

private fun PlayerView.isAssOrSsaSubtitleSelected(): Boolean {
    val currentTracks = player?.currentTracks
    if (currentTracks != null) {
        for (group in currentTracks.groups) {
            if (group.type != androidx.media3.common.C.TRACK_TYPE_TEXT) continue
            for (index in 0 until group.length) {
                if (!group.isTrackSelected(index)) continue
                val format = group.getTrackFormat(index)
                if (format.sampleMimeType == androidx.media3.common.MimeTypes.TEXT_SSA) return true
                val hasAssCodec = format.codecs
                    ?.split(',')
                    ?.asSequence()
                    ?.map { it.trim().lowercase(java.util.Locale.US) }
                    ?.any { codec ->
                        codec == androidx.media3.common.MimeTypes.TEXT_SSA ||
                            codec == "s_text/ass" ||
                            codec == "s_text/ssa" ||
                            codec.endsWith("/x-ssa")
                    } == true
                if (hasAssCodec) return true
            }
        }
    }
    val sidecarKey = subtitleView?.getTag(R.id.player_view_sidecar_generation_tag) as? String
    if (sidecarKey != null && (sidecarKey.contains(".ass", ignoreCase = true) || sidecarKey.contains(".ssa", ignoreCase = true))) {
        return true
    }
    return false
}

private fun PlayerView.applySubtitleStyleIfNeeded(
    subtitleStyle: SubtitleStyleSettings,
    liftPx: Float,
    force: Boolean = false
) {
    val isAss = isAssOrSsaSubtitleSelected()
    val config = SubtitleAppliedConfig(subtitleStyle, isAss, liftPx)
    if (!force && getTag(R.id.player_view_subtitle_style_tag) == config) {
        return
    }
    val subView = subtitleView
    if (subView == null) {
        // SubtitleView not yet available (no track selected yet). Don't set the
        // tag so that when subtitles become active the style is re-applied.
        return
    }
    setTag(R.id.player_view_subtitle_style_tag, config)
    subView.apply {
        val baseFontSize = 24f
        val scaledFontSize = baseFontSize * (subtitleStyle.size / 100f)
        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, scaledFontSize)
        setApplyEmbeddedFontSizes(false)

        val typeface = if (subtitleStyle.bold) {
            android.graphics.Typeface.DEFAULT_BOLD
        } else {
            android.graphics.Typeface.DEFAULT
        }

        val edgeType = if (subtitleStyle.outlineEnabled) {
            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
        } else {
            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
        }

        setStyle(
            androidx.media3.ui.CaptionStyleCompat(
                subtitleStyle.textColor,
                subtitleStyle.backgroundColor,
                android.graphics.Color.TRANSPARENT,
                edgeType,
                subtitleStyle.outlineColor,
                typeface
            )
        )

        setApplyEmbeddedStyles(!isAss)

        val bottomPaddingFraction =
            (0.06f + (subtitleStyle.verticalOffset / 250f)).coerceIn(0f, 0.4f)
        setBottomPaddingFraction(bottomPaddingFraction)

        post {
            val extraPadding = (height * (subtitleStyle.verticalOffset / 400f)).toInt().coerceAtLeast(0)
            setPadding(paddingLeft, paddingTop, paddingRight, extraPadding)
            val availableHeight = height - extraPadding
            if (liftPx > 0f && availableHeight > 0) {
                setBottomPaddingFraction(
                    maxOf(bottomPaddingFraction, (liftPx - extraPadding) / availableHeight).coerceIn(0f, 0.5f)
                )
            }
        }
    }
}

private fun PlayerView.syncLibassOverlay(
    player: ExoPlayer,
    enabled: Boolean,
    renderType: LibassRenderType
) {
    val containerId = if (renderType == LibassRenderType.OVERLAY_OPEN_GL) {
        R.id.libass_overlay_container_gl
    } else {
        R.id.libass_overlay_container
    }
    val overlayContainer = findViewById<android.widget.FrameLayout>(containerId) ?: return
    val needsOverlay = enabled && renderType.usesOverlaySubtitleView()
    val boundPlayer = getTag(R.id.libass_overlay_bound_player) as? ExoPlayer
    val hasOverlayChild = overlayContainer.hasAssOverlayChild()

    if (!needsOverlay) {
        if (hasOverlayChild) {
            overlayContainer.removeAssOverlayChildren()
        }
        if (boundPlayer != null) {
            setTag(R.id.libass_overlay_bound_player, null)
        }
        return
    }

    val assHandler = player.getAssHandlerCompat() ?: return
    if (boundPlayer === player && hasOverlayChild) {
        return
    }

    overlayContainer.removeAssOverlayChildren()
    val assSubtitleView = AssSubtitleView(overlayContainer.context, assHandler)
    overlayContainer.addView(
        assSubtitleView,
        android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
    )
    setTag(R.id.libass_overlay_bound_player, player)
}

private fun LibassRenderType.usesOverlaySubtitleView(): Boolean {
    return this == LibassRenderType.OVERLAY_CANVAS || this == LibassRenderType.OVERLAY_OPEN_GL
}

private fun android.widget.FrameLayout.hasAssOverlayChild(): Boolean {
    for (index in 0 until childCount) {
        if (getChildAt(index) is AssSubtitleView) {
            return true
        }
    }
    return false
}

private fun android.widget.FrameLayout.removeAssOverlayChildren() {
    for (index in childCount - 1 downTo 0) {
        if (getChildAt(index) is AssSubtitleView) {
            removeViewAt(index)
        }
    }
}

/**
 * Remove ASS overlay views when hiding so the libass render thread stops.
 * [syncLibassOverlay] re-creates them on the next Compose update cycle.
 */
private fun PlayerView.setAssOverlayVisibility(visibility: Int) {
    if (visibility == android.view.View.GONE) {
        for (containerId in intArrayOf(R.id.libass_overlay_container, R.id.libass_overlay_container_gl)) {
            val container = findViewById<android.widget.FrameLayout>(containerId) ?: continue
            container.removeAssOverlayChildren()
        }
        setTag(R.id.libass_overlay_bound_player, null)
    }
}

@Composable
private fun PlayerControlsProgressBarHost(
    viewModel: PlayerViewModel,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onUpKey: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val playbackTimeline by viewModel.playbackTimeline.collectAsState()

    ProgressBar(
        currentPosition = playbackTimeline.currentPosition,
        duration = playbackTimeline.duration,
        onSeekPreview = { delta ->
            viewModel.onEvent(PlayerEvent.OnPreviewSeekBy(delta))
        },
        onSeekCommit = {
            viewModel.onEvent(PlayerEvent.OnCommitPreviewSeek)
        },
        focusRequester = focusRequester,
        upFocusRequester = upFocusRequester,
        downFocusRequester = downFocusRequester,
        onUpKey = onUpKey,
        onFocused = onFocused,
        onClick = onClick,
        bufferedPosition = playbackTimeline.bufferedPosition
    )
}

@Composable
private fun TiemposCapaHost(viewModel: PlayerViewModel) {
    val playbackTimeline by viewModel.playbackTimeline.collectAsState()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val estilo = Color.White.copy(alpha = 0.8f)
        Text(com.nuvio.tv.balootv.reproductor.TiemposReproductor.transcurrido(playbackTimeline.currentPosition), color = estilo, fontSize = 11.sp)
        Text(
            com.nuvio.tv.balootv.reproductor.TiemposReproductor.restante(
                playbackTimeline.currentPosition, playbackTimeline.duration, java.time.LocalTime.now()
            ),
            color = estilo,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeekPreview: (Long) -> Unit,
    onSeekCommit: () -> Unit,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onUpKey: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    /** Position (ms) up to which content is buffered. Pass 0 to skip the overlay. */
    bufferedPosition: Long = 0L
) {
    val accentBrush = NuvioTheme.palette.accentBrush()
    val progress = if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val bufferedProgress = if (duration > 0 && bufferedPosition > currentPosition) {
        (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(100),
        label = "progress"
    )
    val animatedBufferedProgress by animateFloatAsState(
        targetValue = bufferedProgress,
        animationSpec = tween(200),
        label = "bufferedProgress"
    )
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(NuvioTheme.spacing.md)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .then(
                if (upFocusRequester != null || downFocusRequester != null) {
                    Modifier.focusProperties {
                        upFocusRequester?.let { up = it }
                        downFocusRequester?.let { down = it }
                    }
                } else {
                    Modifier
                }
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onSeekCommit()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (onClick != null) return@onPreviewKeyEvent true
                        }
                    }
                    return@onPreviewKeyEvent false
                }

                // testing additional key handling for DPAD_LEFT and DPAD_RIGHT to allow seek in focus (check)
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (onClick != null) {
                                // Ignore key-repeat so holding OK doesn't toggle repeatedly.
                                if (keyEvent.nativeKeyEvent.repeatCount == 0) onClick()
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (downFocusRequester != null) {
                                try {
                                    downFocusRequester.requestFocus()
                                } catch (_: Exception) {
                                }
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (upFocusRequester != null) {
                                try {
                                    upFocusRequester.requestFocus()
                                } catch (_: Exception) {
                                }
                                true
                            } else if (onUpKey != null) {
                                onUpKey.invoke()
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onSeekPreview(
                                PlayerScrubRates.deltaMsForKeyRepeat(
                                    repeatCount = keyEvent.nativeKeyEvent.repeatCount,
                                    forward = false
                                )
                            )
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onSeekPreview(
                                PlayerScrubRates.deltaMsForKeyRepeat(
                                    repeatCount = keyEvent.nativeKeyEvent.repeatCount,
                                    forward = true
                                )
                            )
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFocused) NuvioTheme.spacing.md else NuvioTheme.spacing.sm)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    if (isFocused) Color.White.copy(alpha = 0.45f)
                    else Color.White.copy(alpha = 0.3f)
                )
                .playerProgressFill(
                    progress = { animatedProgress },
                    bufferedProgress = { animatedBufferedProgress },
                    bufferedColor = NuvioTheme.colors.Secondary,
                    playedBrush = accentBrush
                )
        )
    }
}

@Composable
private fun SeekOverlay(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long = 0L
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NuvioTheme.spacing.xxl, vertical = NuvioTheme.spacing.xl)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            ProgressBar(
                currentPosition = currentPosition,
                duration = duration,
                onSeekPreview = {},
                onSeekCommit = {},
                bufferedPosition = bufferedPosition
            )

            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun SeekOverlayHost(viewModel: PlayerViewModel) {
    val playbackTimeline by viewModel.playbackTimeline.collectAsState()

    SeekOverlay(
        currentPosition = playbackTimeline.currentPosition,
        duration = playbackTimeline.duration,
        bufferedPosition = playbackTimeline.bufferedPosition
    )
}


@Composable
private fun AspectRatioIndicator(text: String) {
    val customAspectPainter = rememberRawSvgPainter(R.raw.ic_player_aspect_ratio)

    // Floating pill indicator for aspect ratio changes
    Row(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(NuvioTheme.spacing.xl)
            )
            .padding(horizontal = 20.dp, vertical = NuvioTheme.spacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon background circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = Color(0xFF3B3B3B),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = customAspectPainter,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(NuvioTheme.spacing.md))

        // Text
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = Color.White
        )
    }
}

@Composable
private fun StreamSourceIndicator(text: String) {
    Row(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.82f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = NuvioTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlayerEngineSwitchIndicator(
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.86f))
            .padding(horizontal = 22.dp, vertical = NuvioTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.92f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun rememberRawSvgPainter(@RawRes iconRes: Int): Painter {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val sizePx = with(density) { NuvioTheme.spacing.xl.roundToPx() }
    val request = remember(iconRes, context, sizePx) {
        ImageRequest.Builder(context)
            .data(iconRes)
            .size(sizePx)
            .build()
    }
    return rememberAsyncImagePainter(model = request)
}

@Composable
private fun ErrorOverlay(
    message: String,
    showSwitchToMpvAction: Boolean = false,
    onSwitchToMpv: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val exitFocusRequester = remember { FocusRequester() }
    val mpvFocusRequester = remember { FocusRequester() }
    val onSwitchMpvAction = onSwitchToMpv.takeIf { showSwitchToMpvAction }
    val targetFocusRequester = if (onSwitchMpvAction != null) mpvFocusRequester else exitFocusRequester

    var targetButtonPlaced by remember(onSwitchMpvAction != null) { mutableStateOf(false) }
    var mpvFocused by remember(onSwitchMpvAction != null) { mutableStateOf(false) }
    var exitFocused by remember(onSwitchMpvAction != null) { mutableStateOf(false) }
    val hasOverlayFocus = mpvFocused || exitFocused

    LaunchedEffect(targetButtonPlaced, onSwitchMpvAction != null) {
        if (!targetButtonPlaced) return@LaunchedEffect
        val focused = targetFocusRequester.requestFocusAfterFrames(frames = 0)
        if (!focused) {
            targetFocusRequester.requestFocusAfterFrames(frames = 2)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .zIndex(3f)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val isDirection = when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    else -> false
                }
                if (!isDirection || hasOverlayFocus) return@onPreviewKeyEvent false
                runCatching { targetFocusRequester.requestFocus() }.isSuccess
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.focusGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
        ) {
            Text(
                text = stringResource(R.string.player_error_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = NuvioTheme.spacing.xxl)
            )

            Spacer(modifier = Modifier.height(NuvioTheme.spacing.lg))

            Row(
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onSwitchMpvAction != null) {
                    PlayerOverlayButton(
                        text = stringResource(R.string.player_switch_to_mpv),
                        onClick = onSwitchMpvAction,
                        primary = true,
                        modifier = Modifier
                            .focusRequester(mpvFocusRequester)
                            .onPlaced { targetButtonPlaced = true }
                            .onFocusChanged { mpvFocused = it.isFocused }
                            .focusProperties {
                                right = exitFocusRequester
                            }
                    )
                }
                PlayerOverlayButton(
                    text = stringResource(R.string.player_go_back),
                    onClick = onBack,
                    primary = onSwitchMpvAction == null,
                    modifier = Modifier
                        .focusRequester(exitFocusRequester)
                        .onPlaced {
                            if (onSwitchMpvAction == null) targetButtonPlaced = true
                        }
                        .onFocusChanged { exitFocused = it.isFocused }
                        .focusProperties {
                            left = if (onSwitchMpvAction != null) mpvFocusRequester else FocusRequester.Default
                        }
                )
            }
        }
    }
}

@Composable
internal fun PlayerOverlayButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(NuvioTheme.spacing.xxl)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = if (primary) Color.White else NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = if (primary) Color.White else NuvioTheme.colors.Secondary,
            contentColor = if (primary) Color.Black else NuvioTheme.colors.TextPrimary,
            focusedContentColor = if (primary) Color.Black else NuvioTheme.colors.OnSecondary
        ),
        shape = ButtonDefaults.shape(shape = shape),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = shape
            )
        ),
        contentPadding = PaddingValues(
            horizontal = NuvioTheme.spacing.lg,
            vertical = 14.dp
        ),
        scale = ButtonDefaults.scale()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SpeedSelectionDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIndex = remember(currentSpeed) {
        PLAYBACK_SPEEDS.indices.minByOrNull { index ->
            abs(PLAYBACK_SPEEDS[index] - currentSpeed)
        } ?: 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val speedFocusRequesters = remember {
        PLAYBACK_SPEEDS.map { FocusRequester() }
    }

    LaunchedEffect(selectedIndex) {
        runCatching { speedFocusRequesters[selectedIndex].requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(NuvioTheme.radii.xl))
                .background(NuvioTheme.colors.BackgroundElevated)
        ) {
            Column(
                modifier = Modifier.padding(NuvioTheme.spacing.xl)
            ) {
                Text(
                    text = stringResource(R.string.player_speed_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = NuvioTheme.colors.TextPrimary,
                    modifier = Modifier.padding(bottom = NuvioTheme.spacing.lg)
                )

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
                    contentPadding = PaddingValues(top = NuvioTheme.spacing.xs)
                ) {
                    itemsIndexed(PLAYBACK_SPEEDS) { index, speed ->
                        SpeedItem(
                            modifier = Modifier.focusRequester(speedFocusRequesters[index]),
                            speed = speed,
                            isSelected = speed == currentSpeed,
                            onClick = { onSpeedSelected(speed) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedItem(
    modifier: Modifier = Modifier,
    speed: Float,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isSelected) NuvioTheme.colors.Secondary.copy(alpha = 0.2f) else NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(NuvioTheme.radii.sm))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NuvioTheme.spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (speed == 1f) stringResource(R.string.player_speed_normal) else "${speed}x",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) NuvioTheme.colors.Primary else NuvioTheme.colors.TextPrimary
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.cd_selected),
                    tint = NuvioTheme.colors.Secondary,
                    modifier = Modifier.size(NuvioTheme.spacing.xl)
                )
            }
        }
    }
}

@Composable
internal fun DialogButton(
    text: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = if (isPrimary) NuvioTheme.colors.Secondary else NuvioTheme.colors.BackgroundCard,
            contentColor = if (isPrimary) NuvioTheme.colors.OnSecondary else NuvioTheme.colors.TextSecondary,
            focusedContainerColor = if (isPrimary) NuvioTheme.colors.SecondaryVariant else NuvioTheme.colors.FocusBackground,
            focusedContentColor = if (isPrimary) NuvioTheme.colors.OnSecondaryVariant else NuvioTheme.colors.Primary
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = if (isPrimary) {
                    BorderStroke(NuvioTheme.spacing.xxs, NuvioTheme.colors.SecondaryVariant)
                } else {
                    NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs)
                },
                shape = RoundedCornerShape(NuvioTheme.radii.md)
            )
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
        scale = ButtonDefaults.scale(focusedScale = 1f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00"

    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

/**
 * Buffering indicator extracted into its own composable to isolate
 * recomposition scope. When [isBuffering] toggles, only this subtree
 * is recomposed — the rest of [PlayerScreen] is skipped by Compose.
 */
@Composable
private fun PlayerBufferingIndicator(
    isBuffering: Boolean,
    showLoadingOverlay: Boolean
) {
    if (!isBuffering || showLoadingOverlay) return

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator()
    }
}
