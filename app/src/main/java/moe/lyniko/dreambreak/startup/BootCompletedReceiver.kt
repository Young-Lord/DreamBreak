package moe.lyniko.dreambreak.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.lyniko.dreambreak.core.BreakRuntime

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
                val settings = RuntimeBootstrap.restoreFromDisk(appContext)
                val effectiveAppEnabled = BreakRuntime.uiState.value.appEnabled
                val shouldStartRuntime = when (action) {
                    Intent.ACTION_MY_PACKAGE_REPLACED -> effectiveAppEnabled
                    else -> settings.autoStartOnBoot && effectiveAppEnabled
                }

                if (shouldStartRuntime) {
                    RuntimeBootstrap.startRuntimeAndMonitors(appContext)
                    RuntimeBootstrap.syncReminderService(appContext)
                }
            }
            pendingResult.finish()
        }
    }

    companion object {
        private const val ACTION_QUICK_BOOT_POWER_ON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
