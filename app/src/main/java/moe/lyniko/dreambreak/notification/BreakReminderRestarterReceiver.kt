package moe.lyniko.dreambreak.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.lyniko.dreambreak.startup.RuntimeBootstrap

class BreakReminderRestarterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BreakReminderService.ACTION_RESTART_SERVICE) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val appContext = context.applicationContext
                RuntimeBootstrap.restoreFromDisk(appContext, isFirstLoad = true)
                RuntimeBootstrap.startRuntimeAndMonitors(appContext)
                RuntimeBootstrap.syncReminderService(appContext)
            }
            pendingResult.finish()
        }
    }
}
