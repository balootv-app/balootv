package com.nuvio.tv.core.player

import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.data.local.StreamAutoPlayMode

object StreamAutoPlayPolicy {
    fun isEffectivelyEnabled(playerSettings: PlayerSettings): Boolean {
        // BalooTV: "Preguntarme siempre" es eso, siempre; ni el enlace anterior ni el grupo de la serie.
        if (playerSettings.streamAutoPlayMode == StreamAutoPlayMode.MANUAL) return false
        if (playerSettings.streamReuseLastLinkEnabled) return true
        if (playerSettings.streamAutoPlayReuseBingeGroup &&
            playerSettings.streamAutoPlayPreferBingeGroupForNextEpisode) return true

        return when (playerSettings.streamAutoPlayMode) {
            StreamAutoPlayMode.MANUAL -> false
            StreamAutoPlayMode.FIRST_STREAM -> true
            StreamAutoPlayMode.BALOOTV -> true
            StreamAutoPlayMode.REGEX_MATCH -> isRegexSelectionConfigured(playerSettings.streamAutoPlayRegex)
        }
    }

    fun isRegexSelectionConfigured(regexPattern: String): Boolean {
        val pattern = regexPattern.trim()
        if (pattern.isEmpty() || !pattern.any { it.isLetterOrDigit() }) return false
        return runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.isSuccess
    }
}
