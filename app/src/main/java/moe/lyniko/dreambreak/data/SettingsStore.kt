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
import moe.lyniko.dreambreak.core.formatPostponeDurations
import moe.lyniko.dreambreak.core.parsePostponeDurations

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
    val persistentNotificationUpdateFrequencySeconds: Int = 10,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
)

class SettingsStore(private val context: Context) {
    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val defaultPreferences = BreakPreferences()
        val preferences = BreakPreferences(
            smallEvery = prefs[Keys.SMALL_EVERY] ?: defaultPreferences.smallEvery,
            smallFor = prefs[Keys.SMALL_FOR] ?: defaultPreferences.smallFor,
            bigAfter = prefs[Keys.BIG_AFTER] ?: defaultPreferences.bigAfter,
            bigFor = prefs[Keys.BIG_FOR] ?: defaultPreferences.bigFor,
            flashFor = (prefs[Keys.FLASH_FOR] ?: defaultPreferences.flashFor).coerceIn(1, 600),
            topFlashEnabled = prefs[Keys.TOP_FLASH_ENABLED] ?: defaultPreferences.topFlashEnabled,
            topFlashSmallText = prefs[Keys.TOP_FLASH_SMALL_TEXT] ?: defaultPreferences.topFlashSmallText,
            topFlashBigText = prefs[Keys.TOP_FLASH_BIG_TEXT] ?: defaultPreferences.topFlashBigText,
            preBreakNotificationEnabled =
                prefs[Keys.PRE_BREAK_NOTIFICATION_ENABLED] ?: defaultPreferences.preBreakNotificationEnabled,
            preBreakNotificationLeadSeconds =
                (prefs[Keys.PRE_BREAK_NOTIFICATION_LEAD_SECONDS] ?: defaultPreferences.preBreakNotificationLeadSeconds)
                    .coerceIn(1, 3600),
            preBreakNotificationSmallTitle =
                prefs[Keys.PRE_BREAK_NOTIFICATION_SMALL_TITLE] ?: defaultPreferences.preBreakNotificationSmallTitle,
            preBreakNotificationSmallContent =
                prefs[Keys.PRE_BREAK_NOTIFICATION_SMALL_CONTENT] ?: defaultPreferences.preBreakNotificationSmallContent,
            preBreakNotificationBigTitle =
                prefs[Keys.PRE_BREAK_NOTIFICATION_BIG_TITLE] ?: defaultPreferences.preBreakNotificationBigTitle,
            preBreakNotificationBigContent =
                prefs[Keys.PRE_BREAK_NOTIFICATION_BIG_CONTENT] ?: defaultPreferences.preBreakNotificationBigContent,
            postponeFor = parsePostponeDurations(
                rawInput = prefs[Keys.POSTPONE_FOR] ?: prefs[Keys.POSTPONE_FOR_LEGACY]?.toString(),
                fallback = defaultPreferences.postponeFor,
            ),
        )
        AppSettings(
            preferences = preferences,
            pauseInListedApps = prefs[Keys.PAUSE_IN_LISTED_APPS] ?: false,
            monitoredApps = prefs[Keys.MONITORED_APPS] ?: "",
            autoStartOnBoot = prefs[Keys.AUTO_START_ON_BOOT] ?: false,
            appEnabled = prefs[Keys.APP_ENABLED] ?: true,
            overlayTransparencyPercent = (prefs[Keys.OVERLAY_TRANSPARENCY_PERCENT] ?: 28).coerceIn(0, 100),
            overlayBackgroundUri = prefs[Keys.OVERLAY_BACKGROUND_URI] ?: "",
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            excludeFromRecents = prefs[Keys.EXCLUDE_FROM_RECENTS] ?: false,
            persistentNotificationEnabled = prefs[Keys.PERSISTENT_NOTIFICATION_ENABLED] ?: false,
            persistentNotificationUpdateFrequencySeconds =
                (prefs[Keys.PERSISTENT_NOTIFICATION_UPDATE_FREQUENCY_SECONDS] ?: 10).coerceIn(1, 600),
            themeMode = AppThemeMode.fromStorage(prefs[Keys.THEME_MODE]),
        )
    }

    suspend fun save(settings: AppSettings) {
        val defaultPreferences = BreakPreferences()
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SMALL_EVERY] = settings.preferences.smallEvery
            prefs[Keys.SMALL_FOR] = settings.preferences.smallFor
            prefs[Keys.BIG_AFTER] = settings.preferences.bigAfter
            prefs[Keys.BIG_FOR] = settings.preferences.bigFor
            prefs[Keys.FLASH_FOR] = settings.preferences.flashFor.coerceIn(1, 600)
            prefs[Keys.TOP_FLASH_ENABLED] = settings.preferences.topFlashEnabled
            prefs[Keys.TOP_FLASH_SMALL_TEXT] =
                settings.preferences.topFlashSmallText.ifBlank { defaultPreferences.topFlashSmallText }
            prefs[Keys.TOP_FLASH_BIG_TEXT] =
                settings.preferences.topFlashBigText.ifBlank { defaultPreferences.topFlashBigText }
            prefs[Keys.PRE_BREAK_NOTIFICATION_ENABLED] = settings.preferences.preBreakNotificationEnabled
            prefs[Keys.PRE_BREAK_NOTIFICATION_LEAD_SECONDS] =
                settings.preferences.preBreakNotificationLeadSeconds.coerceIn(1, 3600)
            prefs[Keys.PRE_BREAK_NOTIFICATION_SMALL_TITLE] =
                settings.preferences.preBreakNotificationSmallTitle.ifBlank { defaultPreferences.preBreakNotificationSmallTitle }
            prefs[Keys.PRE_BREAK_NOTIFICATION_SMALL_CONTENT] =
                settings.preferences.preBreakNotificationSmallContent.ifBlank { defaultPreferences.preBreakNotificationSmallContent }
            prefs[Keys.PRE_BREAK_NOTIFICATION_BIG_TITLE] =
                settings.preferences.preBreakNotificationBigTitle.ifBlank { defaultPreferences.preBreakNotificationBigTitle }
            prefs[Keys.PRE_BREAK_NOTIFICATION_BIG_CONTENT] =
                settings.preferences.preBreakNotificationBigContent.ifBlank { defaultPreferences.preBreakNotificationBigContent }
            prefs[Keys.POSTPONE_FOR] = formatPostponeDurations(settings.preferences.postponeFor)
            prefs.remove(Keys.POSTPONE_FOR_LEGACY)
            prefs.remove(Keys.RESET_INTERVAL_AFTER_PAUSE)
            prefs.remove(Keys.RESET_CYCLE_AFTER_PAUSE)
            prefs[Keys.PAUSE_IN_LISTED_APPS] = settings.pauseInListedApps
            prefs[Keys.MONITORED_APPS] = settings.monitoredApps
            prefs[Keys.AUTO_START_ON_BOOT] = settings.autoStartOnBoot
            prefs[Keys.APP_ENABLED] = settings.appEnabled
            prefs[Keys.OVERLAY_TRANSPARENCY_PERCENT] = settings.overlayTransparencyPercent.coerceIn(0, 100)
            prefs[Keys.OVERLAY_BACKGROUND_URI] = settings.overlayBackgroundUri
            prefs[Keys.ONBOARDING_COMPLETED] = settings.onboardingCompleted
            prefs[Keys.EXCLUDE_FROM_RECENTS] = settings.excludeFromRecents
            prefs[Keys.PERSISTENT_NOTIFICATION_ENABLED] = settings.persistentNotificationEnabled
            prefs[Keys.PERSISTENT_NOTIFICATION_UPDATE_FREQUENCY_SECONDS] =
                settings.persistentNotificationUpdateFrequencySeconds.coerceIn(1, 600)
            prefs[Keys.THEME_MODE] = settings.themeMode.storageValue
        }
    }

    private object Keys {
        val SMALL_EVERY = intPreferencesKey("small_every")
        val SMALL_FOR = intPreferencesKey("small_for")
        val BIG_AFTER = intPreferencesKey("big_after")
        val BIG_FOR = intPreferencesKey("big_for")
        val FLASH_FOR = intPreferencesKey("flash_for")
        val TOP_FLASH_ENABLED = booleanPreferencesKey("top_flash_enabled")
        val TOP_FLASH_SMALL_TEXT = stringPreferencesKey("top_flash_small_text")
        val TOP_FLASH_BIG_TEXT = stringPreferencesKey("top_flash_big_text")
        val PRE_BREAK_NOTIFICATION_ENABLED = booleanPreferencesKey("pre_break_notification_enabled")
        val PRE_BREAK_NOTIFICATION_LEAD_SECONDS = intPreferencesKey("pre_break_notification_lead_seconds")
        val PRE_BREAK_NOTIFICATION_SMALL_TITLE = stringPreferencesKey("pre_break_notification_small_title")
        val PRE_BREAK_NOTIFICATION_SMALL_CONTENT = stringPreferencesKey("pre_break_notification_small_content")
        val PRE_BREAK_NOTIFICATION_BIG_TITLE = stringPreferencesKey("pre_break_notification_big_title")
        val PRE_BREAK_NOTIFICATION_BIG_CONTENT = stringPreferencesKey("pre_break_notification_big_content")
        val POSTPONE_FOR = stringPreferencesKey("postpone_for_list")
        val POSTPONE_FOR_LEGACY = intPreferencesKey("postpone_for")
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
        val PERSISTENT_NOTIFICATION_UPDATE_FREQUENCY_SECONDS =
            intPreferencesKey("persistent_notification_update_frequency_seconds")
        val THEME_MODE = intPreferencesKey("theme_mode")
    }
}
