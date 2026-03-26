package moe.lyniko.dreambreak.startup

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
import moe.lyniko.dreambreak.notification.BreakReminderService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != ACTION_QUICK_BOOT_POWER_ON &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
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
                )
                val shouldStartRuntime = when (action) {
                    Intent.ACTION_MY_PACKAGE_REPLACED -> settings.appEnabled
                    else -> settings.autoStartOnBoot && settings.appEnabled
                }

                if (shouldStartRuntime) {
                    BreakRuntime.start()
                    AppPauseMonitor.start(appContext)
                }

                val shouldStartService = when (action) {
                    Intent.ACTION_MY_PACKAGE_REPLACED -> settings.appEnabled
                    else -> settings.autoStartOnBoot && settings.appEnabled
                }

                if (
                    shouldStartService &&
                    settings.persistentNotificationEnabled &&
                    MainActivity.hasNotificationPermission(appContext)
                ) {
                    BreakReminderService.start(appContext)
                }
            }
            pendingResult.finish()
        }
    }

    companion object {
        private const val ACTION_QUICK_BOOT_POWER_ON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
