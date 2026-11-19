package com.nuvio.tv.domain.model

data class AmbientModeSettings(
    val enabled: Boolean = true,
    val timeoutMinutes: Int = DEFAULT_TIMEOUT_MINUTES
) {
    companion object {
        const val DEFAULT_TIMEOUT_MINUTES = 5
        val TIMEOUT_OPTIONS_MINUTES = listOf(1, 2, 5, 10, 15, 30)
    }
}
