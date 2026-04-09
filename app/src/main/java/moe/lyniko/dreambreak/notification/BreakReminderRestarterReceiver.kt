package moe.lyniko.dreambreak.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.lyniko.dreambreak.MainActivity
import moe.lyniko.dreambreak.core.BreakRuntime
import moe.lyniko.dreambreak.data.SettingsStore
import moe.lyniko.dreambreak.monitor.AppPauseMonitor

class BreakReminderRestarterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BreakReminderService.ACTION_RESTART_SERVICE) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val appContext = context.applicationContext
                val settings = SettingsStore(appContext).settingsFlow.first()
                BreakRuntime.restoreSettings(
                    preferences = settings.preferences,
                    pauseInListedApps = settings.pauseInListedApps,
                    monitoredApps = settings.monitoredApps,
                    autoStartOnBoot = settings.autoStartOnBoot,
                    appEnabled = settings.appEnabled,
                    overlayTransparencyPercent = settings.overlayTransparencyPercent,
                    overlayBackgroundPortraitUri = settings.overlayBackgroundPortraitUri,
                    overlayBackgroundLandscapeUri = settings.overlayBackgroundLandscapeUri,
                    onboardingCompleted = settings.onboardingCompleted,
                    excludeFromRecents = settings.excludeFromRecents,
                    persistentNotificationEnabled = settings.persistentNotificationEnabled,
                    persistentNotificationUpdateFrequencySeconds = settings.persistentNotificationUpdateFrequencySeconds,
                    persistentNotificationTitleTemplate = settings.persistentNotificationTitleTemplate,
                    persistentNotificationContentTemplate = settings.persistentNotificationContentTemplate,
                    qsTileCountdownAsTitle = settings.qsTileCountdownAsTitle,
                    breakShowPostponeButton = settings.breakShowPostponeButton,
                    breakShowTitle = settings.breakShowTitle,
                    breakShowCountdown = settings.breakShowCountdown,
                    breakShowExitButton = settings.breakShowExitButton,
                    breakExitPostponeSeconds = settings.breakExitPostponeSeconds,
                    breakOverlayFadeInDurationMs = settings.breakOverlayFadeInDurationMs,
                    breakOverlayFadeOutDurationMs = settings.breakOverlayFadeOutDurationMs,
                    themeMode = settings.themeMode,
                    hasVisitedSpecificAppsPage = settings.hasVisitedSpecificAppsPage,
                    hasEnabledPauseInListedAppsOnce = settings.hasEnabledPauseInListedAppsOnce,
                    hasAddedExternalPauseAppOnce = settings.hasAddedExternalPauseAppOnce,
                )

                val uiState = BreakRuntime.uiState.value
                if (!uiState.appEnabled || !uiState.persistentNotificationEnabled) {
                    return@runCatching
                }
                if (!MainActivity.hasNotificationPermission(appContext)) {
                    return@runCatching
                }

                BreakRuntime.start()
                AppPauseMonitor.start(appContext)
                BreakReminderService.start(appContext)
            }
            pendingResult.finish()
        }
    }
}
