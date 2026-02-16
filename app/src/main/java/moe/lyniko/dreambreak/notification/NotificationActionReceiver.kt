package moe.lyniko.dreambreak.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import moe.lyniko.dreambreak.core.BreakRuntime
import moe.lyniko.dreambreak.R

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        BreakRuntime.start()
        when (intent?.action) {
            ACTION_BREAK_NOW -> {
                BreakRuntime.requestBreakNow()
                Toast.makeText(context, context.getString(R.string.notification_action_break_now), Toast.LENGTH_SHORT).show()
            }

            ACTION_POSTPONE -> {
                BreakRuntime.postponeBreak()
                Toast.makeText(context, context.getString(R.string.notification_action_postpone), Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val ACTION_BREAK_NOW = "moe.lyniko.dreambreak.action.BREAK_NOW"
        const val ACTION_POSTPONE = "moe.lyniko.dreambreak.action.POSTPONE"
    }
}
