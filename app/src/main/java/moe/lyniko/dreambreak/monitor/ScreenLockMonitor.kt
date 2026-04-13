package moe.lyniko.dreambreak.monitor

import android.content.Context
import androidx.core.content.ContextCompat

object ScreenLockMonitor {
    private val startLock = Any()
    private var receiver: ScreenLockReceiver? = null

    fun start(context: Context) {
        val appContext = context.applicationContext
        synchronized(startLock) {
            if (receiver != null) {
                return
            }

            val screenLockReceiver = ScreenLockReceiver()
            ContextCompat.registerReceiver(
                appContext,
                screenLockReceiver,
                ScreenLockReceiver.intentFilter(),
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiver = screenLockReceiver
        }
    }
}
