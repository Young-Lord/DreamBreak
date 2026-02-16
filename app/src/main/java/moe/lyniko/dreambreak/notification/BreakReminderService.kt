package moe.lyniko.dreambreak.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import moe.lyniko.dreambreak.MainActivity
import moe.lyniko.dreambreak.R
import moe.lyniko.dreambreak.core.BreakRuntime
import moe.lyniko.dreambreak.core.SessionMode
import moe.lyniko.dreambreak.monitor.ScreenLockReceiver
import moe.lyniko.dreambreak.overlay.BreakOverlayController

class BreakReminderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Main.immediate)
    private var observeJob: Job? = null
    private lateinit var overlayController: BreakOverlayController
    private lateinit var screenLockReceiver: ScreenLockReceiver

    override fun onCreate() {
        super.onCreate()
        BreakRuntime.start()
        ensureChannel()
        overlayController = BreakOverlayController(
            this,
            onInterruptBreak = { BreakRuntime.interruptBreak() },
            onPostponeBreak = { seconds -> BreakRuntime.postponeBreakForSeconds(seconds) },
        )
        screenLockReceiver = ScreenLockReceiver()
        registerReceiver(screenLockReceiver, ScreenLockReceiver.intentFilter())
        observeJob = scope.launch {
            BreakRuntime.uiState.collectLatest { uiState ->
                NotificationManagerCompat.from(this@BreakReminderService)
                    .notify(NOTIFICATION_ID, buildNotification(uiState.state.mode, uiState.state.secondsToNextBreak, uiState.preferences.flashFor))
                overlayController.render(
                    state = uiState.state,
                    appEnabled = uiState.appEnabled,
                    overlayBackgroundUri = uiState.overlayBackgroundUri,
                    overlayTransparencyPercent = uiState.overlayTransparencyPercent,
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uiState = BreakRuntime.uiState.value
        val notification = buildNotification(uiState.state.mode, uiState.state.secondsToNextBreak, uiState.preferences.flashFor)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        unregisterReceiver(screenLockReceiver)
        overlayController.release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(mode: SessionMode, secondsToNextBreak: Int, flashFor: Int) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(notificationTitle(mode, secondsToNextBreak))
        .setContentText(notificationContent(mode, secondsToNextBreak, flashFor))
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .setOngoing(true)
        .addAction(
            0,
            getString(R.string.notification_action_break_now),
            actionPendingIntent(NotificationActionReceiver.ACTION_BREAK_NOW),
        )
        .addAction(
            0,
            getString(R.string.notification_action_postpone),
            actionPendingIntent(NotificationActionReceiver.ACTION_POSTPONE),
        )
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun notificationTitle(mode: SessionMode, secondsToNextBreak: Int): String {
        return if (mode == SessionMode.BREAK) {
            getString(R.string.notification_title_breaking)
        } else {
            getString(R.string.notification_title_countdown, formatSeconds(secondsToNextBreak))
        }
    }

    private fun notificationContent(mode: SessionMode, secondsToNextBreak: Int, flashFor: Int): String {
        return if (mode == SessionMode.NORMAL && secondsToNextBreak <= flashFor) {
            getString(R.string.notification_content_near)
        } else {
            getString(R.string.notification_content)
        }
    }

    private fun formatSeconds(rawSeconds: Int): String {
        val safe = rawSeconds.coerceAtLeast(0)
        val minutePart = safe / 60
        val secondPart = safe % 60
        return "%02d:%02d".format(minutePart, secondPart)
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, NotificationActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "dream_break_reminder"
        private const val NOTIFICATION_ID = 1124

        fun start(context: Context) {
            val serviceIntent = Intent(context, BreakReminderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
