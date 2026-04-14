package moe.lyniko.dreambreak.startup

import android.content.Context
import kotlinx.coroutines.flow.first
import moe.lyniko.dreambreak.MainActivity
import moe.lyniko.dreambreak.core.BreakRuntime
import moe.lyniko.dreambreak.data.AppSettings
import moe.lyniko.dreambreak.data.SettingsStore
import moe.lyniko.dreambreak.monitor.AppPauseMonitor
import moe.lyniko.dreambreak.monitor.ScreenLockMonitor
import moe.lyniko.dreambreak.notification.BreakReminderService

object RuntimeBootstrap {
    suspend fun restoreFromDisk(
        context: Context,
        isFirstLoad: Boolean = false,
    ): AppSettings {
        val settings = SettingsStore(context.applicationContext).settingsFlow.first()
        BreakRuntime.restoreSettings(settings, isFirstLoad = isFirstLoad)
        return settings
    }

    fun applySettings(
        settings: AppSettings,
        isFirstLoad: Boolean = false,
    ) {
        BreakRuntime.restoreSettings(settings, isFirstLoad = isFirstLoad)
    }

    fun startRuntimeAndMonitors(
        context: Context,
        startAppPauseMonitor: Boolean = true,
        startScreenLockMonitor: Boolean = true,
    ) {
        val appContext = context.applicationContext
        BreakRuntime.start()
        if (startAppPauseMonitor) {
            AppPauseMonitor.start(appContext)
        }
        if (startScreenLockMonitor) {
            ScreenLockMonitor.start(appContext)
        }
    }

    fun syncReminderService(context: Context) {
        val appContext = context.applicationContext
        val uiState = BreakRuntime.uiState.value
        val hasPermission = MainActivity.hasNotificationPermission(appContext)

        if (uiState.persistentNotificationEnabled && !hasPermission) {
            BreakRuntime.setPersistentNotificationEnabled(false)
            BreakReminderService.stop(appContext)
            return
        }

        if (uiState.appEnabled && uiState.persistentNotificationEnabled && hasPermission) {
            BreakReminderService.start(appContext)
        } else {
            BreakReminderService.stop(appContext)
        }
    }
}
