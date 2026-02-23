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
import moe.lyniko.dreambreak.notification.BreakReminderService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_QUICK_BOOT_POWER_ON) {
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
                    overlayBackgroundUri = settings.overlayBackgroundUri,
                    onboardingCompleted = settings.onboardingCompleted,
                    excludeFromRecents = settings.excludeFromRecents,
                    persistentNotificationEnabled = settings.persistentNotificationEnabled,
                    persistentNotificationUpdateFrequencySeconds = settings.persistentNotificationUpdateFrequencySeconds,
                    themeMode = settings.themeMode,
                )
                if (
                    settings.autoStartOnBoot &&
                    settings.appEnabled &&
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
