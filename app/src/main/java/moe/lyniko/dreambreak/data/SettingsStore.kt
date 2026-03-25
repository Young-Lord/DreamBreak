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
import moe.lyniko.dreambreak.core.DEFAULT_POSTPONE_DURATION_SECONDS
import moe.lyniko.dreambreak.core.DEFAULT_PERSISTENT_NOTIFICATION_CONTENT_TEMPLATE
import moe.lyniko.dreambreak.core.DEFAULT_PERSISTENT_NOTIFICATION_TITLE_TEMPLATE
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
    val overlayBackgroundPortraitUri: String = "",
    val overlayBackgroundLandscapeUri: String = "",
    val onboardingCompleted: Boolean = false,
    val excludeFromRecents: Boolean = false,
    val persistentNotificationEnabled: Boolean = false,
    val persistentNotificationUpdateFrequencySeconds: Int = 10,
    val persistentNotificationTitleTemplate: String = DEFAULT_PERSISTENT_NOTIFICATION_TITLE_TEMPLATE,
    val persistentNotificationContentTemplate: String = DEFAULT_PERSISTENT_NOTIFICATION_CONTENT_TEMPLATE,
    val qsTileCountdownAsTitle: Boolean = false,
    val breakShowPostponeButton: Boolean = true,
    val breakShowTitle: Boolean = true,
    val breakShowCountdown: Boolean = true,
    val breakShowExitButton: Boolean = true,
    val breakExitPostponeSeconds: Int = DEFAULT_POSTPONE_DURATION_SECONDS,
    val breakOverlayAnimationDurationMs: Int = 300,
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
        val legacyOverlayBackgroundUri = prefs[Keys.OVERLAY_BACKGROUND_URI] ?: ""
        AppSettings(
            preferences = preferences,
            pauseInListedApps = prefs[Keys.PAUSE_IN_LISTED_APPS] ?: false,
            monitoredApps = prefs[Keys.MONITORED_APPS] ?: "",
            autoStartOnBoot = prefs[Keys.AUTO_START_ON_BOOT] ?: false,
            appEnabled = prefs[Keys.APP_ENABLED] ?: true,
            overlayTransparencyPercent = (prefs[Keys.OVERLAY_TRANSPARENCY_PERCENT] ?: 28).coerceIn(0, 100),
            overlayBackgroundPortraitUri =
                prefs[Keys.OVERLAY_BACKGROUND_PORTRAIT_URI] ?: legacyOverlayBackgroundUri,
            overlayBackgroundLandscapeUri =
                prefs[Keys.OVERLAY_BACKGROUND_LANDSCAPE_URI] ?: legacyOverlayBackgroundUri,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            excludeFromRecents = prefs[Keys.EXCLUDE_FROM_RECENTS] ?: false,
            persistentNotificationEnabled = prefs[Keys.PERSISTENT_NOTIFICATION_ENABLED] ?: false,
            persistentNotificationUpdateFrequencySeconds =
                (prefs[Keys.PERSISTENT_NOTIFICATION_UPDATE_FREQUENCY_SECONDS] ?: 10).coerceIn(1, 600),
            persistentNotificationTitleTemplate =
                prefs[Keys.PERSISTENT_NOTIFICATION_TITLE_TEMPLATE]
                    ?: DEFAULT_PERSISTENT_NOTIFICATION_TITLE_TEMPLATE,
            persistentNotificationContentTemplate =
                prefs[Keys.PERSISTENT_NOTIFICATION_CONTENT_TEMPLATE]
                    ?: DEFAULT_PERSISTENT_NOTIFICATION_CONTENT_TEMPLATE,
            qsTileCountdownAsTitle = prefs[Keys.QS_TILE_COUNTDOWN_AS_TITLE] ?: false,
            breakShowPostponeButton = prefs[Keys.BREAK_SHOW_POSTPONE_BUTTON] ?: true,
            breakShowTitle = prefs[Keys.BREAK_SHOW_TITLE] ?: true,
            breakShowCountdown = prefs[Keys.BREAK_SHOW_COUNTDOWN] ?: true,
            breakShowExitButton = prefs[Keys.BREAK_SHOW_EXIT_BUTTON] ?: true,
            breakExitPostponeSeconds =
                (prefs[Keys.BREAK_EXIT_POSTPONE_SECONDS] ?: DEFAULT_POSTPONE_DURATION_SECONDS).coerceIn(1, 3600),
            breakOverlayAnimationDurationMs =
                (prefs[Keys.BREAK_OVERLAY_ANIMATION_DURATION_MS] ?: 300).coerceIn(0, 5000),
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
                settings.preferences.preBreakNotificationSmallContent
            prefs[Keys.PRE_BREAK_NOTIFICATION_BIG_TITLE] =
                settings.preferences.preBreakNotificationBigTitle.ifBlank { defaultPreferences.preBreakNotificationBigTitle }
            prefs[Keys.PRE_BREAK_NOTIFICATION_BIG_CONTENT] =
                settings.preferences.preBreakNotificationBigContent
            prefs[Keys.POSTPONE_FOR] = formatPostponeDurations(settings.preferences.postponeFor)
            prefs.remove(Keys.POSTPONE_FOR_LEGACY)
            prefs.remove(Keys.RESET_INTERVAL_AFTER_PAUSE)
            prefs.remove(Keys.RESET_CYCLE_AFTER_PAUSE)
            prefs[Keys.PAUSE_IN_LISTED_APPS] = settings.pauseInListedApps
            prefs[Keys.MONITORED_APPS] = settings.monitoredApps
            prefs[Keys.AUTO_START_ON_BOOT] = settings.autoStartOnBoot
            prefs[Keys.APP_ENABLED] = settings.appEnabled
            prefs[Keys.OVERLAY_TRANSPARENCY_PERCENT] = settings.overlayTransparencyPercent.coerceIn(0, 100)
            prefs[Keys.OVERLAY_BACKGROUND_PORTRAIT_URI] = settings.overlayBackgroundPortraitUri
            prefs[Keys.OVERLAY_BACKGROUND_LANDSCAPE_URI] = settings.overlayBackgroundLandscapeUri
            prefs[Keys.OVERLAY_BACKGROUND_URI] = settings.overlayBackgroundPortraitUri
            prefs[Keys.ONBOARDING_COMPLETED] = settings.onboardingCompleted
            prefs[Keys.EXCLUDE_FROM_RECENTS] = settings.excludeFromRecents
            prefs[Keys.PERSISTENT_NOTIFICATION_ENABLED] = settings.persistentNotificationEnabled
            prefs[Keys.PERSISTENT_NOTIFICATION_UPDATE_FREQUENCY_SECONDS] =
                settings.persistentNotificationUpdateFrequencySeconds.coerceIn(1, 600)
            prefs[Keys.PERSISTENT_NOTIFICATION_TITLE_TEMPLATE] =
                settings.persistentNotificationTitleTemplate.trim()
                    .ifBlank { DEFAULT_PERSISTENT_NOTIFICATION_TITLE_TEMPLATE }
            prefs[Keys.PERSISTENT_NOTIFICATION_CONTENT_TEMPLATE] =
                settings.persistentNotificationContentTemplate.trim()
            prefs[Keys.QS_TILE_COUNTDOWN_AS_TITLE] = settings.qsTileCountdownAsTitle
            prefs[Keys.BREAK_SHOW_POSTPONE_BUTTON] = settings.breakShowPostponeButton
            prefs[Keys.BREAK_SHOW_TITLE] = settings.breakShowTitle
            prefs[Keys.BREAK_SHOW_COUNTDOWN] = settings.breakShowCountdown
            prefs[Keys.BREAK_SHOW_EXIT_BUTTON] = settings.breakShowExitButton
            prefs[Keys.BREAK_EXIT_POSTPONE_SECONDS] = settings.breakExitPostponeSeconds.coerceIn(1, 3600)
            prefs[Keys.BREAK_OVERLAY_ANIMATION_DURATION_MS] =
                settings.breakOverlayAnimationDurationMs.coerceIn(0, 5000)
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
        val OVERLAY_BACKGROUND_PORTRAIT_URI = stringPreferencesKey("overlay_background_portrait_uri")
        val OVERLAY_BACKGROUND_LANDSCAPE_URI = stringPreferencesKey("overlay_background_landscape_uri")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val EXCLUDE_FROM_RECENTS = booleanPreferencesKey("exclude_from_recents")
        val PERSISTENT_NOTIFICATION_ENABLED = booleanPreferencesKey("persistent_notification_enabled")
        val PERSISTENT_NOTIFICATION_UPDATE_FREQUENCY_SECONDS =
            intPreferencesKey("persistent_notification_update_frequency_seconds")
        val PERSISTENT_NOTIFICATION_TITLE_TEMPLATE =
            stringPreferencesKey("persistent_notification_title_template")
        val PERSISTENT_NOTIFICATION_CONTENT_TEMPLATE =
            stringPreferencesKey("persistent_notification_content_template")
        val QS_TILE_COUNTDOWN_AS_TITLE = booleanPreferencesKey("qs_tile_countdown_as_title")
        val BREAK_SHOW_POSTPONE_BUTTON = booleanPreferencesKey("break_show_postpone_button")
        val BREAK_SHOW_TITLE = booleanPreferencesKey("break_show_title")
        val BREAK_SHOW_COUNTDOWN = booleanPreferencesKey("break_show_countdown")
        val BREAK_SHOW_EXIT_BUTTON = booleanPreferencesKey("break_show_exit_button")
        val BREAK_EXIT_POSTPONE_SECONDS = intPreferencesKey("break_exit_postpone_seconds")
        val BREAK_OVERLAY_ANIMATION_DURATION_MS = intPreferencesKey("break_overlay_animation_duration_ms")
        val THEME_MODE = intPreferencesKey("theme_mode")
    }
}
