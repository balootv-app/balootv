package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.AmbientModeSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AmbientModeDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        const val FEATURE = "ambient_mode_settings"
    }

    private fun store() = factory.get(profileManager.activeProfileId.value, FEATURE)

    private val enabledKey = booleanPreferencesKey("ambient_enabled")
    private val timeoutMinutesKey = intPreferencesKey("ambient_timeout_minutes")

    val settings: Flow<AmbientModeSettings> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { prefs ->
            AmbientModeSettings(
                enabled = prefs[enabledKey] ?: true,
                timeoutMinutes = prefs[timeoutMinutesKey] ?: AmbientModeSettings.DEFAULT_TIMEOUT_MINUTES
            )
        }
    }.distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        store().edit { it[enabledKey] = enabled }
    }

    suspend fun setTimeoutMinutes(minutes: Int) {
        store().edit { it[timeoutMinutesKey] = minutes }
    }
}
