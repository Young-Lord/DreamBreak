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
import moe.lyniko.dreambreak.core.BREAK_EXIT_POSTPONE_MAX
import moe.lyniko.dreambreak.core.BREAK_EXIT_POSTPONE_MIN
import moe.lyniko.dreambreak.core.DEFAULT_PERSISTENT_NOTIFICATION_CONTENT_TEMPLATE
import moe.lyniko.dreambreak.core.DEFAULT_PERSISTENT_NOTIFICATION_TITLE_TEMPLATE
import moe.lyniko.dreambreak.core.DEFAULT_POSTPONE_DURATION_SECONDS
import moe.lyniko.dreambreak.core.FLASH_FOR_MAX
import moe.lyniko.dreambreak.core.FLASH_FOR_MIN
import moe.lyniko.dreambreak.core.NOTIFICATION_FREQUENCY_MAX
import moe.lyniko.dreambreak.core.NOTIFICATION_FREQUENCY_MIN
import moe.lyniko.dreambreak.core.OVERLAY_ANIMATION_DURATION_MAX
import moe.lyniko.dreambreak.core.OVERLAY_ANIMATION_DURATION_MIN
import moe.lyniko.dreambreak.core.OVERLAY_TRANSPARENCY_MAX
import moe.lyniko.dreambreak.core.OVERLAY_TRANSPARENCY_MIN
import moe.lyniko.dreambreak.core.PRE_BREAK_LEAD_SECONDS_MAX
import moe.lyniko.dreambreak.core.PRE_BREAK_LEAD_SECONDS_MIN
import moe.lyniko.dreambreak.core.formatPostponeDurations
import moe.lyniko.dreambreak.core.parsePostponeDurations

private val Context.settingsDataStore by preferencesDataStore(name = "dream_break_settings")

data class AppSettings(
    val preferences: BreakPreferences = BreakPreferences(),
    val pauseInListedApps: Boolean = false,
    val appListMode: AppListMode = AppListMode.WHITELIST,
    val monitoredApps: String = "",
    val monitoredAppsBlacklist: String = "",
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
    val breakOverlayFadeInDurationMs: Int = 300,
    val breakOverlayFadeOutDurationMs: Int = 300,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val hasVisitedSpecificAppsPage: Boolean = false,
    val hasEnabledPauseInListedAppsOnce: Boolean = false,
    val hasAddedExternalPauseAppOnce: Boolean = false,
    val restoreEnabledStateOnStart: Boolean = false,
    val reenableOnScreenUnlock: Boolean = false,
)

class SettingsStore(private val context: Context) {
    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val defaultPreferences = BreakPreferences()
        val preferences = BreakPreferences(
            smallEvery = prefs[Keys.SMALL_EVERY] ?: defaultPreferences.smallEvery,
            smallFor = prefs[Keys.SMALL_FOR] ?: defaultPreferences.smallFor,
            bigAfter = prefs[Keys.BIG_AFTER] ?: defaultPreferences.bigAfter,
            bigFor = prefs[Keys.BIG_FOR] ?: defaultPreferences.bigFor,
            flashFor = (prefs[Keys.FLASH_FOR] ?: defaultPreferences.flashFor).coerceIn(FLASH_FOR_MIN, FLASH_FOR_MAX),
            topFlashEnabled = prefs[Keys.TOP_FLASH_ENABLED] ?: defaultPreferences.topFlashEnabled,
            topFlashSmallText = prefs[Keys.TOP_FLASH_SMALL_TEXT] ?: defaultPreferences.topFlashSmallText,
            topFlashBigText = prefs[Keys.TOP_FLASH_BIG_TEXT] ?: defaultPreferences.topFlashBigText,
            preBreakNotificationEnabled =
                prefs[Keys.PRE_BREAK_NOTIFICATION_ENABLED] ?: defaultPreferences.preBreakNotificationEnabled,
            preBreakNotificationLeadSeconds =
                (prefs[Keys.PRE_BREAK_NOTIFICATION_LEAD_SECONDS] ?: defaultPreferences.preBreakNotificationLeadSeconds)
                    .coerceIn(PRE_BREAK_LEAD_SECONDS_MIN, PRE_BREAK_LEAD_SECONDS_MAX),
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
        val legacyOverlayAnimationDurationMs =
            (prefs[Keys.BREAK_OVERLAY_ANIMATION_DURATION_MS] ?: 300).coerceIn(OVERLAY_ANIMATION_DURATION_MIN, OVERLAY_ANIMATION_DURATION_MAX)
        val monitoredApps = prefs[Keys.MONITORED_APPS] ?: ""
        val monitoredAppsBlacklist = prefs[Keys.MONITORED_APPS_BLACKLIST] ?: ""
        val appListMode = AppListMode.fromStorage(prefs[Keys.APP_LIST_MODE])
        val pauseInListedApps = prefs[Keys.PAUSE_IN_LISTED_APPS] ?: false
        val inferredVisitedSpecificAppsPage =
            pauseInListedApps || monitoredApps.isNotBlank() || monitoredAppsBlacklist.isNotBlank()
        val inferredEnabledPauseInListedApps = pauseInListedApps
        val inferredAddedExternalPauseApp = hasExternalAppConfigured(
            monitoredApps = monitoredApps,
            selfPackageName = context.packageName,
        ) || hasExternalAppConfigured(
            monitoredApps = monitoredAppsBlacklist,
            selfPackageName = context.packageName,
        )
        AppSettings(
            preferences = preferences,
            pauseInListedApps = pauseInListedApps,
            appListMode = appListMode,
            monitoredApps = monitoredApps,
            monitoredAppsBlacklist = monitoredAppsBlacklist,
            autoStartOnBoot = prefs[Keys.AUTO_START_ON_BOOT] ?: false,
            appEnabled = prefs[Keys.APP_ENABLED] ?: true,
            overlayTransparencyPercent = (prefs[Keys.OVERLAY_TRANSPARENCY_PERCENT] ?: 28).coerceIn(OVERLAY_TRANSPARENCY_MIN, OVERLAY_TRANSPARENCY_MAX),
            overlayBackgroundPortraitUri =
                prefs[Keys.OVERLAY_BACKGROUND_PORTRAIT_URI] ?: legacyOverlayBackgroundUri,
            overlayBackgroundLandscapeUri =
                prefs[Keys.OVERLAY_BACKGROUND_LANDSCAPE_URI] ?: legacyOverlayBackgroundUri,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            excludeFromRecents = prefs[Keys.EXCLUDE_FROM_RECENTS] ?: false,
            persistentNotificationEnabled = prefs[Keys.PERSISTENT_NOTIFICATION_ENABLED] ?: false,
            persistentNotificationUpdateFrequencySeconds =
                (prefs[Keys.PERSISTENT_NOTIFICATION_UPDATE_FREQUENCY_SECONDS] ?: 10).coerceIn(NOTIFICATION_FREQUENCY_MIN, NOTIFICATION_FREQUENCY_MAX),
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
                (prefs[Keys.BREAK_EXIT_POSTPONE_SECONDS] ?: DEFAULT_POSTPONE_DURATION_SECONDS).coerceIn(BREAK_EXIT_POSTPONE_MIN, BREAK_EXIT_POSTPONE_MAX),
            breakOverlayFadeInDurationMs =
                (prefs[Keys.BREAK_OVERLAY_FADE_IN_DURATION_MS] ?: legacyOverlayAnimationDurationMs)
                    .coerceIn(OVERLAY_ANIMATION_DURATION_MIN, OVERLAY_ANIMATION_DURATION_MAX),
            breakOverlayFadeOutDurationMs =
                (prefs[Keys.BREAK_OVERLAY_FADE_OUT_DURATION_MS] ?: legacyOverlayAnimationDurationMs)
                    .coerceIn(OVERLAY_ANIMATION_DURATION_MIN, OVERLAY_ANIMATION_DURATION_MAX),
            themeMode = AppThemeMode.fromStorage(prefs[Keys.THEME_MODE]),
            hasVisitedSpecificAppsPage =
                prefs[Keys.HAS_VISITED_SPECIFIC_APPS_PAGE] ?: inferredVisitedSpecificAppsPage,
            hasEnabledPauseInListedAppsOnce =
                prefs[Keys.HAS_ENABLED_PAUSE_IN_LISTED_APPS_ONCE] ?: inferredEnabledPauseInListedApps,
            hasAddedExternalPauseAppOnce =
                prefs[Keys.HAS_ADDED_EXTERNAL_PAUSE_APP_ONCE] ?: inferredAddedExternalPauseApp,
            restoreEnabledStateOnStart = prefs[Keys.RESTORE_ENABLED_STATE_ON_START] ?: false,
            reenableOnScreenUnlock = prefs[Keys.REENABLE_ON_SCREEN_UNLOCK] ?: false,
        )
    }

    suspend fun save(settings: AppSettings) {
        val defaultPreferences = BreakPreferences()
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SMALL_EVERY] = settings.preferences.smallEvery
            prefs[Keys.SMALL_FOR] = settings.preferences.smallFor
            prefs[Keys.BIG_AFTER] = settings.preferences.bigAfter
            prefs[Keys.BIG_FOR] = settings.preferences.bigFor
            prefs[Keys.FLASH_FOR] = settings.preferences.flashFor.coerceIn(FLASH_FOR_MIN, FLASH_FOR_MAX)
            prefs[Keys.TOP_FLASH_ENABLED] = settings.preferences.topFlashEnabled
            prefs[Keys.TOP_FLASH_SMALL_TEXT] =
                settings.preferences.topFlashSmallText.ifBlank { defaultPreferences.topFlashSmallText }
            prefs[Keys.TOP_FLASH_BIG_TEXT] =
                settings.preferences.topFlashBigText.ifBlank { defaultPreferences.topFlashBigText }
            prefs[Keys.PRE_BREAK_NOTIFICATION_ENABLED] = settings.preferences.preBreakNotificationEnabled
            prefs[Keys.PRE_BREAK_NOTIFICATION_LEAD_SECONDS] =
                settings.preferences.preBreakNotificationLeadSeconds.coerceIn(PRE_BREAK_LEAD_SECONDS_MIN, PRE_BREAK_LEAD_SECONDS_MAX)
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
            prefs[Keys.APP_LIST_MODE] = settings.appListMode.storageValue
            prefs[Keys.MONITORED_APPS] = settings.monitoredApps
            prefs[Keys.MONITORED_APPS_BLACKLIST] = settings.monitoredAppsBlacklist
            prefs[Keys.AUTO_START_ON_BOOT] = settings.autoStartOnBoot
            prefs[Keys.APP_ENABLED] = settings.appEnabled
            prefs[Keys.OVERLAY_TRANSPARENCY_PERCENT] = settings.overlayTransparencyPercent.coerceIn(OVERLAY_TRANSPARENCY_MIN, OVERLAY_TRANSPARENCY_MAX)
            prefs[Keys.OVERLAY_BACKGROUND_PORTRAIT_URI] = settings.overlayBackgroundPortraitUri
            prefs[Keys.OVERLAY_BACKGROUND_LANDSCAPE_URI] = settings.overlayBackgroundLandscapeUri
            prefs[Keys.OVERLAY_BACKGROUND_URI] = settings.overlayBackgroundPortraitUri
            prefs[Keys.ONBOARDING_COMPLETED] = settings.onboardingCompleted
            prefs[Keys.EXCLUDE_FROM_RECENTS] = settings.excludeFromRecents
            prefs[Keys.PERSISTENT_NOTIFICATION_ENABLED] = settings.persistentNotificationEnabled
            prefs[Keys.PERSISTENT_NOTIFICATION_UPDATE_FREQUENCY_SECONDS] =
                settings.persistentNotificationUpdateFrequencySeconds.coerceIn(NOTIFICATION_FREQUENCY_MIN, NOTIFICATION_FREQUENCY_MAX)
            prefs[Keys.PERSISTENT_NOTIFICATION_TITLE_TEMPLATE] =
                settings.persistentNotificationTitleTemplate.trim()
                    .ifBlank { DEFAULT_PERSISTENT_NOTIFICATION_TITLE_TEMPLATE }
            prefs[Keys.PERSISTENT_NOTIFICATION_CONTENT_TEMPLATE] =
                settings.persistentNotificationContentTemplate.trim()
                    .ifBlank { DEFAULT_PERSISTENT_NOTIFICATION_CONTENT_TEMPLATE }
            prefs[Keys.QS_TILE_COUNTDOWN_AS_TITLE] = settings.qsTileCountdownAsTitle
            prefs[Keys.BREAK_SHOW_POSTPONE_BUTTON] = settings.breakShowPostponeButton
            prefs[Keys.BREAK_SHOW_TITLE] = settings.breakShowTitle
            prefs[Keys.BREAK_SHOW_COUNTDOWN] = settings.breakShowCountdown
            prefs[Keys.BREAK_SHOW_EXIT_BUTTON] = settings.breakShowExitButton
            prefs[Keys.BREAK_EXIT_POSTPONE_SECONDS] = settings.breakExitPostponeSeconds.coerceIn(BREAK_EXIT_POSTPONE_MIN, BREAK_EXIT_POSTPONE_MAX)
            prefs[Keys.BREAK_OVERLAY_FADE_IN_DURATION_MS] =
                settings.breakOverlayFadeInDurationMs.coerceIn(OVERLAY_ANIMATION_DURATION_MIN, OVERLAY_ANIMATION_DURATION_MAX)
            prefs[Keys.BREAK_OVERLAY_FADE_OUT_DURATION_MS] =
                settings.breakOverlayFadeOutDurationMs.coerceIn(OVERLAY_ANIMATION_DURATION_MIN, OVERLAY_ANIMATION_DURATION_MAX)
            prefs[Keys.BREAK_OVERLAY_ANIMATION_DURATION_MS] =
                settings.breakOverlayFadeInDurationMs.coerceIn(OVERLAY_ANIMATION_DURATION_MIN, OVERLAY_ANIMATION_DURATION_MAX)
            prefs[Keys.THEME_MODE] = settings.themeMode.storageValue
            prefs[Keys.HAS_VISITED_SPECIFIC_APPS_PAGE] = settings.hasVisitedSpecificAppsPage
            prefs[Keys.HAS_ENABLED_PAUSE_IN_LISTED_APPS_ONCE] = settings.hasEnabledPauseInListedAppsOnce
            prefs[Keys.HAS_ADDED_EXTERNAL_PAUSE_APP_ONCE] = settings.hasAddedExternalPauseAppOnce
            prefs[Keys.RESTORE_ENABLED_STATE_ON_START] = settings.restoreEnabledStateOnStart
            prefs[Keys.REENABLE_ON_SCREEN_UNLOCK] = settings.reenableOnScreenUnlock
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
        val APP_LIST_MODE = intPreferencesKey("app_list_mode")
        val MONITORED_APPS = stringPreferencesKey("monitored_apps")
        val MONITORED_APPS_BLACKLIST = stringPreferencesKey("monitored_apps_blacklist")
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
        val BREAK_OVERLAY_FADE_IN_DURATION_MS = intPreferencesKey("break_overlay_fade_in_duration_ms")
        val BREAK_OVERLAY_FADE_OUT_DURATION_MS = intPreferencesKey("break_overlay_fade_out_duration_ms")
        val BREAK_OVERLAY_ANIMATION_DURATION_MS = intPreferencesKey("break_overlay_animation_duration_ms")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val HAS_VISITED_SPECIFIC_APPS_PAGE =
            booleanPreferencesKey("has_visited_specific_apps_page")
        val HAS_ENABLED_PAUSE_IN_LISTED_APPS_ONCE =
            booleanPreferencesKey("has_enabled_pause_in_listed_apps_once")
        val HAS_ADDED_EXTERNAL_PAUSE_APP_ONCE =
            booleanPreferencesKey("has_added_external_pause_app_once")
        val RESTORE_ENABLED_STATE_ON_START =
            booleanPreferencesKey("restore_enabled_state_on_start")
        val REENABLE_ON_SCREEN_UNLOCK =
            booleanPreferencesKey("reenable_on_screen_unlock")
    }
}

private fun hasExternalAppConfigured(monitoredApps: String, selfPackageName: String): Boolean {
    return monitoredApps
        .split(',')
        .map { it.trim() }
        .any { it.isNotEmpty() && it != selfPackageName }
}
