package moe.lyniko.dreambreak.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import moe.lyniko.dreambreak.MainActivity
import moe.lyniko.dreambreak.R
import moe.lyniko.dreambreak.core.BreakPhase
import moe.lyniko.dreambreak.core.BreakPreferences
import moe.lyniko.dreambreak.core.BreakRuntime
import moe.lyniko.dreambreak.core.BreakState
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
                val hasPermission = MainActivity.hasNotificationPermission(this@BreakReminderService)
                val shouldRun = uiState.persistentNotificationEnabled && uiState.appEnabled && hasPermission
                if (!shouldRun) {
                    if (uiState.persistentNotificationEnabled && !hasPermission) {
                        BreakRuntime.setPersistentNotificationEnabled(false)
                    }
                    NotificationManagerCompat.from(this@BreakReminderService).cancel(NOTIFICATION_ID)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }

                NotificationManagerCompat.from(this@BreakReminderService)
                    .notify(NOTIFICATION_ID, buildNotification(uiState.state, uiState.preferences))
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
        val hasPermission = MainActivity.hasNotificationPermission(this)
        if (!uiState.persistentNotificationEnabled || !uiState.appEnabled || !hasPermission) {
            if (uiState.persistentNotificationEnabled && !hasPermission) {
                BreakRuntime.setPersistentNotificationEnabled(false)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(uiState.state, uiState.preferences)
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

    private fun buildNotification(state: BreakState, preferences: BreakPreferences): android.app.Notification {
        val nextBreakType = if (isNextBreakBig(state, preferences)) {
            getString(R.string.break_type_big)
        } else {
            getString(R.string.break_type_small)
        }
        val secondsUntilNextBreak = secondsUntilNextBreak(state, preferences)
        val content = getString(
            R.string.notification_content_next,
            nextBreakType,
            formatSeconds(secondsUntilNextBreak),
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                getString(
                    R.string.notification_title_progress,
                    state.completedSmallBreaks,
                    state.completedBigBreaks,
                )
            )
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
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
                postponePickerPendingIntent(),
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
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

    private fun postponePickerPendingIntent(): PendingIntent {
        val intent = Intent(this, PostponePickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_CODE_POSTPONE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun isNextBreakBig(state: BreakState, preferences: BreakPreferences): Boolean {
        if (preferences.bigAfter <= 0) {
            return false
        }
        val nextCycle = state.breakCycleCount + 1
        return nextCycle % preferences.bigAfter == 0
    }

    private fun secondsUntilNextBreak(state: BreakState, preferences: BreakPreferences): Int {
        if (state.mode != SessionMode.BREAK) {
            return state.secondsToNextBreak.coerceAtLeast(0)
        }

        val currentBreakRemaining = when (state.phase) {
            BreakPhase.PROMPT -> {
                val promptRemaining = (preferences.flashFor - state.promptSecondsElapsed).coerceAtLeast(0)
                val fullScreenDuration = if (state.isBigBreak) preferences.bigFor else preferences.smallFor
                promptRemaining + fullScreenDuration
            }

            BreakPhase.FULL_SCREEN, BreakPhase.POST -> state.breakSecondsRemaining.coerceAtLeast(0)
            null -> 0
        }

        return currentBreakRemaining + preferences.smallEvery
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
        private const val REQUEST_CODE_POSTPONE = 881

        fun start(context: Context) {
            val serviceIntent = Intent(context, BreakReminderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BreakReminderService::class.java))
        }
    }
}
