package com.nuvio.tv.ui.screens.player

import android.app.Activity
import android.util.Log
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

internal fun PlayerRuntimeController.attachHostActivity(activity: Activity?) {
    hostActivityRef = activity?.let { WeakReference(it) }
}

internal fun PlayerRuntimeController.startInitialPlaybackIfNeeded() {
    if (initialPlaybackStarted) return

    initialPlaybackStarted = true

    val bg = navigationArgs.bingeGroup
    val cid = contentId
    if (cid != null && bg != null) {
        scope.launch(kotlinx.coroutines.NonCancellable) {
            val existingFilename = bingeGroupCacheDataStore.getFilename(cid)
            val existingInfoHash = bingeGroupCacheDataStore.getInfoHash(cid)
            bingeGroupCacheDataStore.replace(cid, bg, existingFilename, existingInfoHash)
        }
    }

    val infoHash = navigationArgs.infoHash
    Log.d(
        "PlayerStartup",
        "startInitialPlayback: infoHash=$infoHash host=${currentStreamUrl.safeStartupHost()} " +
            "urlHash=${currentStreamUrl.hashCode().toUInt().toString(16)}"
    )
    preparePlaybackBeforeStart(
        url = currentStreamUrl,
        headers = currentHeaders,
        loadSavedProgress = !navigationArgs.startFromBeginning
    )
}

internal fun PlayerRuntimeController.currentHostActivity(): Activity? {
    return hostActivityRef?.get()
}

private fun String.safeStartupHost(): String {
    return runCatching {
        android.net.Uri.parse(this).host ?: substringBefore("://").takeIf { it.isNotBlank() } ?: "unknown"
    }.getOrDefault("unknown")
}
