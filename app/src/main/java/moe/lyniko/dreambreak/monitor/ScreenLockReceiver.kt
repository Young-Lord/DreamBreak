package moe.lyniko.dreambreak.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.lyniko.dreambreak.MainActivity
import moe.lyniko.dreambreak.core.BreakRuntime
import moe.lyniko.dreambreak.core.toAppSettings
import moe.lyniko.dreambreak.data.SettingsStore
import moe.lyniko.dreambreak.notification.BreakReminderService

class ScreenLockReceiver : BroadcastReceiver() {
    companion object {
        fun intentFilter(): IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> BreakRuntime.setScreenLocked(true)
            Intent.ACTION_USER_PRESENT -> {
                BreakRuntime.setScreenLocked(false)
                BreakRuntime.start()

                val updatedUiState = BreakRuntime.uiState.value
                if (
                    updatedUiState.appEnabled &&
                    updatedUiState.persistentNotificationEnabled &&
                    MainActivity.hasNotificationPermission(appContext)
                ) {
                    BreakReminderService.start(appContext)
                }

                if (updatedUiState.appEnabled) {
                    val pendingResult = goAsync()
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        runCatching { SettingsStore(appContext).save(updatedUiState.toAppSettings()) }
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
