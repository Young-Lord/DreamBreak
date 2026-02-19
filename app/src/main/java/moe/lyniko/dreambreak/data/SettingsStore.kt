package moe.lyniko.dreambreak.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.lyniko.dreambreak.core.BreakPreferences

private val Context.settingsDataStore by preferencesDataStore(name = "dream_break_settings")

data class AppSettings(
    val preferences: BreakPreferences = BreakPreferences(),
    val pauseInListedApps: Boolean = false,
    val monitoredApps: String = "",
    val autoStartOnBoot: Boolean = false,
    val appEnabled: Boolean = true,
    val overlayTransparencyPercent: Int = 28,
    val overlayBackgroundUri: String = "",
    val onboardingCompleted: Boolean = false,
    val excludeFromRecents: Boolean = false,
    val persistentNotificationEnabled: Boolean = false,
)

class SettingsStore(private val context: Context) {
    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val preferences = BreakPreferences(
            smallEvery = prefs[Keys.SMALL_EVERY] ?: BreakPreferences().smallEvery,
            smallFor = prefs[Keys.SMALL_FOR] ?: BreakPreferences().smallFor,
            bigAfter = prefs[Keys.BIG_AFTER] ?: BreakPreferences().bigAfter,
            bigFor = prefs[Keys.BIG_FOR] ?: BreakPreferences().bigFor,
            flashFor = prefs[Keys.FLASH_FOR] ?: BreakPreferences().flashFor,
            postponeFor = prefs[Keys.POSTPONE_FOR] ?: BreakPreferences().postponeFor,
            resetIntervalAfterPause = prefs[Keys.RESET_INTERVAL_AFTER_PAUSE] ?: BreakPreferences().resetIntervalAfterPause,
            resetCycleAfterPause = prefs[Keys.RESET_CYCLE_AFTER_PAUSE] ?: BreakPreferences().resetCycleAfterPause,
        )
        AppSettings(
            preferences = preferences,
            pauseInListedApps = prefs[Keys.PAUSE_IN_LISTED_APPS] ?: false,
            monitoredApps = prefs[Keys.MONITORED_APPS] ?: "",
            autoStartOnBoot = prefs[Keys.AUTO_START_ON_BOOT] ?: false,
            appEnabled = prefs[Keys.APP_ENABLED] ?: true,
            overlayTransparencyPercent = (prefs[Keys.OVERLAY_TRANSPARENCY_PERCENT] ?: 28).coerceIn(0, 90),
            overlayBackgroundUri = prefs[Keys.OVERLAY_BACKGROUND_URI] ?: "",
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            excludeFromRecents = prefs[Keys.EXCLUDE_FROM_RECENTS] ?: false,
            persistentNotificationEnabled = prefs[Keys.PERSISTENT_NOTIFICATION_ENABLED] ?: false,
        )
    }

    suspend fun save(settings: AppSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SMALL_EVERY] = settings.preferences.smallEvery
            prefs[Keys.SMALL_FOR] = settings.preferences.smallFor
            prefs[Keys.BIG_AFTER] = settings.preferences.bigAfter
            prefs[Keys.BIG_FOR] = settings.preferences.bigFor
            prefs[Keys.FLASH_FOR] = settings.preferences.flashFor
            prefs[Keys.POSTPONE_FOR] = settings.preferences.postponeFor
            prefs[Keys.RESET_INTERVAL_AFTER_PAUSE] = settings.preferences.resetIntervalAfterPause
            prefs[Keys.RESET_CYCLE_AFTER_PAUSE] = settings.preferences.resetCycleAfterPause
            prefs[Keys.PAUSE_IN_LISTED_APPS] = settings.pauseInListedApps
            prefs[Keys.MONITORED_APPS] = settings.monitoredApps
            prefs[Keys.AUTO_START_ON_BOOT] = settings.autoStartOnBoot
            prefs[Keys.APP_ENABLED] = settings.appEnabled
            prefs[Keys.OVERLAY_TRANSPARENCY_PERCENT] = settings.overlayTransparencyPercent.coerceIn(0, 90)
            prefs[Keys.OVERLAY_BACKGROUND_URI] = settings.overlayBackgroundUri
            prefs[Keys.ONBOARDING_COMPLETED] = settings.onboardingCompleted
            prefs[Keys.EXCLUDE_FROM_RECENTS] = settings.excludeFromRecents
            prefs[Keys.PERSISTENT_NOTIFICATION_ENABLED] = settings.persistentNotificationEnabled
        }
    }

    private object Keys {
        val SMALL_EVERY = intPreferencesKey("small_every")
        val SMALL_FOR = intPreferencesKey("small_for")
        val BIG_AFTER = intPreferencesKey("big_after")
        val BIG_FOR = intPreferencesKey("big_for")
        val FLASH_FOR = intPreferencesKey("flash_for")
        val POSTPONE_FOR = intPreferencesKey("postpone_for")
        val RESET_INTERVAL_AFTER_PAUSE = intPreferencesKey("reset_interval_after_pause")
        val RESET_CYCLE_AFTER_PAUSE = intPreferencesKey("reset_cycle_after_pause")
        val PAUSE_IN_LISTED_APPS = booleanPreferencesKey("pause_in_clock_app")
        val MONITORED_APPS = stringPreferencesKey("monitored_apps")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val APP_ENABLED = booleanPreferencesKey("app_enabled")
        val OVERLAY_TRANSPARENCY_PERCENT = intPreferencesKey("overlay_transparency_percent")
        val OVERLAY_BACKGROUND_URI = stringPreferencesKey("overlay_background_uri")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val EXCLUDE_FROM_RECENTS = booleanPreferencesKey("exclude_from_recents")
        val PERSISTENT_NOTIFICATION_ENABLED = booleanPreferencesKey("persistent_notification_enabled")
    }
}
