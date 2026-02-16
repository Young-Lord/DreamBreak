package moe.lyniko.dreambreak.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
                val settings = SettingsStore(context.applicationContext).settingsFlow.first()
                if (settings.autoStartOnBoot && settings.appEnabled) {
                    BreakReminderService.start(context.applicationContext)
                }
            }
            pendingResult.finish()
        }
    }

    companion object {
        private const val ACTION_QUICK_BOOT_POWER_ON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
