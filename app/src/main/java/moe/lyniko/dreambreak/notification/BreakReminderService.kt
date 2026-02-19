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
import android.os.SystemClock
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
import moe.lyniko.dreambreak.core.DEFAULT_PRE_BREAK_BIG_CONTENT
import moe.lyniko.dreambreak.core.DEFAULT_PRE_BREAK_BIG_TITLE
import moe.lyniko.dreambreak.core.DEFAULT_PRE_BREAK_SMALL_CONTENT
import moe.lyniko.dreambreak.core.DEFAULT_PRE_BREAK_SMALL_TITLE
import moe.lyniko.dreambreak.core.SessionMode
import moe.lyniko.dreambreak.monitor.ScreenLockReceiver
import moe.lyniko.dreambreak.overlay.BreakOverlayController

class BreakReminderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Main.immediate)
    private var observeJob: Job? = null
    private lateinit var overlayController: BreakOverlayController
    private lateinit var screenLockReceiver: ScreenLockReceiver
    private var lastNotifiedState: BreakState? = null
    private var lastNotifiedPreferences: BreakPreferences? = null
    private var lastNotifiedNormalModeIntervalSeconds: Int = DEFAULT_NORMAL_MODE_UPDATE_SECONDS
    private var lastNotificationElapsedRealtimeMs: Long = 0L
    private var lastPreBreakNotifiedCycle: Int = -1

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
                    NotificationManagerCompat.from(this@BreakReminderService).cancel(PRE_BREAK_NOTIFICATION_ID)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }

                updateNotificationIfNeeded(
                    state = uiState.state,
                    preferences = uiState.preferences,
                    normalModeUpdateIntervalSeconds = uiState.persistentNotificationUpdateFrequencySeconds,
                )
                updatePreBreakNotificationIfNeeded(
                    state = uiState.state,
                    preferences = uiState.preferences,
                )
                overlayController.render(
                    state = uiState.state,
                    appEnabled = uiState.appEnabled,
                    overlayBackgroundUri = uiState.overlayBackgroundUri,
                    overlayTransparencyPercent = uiState.overlayTransparencyPercent,
                    postponeOptions = uiState.preferences.postponeFor,
                    topFlashSmallText = uiState.preferences.topFlashSmallText,
                    topFlashBigText = uiState.preferences.topFlashBigText,
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
        recordNotified(
            state = uiState.state,
            preferences = uiState.preferences,
            normalModeUpdateIntervalSeconds = uiState.persistentNotificationUpdateFrequencySeconds,
            atElapsedRealtimeMs = SystemClock.elapsedRealtime(),
        )
        return START_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        unregisterReceiver(screenLockReceiver)
        overlayController.release()
        NotificationManagerCompat.from(this).cancel(PRE_BREAK_NOTIFICATION_ID)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotificationIfNeeded(
        state: BreakState,
        preferences: BreakPreferences,
        normalModeUpdateIntervalSeconds: Int,
    ) {
        val safeIntervalSeconds = normalModeUpdateIntervalSeconds.coerceIn(1, 600)
        val nowElapsedRealtimeMs = SystemClock.elapsedRealtime()
        if (!shouldNotify(state, preferences, safeIntervalSeconds, nowElapsedRealtimeMs)) {
            return
        }

        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID, buildNotification(state, preferences))
        recordNotified(state, preferences, safeIntervalSeconds, nowElapsedRealtimeMs)
    }

    private fun shouldNotify(
        state: BreakState,
        preferences: BreakPreferences,
        normalModeUpdateIntervalSeconds: Int,
        nowElapsedRealtimeMs: Long,
    ): Boolean {
        val previousState = lastNotifiedState ?: return true
        val previousPreferences = lastNotifiedPreferences ?: return true

        if (previousPreferences != preferences) {
            return true
        }
        if (lastNotifiedNormalModeIntervalSeconds != normalModeUpdateIntervalSeconds) {
            return true
        }
        if (
            previousState.mode != state.mode ||
            previousState.phase != state.phase ||
            previousState.isBigBreak != state.isBigBreak ||
            previousState.breakCycleCount != state.breakCycleCount ||
            previousState.completedSmallBreaks != state.completedSmallBreaks ||
            previousState.completedBigBreaks != state.completedBigBreaks
        ) {
            return true
        }
        if (
            previousState.mode == SessionMode.NORMAL &&
            state.mode == SessionMode.NORMAL &&
            state.secondsToNextBreak > previousState.secondsToNextBreak + 1
        ) {
            return true
        }

        return when (state.mode) {
            SessionMode.NORMAL -> {
                nowElapsedRealtimeMs - lastNotificationElapsedRealtimeMs >= normalModeUpdateIntervalSeconds * 1000L
            }

            SessionMode.BREAK -> true
            SessionMode.PAUSED -> false
        }
    }

    private fun recordNotified(
        state: BreakState,
        preferences: BreakPreferences,
        normalModeUpdateIntervalSeconds: Int,
        atElapsedRealtimeMs: Long,
    ) {
        lastNotifiedState = state
        lastNotifiedPreferences = preferences
        lastNotifiedNormalModeIntervalSeconds = normalModeUpdateIntervalSeconds.coerceIn(1, 600)
        lastNotificationElapsedRealtimeMs = atElapsedRealtimeMs
    }

    private fun updatePreBreakNotificationIfNeeded(
        state: BreakState,
        preferences: BreakPreferences,
    ) {
        if (!preferences.preBreakNotificationEnabled) {
            NotificationManagerCompat.from(this).cancel(PRE_BREAK_NOTIFICATION_ID)
            lastPreBreakNotifiedCycle = -1
            return
        }

        if (state.mode != SessionMode.NORMAL) {
            NotificationManagerCompat.from(this).cancel(PRE_BREAK_NOTIFICATION_ID)
            return
        }

        val leadSeconds = preferences.preBreakNotificationLeadSeconds.coerceIn(1, 3600)
        val secondsToNextBreak = state.secondsToNextBreak.coerceAtLeast(0)
        val nextCycle = state.breakCycleCount + 1
        if (secondsToNextBreak == 0 || secondsToNextBreak > leadSeconds) {
            return
        }
        if (lastPreBreakNotifiedCycle == nextCycle) {
            return
        }

        val nextBreakBig = isNextBreakBig(state, preferences)
        NotificationManagerCompat.from(this).notify(
            PRE_BREAK_NOTIFICATION_ID,
            buildPreBreakNotification(
                nextBreakBig = nextBreakBig,
                secondsToNextBreak = secondsToNextBreak,
                preferences = preferences,
            ),
        )
        lastPreBreakNotifiedCycle = nextCycle
    }

    private fun buildPreBreakNotification(
        nextBreakBig: Boolean,
        secondsToNextBreak: Int,
        preferences: BreakPreferences,
    ): android.app.Notification {
        val title = if (nextBreakBig) {
            preferences.preBreakNotificationBigTitle
        } else {
            preferences.preBreakNotificationSmallTitle
        }.ifBlank {
            if (nextBreakBig) DEFAULT_PRE_BREAK_BIG_TITLE else DEFAULT_PRE_BREAK_SMALL_TITLE
        }

        val contentPrefix = if (nextBreakBig) {
            preferences.preBreakNotificationBigContent
        } else {
            preferences.preBreakNotificationSmallContent
        }.ifBlank {
            if (nextBreakBig) DEFAULT_PRE_BREAK_BIG_CONTENT else DEFAULT_PRE_BREAK_SMALL_CONTENT
        }
        val content = "$contentPrefix (${formatSeconds(secondsToNextBreak)})"

        return NotificationCompat.Builder(this, PRE_BREAK_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    REQUEST_CODE_PRE_BREAK_CONTENT,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
    }

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

        val preBreakChannel = NotificationChannel(
            PRE_BREAK_CHANNEL_ID,
            getString(R.string.notification_channel_pre_break_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(preBreakChannel)
    }

    companion object {
        const val CHANNEL_ID = "dream_break_reminder"
        const val PRE_BREAK_CHANNEL_ID = "dream_break_pre_break"
        private const val NOTIFICATION_ID = 1124
        private const val PRE_BREAK_NOTIFICATION_ID = 1125
        private const val REQUEST_CODE_POSTPONE = 881
        private const val REQUEST_CODE_PRE_BREAK_CONTENT = 882
        private const val DEFAULT_NORMAL_MODE_UPDATE_SECONDS = 10

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
