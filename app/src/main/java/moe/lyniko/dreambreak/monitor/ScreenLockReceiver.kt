package moe.lyniko.dreambreak.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import moe.lyniko.dreambreak.core.BreakRuntime

class ScreenLockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> BreakRuntime.setScreenLocked(true)
            Intent.ACTION_USER_PRESENT -> BreakRuntime.setScreenLocked(false)
        }
    }

    companion object {
        fun intentFilter(): IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
    }
}
