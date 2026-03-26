package moe.lyniko.dreambreak.tile

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.lyniko.dreambreak.MainActivity
import moe.lyniko.dreambreak.R
import moe.lyniko.dreambreak.core.BreakPhase
import moe.lyniko.dreambreak.core.BreakPreferences
import moe.lyniko.dreambreak.core.BreakRuntime
import moe.lyniko.dreambreak.core.BreakState
import moe.lyniko.dreambreak.core.SessionMode
import moe.lyniko.dreambreak.data.AppSettings
import moe.lyniko.dreambreak.data.SettingsStore
import moe.lyniko.dreambreak.monitor.AppPauseMonitor
import moe.lyniko.dreambreak.notification.BreakReminderService

class DreamBreakTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Main.immediate)
    private val settingsStore by lazy { SettingsStore(applicationContext) }

    private var initializationJob: Job? = null
    private var settingsObserverJob: Job? = null
    private var uiObserverJob: Job? = null
    private var tileRefreshJob: Job? = null

    override fun onTileAdded() {
        super.onTileAdded()
        scope.launch {
            val settings = settingsStore.settingsFlow.first()
            applySettingsToRuntime(settings)
            val uiState = BreakRuntime.uiState.value
            updateTile(
                appEnabled = uiState.appEnabled,
                state = uiState.state,
                preferences = uiState.preferences,
                qsTileCountdownAsTitle = uiState.qsTileCountdownAsTitle,
            )
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        startListeningJobs()
    }

    override fun onStopListening() {
        cancelListeningJobs()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val appContext = applicationContext
            val currentSettings = settingsStore.settingsFlow.first()
            val nextEnabled = !currentSettings.appEnabled

            BreakRuntime.setAppEnabled(nextEnabled)
            settingsStore.save(currentSettings.copy(appEnabled = nextEnabled))

            if (nextEnabled) {
                BreakRuntime.start()
                AppPauseMonitor.start(appContext)
                if (
                    currentSettings.persistentNotificationEnabled &&
                    MainActivity.hasNotificationPermission(appContext)
                ) {
                    BreakReminderService.start(appContext)
                }
            } else {
                BreakReminderService.stop(appContext)
            }

            val uiState = BreakRuntime.uiState.value
            updateTile(
                appEnabled = uiState.appEnabled,
                state = uiState.state,
                preferences = uiState.preferences,
                qsTileCountdownAsTitle = uiState.qsTileCountdownAsTitle,
            )
        }
    }

    override fun onDestroy() {
        cancelListeningJobs()
        scope.cancel()
        super.onDestroy()
    }

    private fun startListeningJobs() {
        initializationJob?.cancel()
        initializationJob = scope.launch {
            val settings = settingsStore.settingsFlow.first()
            applySettingsToRuntime(settings)
            BreakRuntime.start()
            val uiState = BreakRuntime.uiState.value
            updateTile(
                appEnabled = uiState.appEnabled,
                state = uiState.state,
                preferences = uiState.preferences,
                qsTileCountdownAsTitle = uiState.qsTileCountdownAsTitle,
            )
            observeSettings()
            observeUiState()
            startTileRefreshJob()
        }
    }

    private fun cancelListeningJobs() {
        initializationJob?.cancel()
        initializationJob = null
        settingsObserverJob?.cancel()
        settingsObserverJob = null
        uiObserverJob?.cancel()
        uiObserverJob = null
        tileRefreshJob?.cancel()
        tileRefreshJob = null
    }

    private fun observeSettings() {
        if (settingsObserverJob?.isActive == true) {
            return
        }

        settingsObserverJob = scope.launch {
            settingsStore.settingsFlow.collectLatest { settings ->
                applySettingsToRuntime(settings)
            }
        }
    }

    private fun observeUiState() {
        if (uiObserverJob?.isActive == true) {
            return
        }

        uiObserverJob = scope.launch {
            BreakRuntime.uiState.collectLatest { uiState ->
                updateTile(
                    appEnabled = uiState.appEnabled,
                    state = uiState.state,
                    preferences = uiState.preferences,
                    qsTileCountdownAsTitle = uiState.qsTileCountdownAsTitle,
                )
            }
        }
    }

    private fun startTileRefreshJob() {
        if (tileRefreshJob?.isActive == true) {
            return
        }

        tileRefreshJob = scope.launch {
            while (isActive) {
                val uiState = BreakRuntime.uiState.value
                updateTile(
                    appEnabled = uiState.appEnabled,
                    state = uiState.state,
                    preferences = uiState.preferences,
                    qsTileCountdownAsTitle = uiState.qsTileCountdownAsTitle,
                )
                delay(1000)
            }
        }
    }

    private fun applySettingsToRuntime(settings: AppSettings) {
        BreakRuntime.restoreSettings(
            preferences = settings.preferences,
            pauseInListedApps = settings.pauseInListedApps,
            monitoredApps = settings.monitoredApps,
            autoStartOnBoot = settings.autoStartOnBoot,
            appEnabled = settings.appEnabled,
            overlayTransparencyPercent = settings.overlayTransparencyPercent,
            overlayBackgroundPortraitUri = settings.overlayBackgroundPortraitUri,
            overlayBackgroundLandscapeUri = settings.overlayBackgroundLandscapeUri,
            onboardingCompleted = settings.onboardingCompleted,
            excludeFromRecents = settings.excludeFromRecents,
            persistentNotificationEnabled = settings.persistentNotificationEnabled,
            persistentNotificationUpdateFrequencySeconds =
                settings.persistentNotificationUpdateFrequencySeconds,
            persistentNotificationTitleTemplate = settings.persistentNotificationTitleTemplate,
            persistentNotificationContentTemplate = settings.persistentNotificationContentTemplate,
            qsTileCountdownAsTitle = settings.qsTileCountdownAsTitle,
            breakShowPostponeButton = settings.breakShowPostponeButton,
            breakShowTitle = settings.breakShowTitle,
            breakShowCountdown = settings.breakShowCountdown,
            breakShowExitButton = settings.breakShowExitButton,
            breakExitPostponeSeconds = settings.breakExitPostponeSeconds,
            breakOverlayFadeInDurationMs = settings.breakOverlayFadeInDurationMs,
            breakOverlayFadeOutDurationMs = settings.breakOverlayFadeOutDurationMs,
            themeMode = settings.themeMode,
        )
    }

    private fun updateTile(
        appEnabled: Boolean,
        state: BreakState,
        preferences: BreakPreferences,
        qsTileCountdownAsTitle: Boolean,
    ) {
        val tile = qsTile ?: return
        val label = getString(R.string.qs_tile_label)
        val countdown = if (appEnabled) {
            val seconds = secondsUntilNextBreak(state, preferences)
            // show raw MM:SS countdown without any prefix
            formatMinutesSeconds(seconds)
        } else {
            null
        }

        tile.label = if (appEnabled && qsTileCountdownAsTitle) {
            countdown ?: label
        } else {
            label
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_bed)
        tile.state = if (appEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (appEnabled && !qsTileCountdownAsTitle) countdown else null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // stateDescription should contain only the raw countdown when enabled
            tile.stateDescription = countdown
        }
        // contentDescription: expose only the raw countdown when enabled (no "Next break in" prefix)
        tile.contentDescription = if (appEnabled && countdown != null) {
            countdown
        } else {
            label
        }
        tile.updateTile()
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

    private fun formatMinutesSeconds(rawSeconds: Int): String {
        val safe = rawSeconds.coerceAtLeast(0)
        val minutePart = safe / 60
        val secondPart = safe % 60
        return "%02d:%02d".format(minutePart, secondPart)
    }
}
