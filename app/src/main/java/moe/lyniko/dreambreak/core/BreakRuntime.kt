package moe.lyniko.dreambreak.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.lyniko.dreambreak.data.AppThemeMode

data class BreakUiState(
    val preferences: BreakPreferences = BreakPreferences(),
    val state: BreakState = BreakState.initial(BreakPreferences()),
    val pauseInListedApps: Boolean = false,
    val monitoredApps: String = "",
    val autoStartOnBoot: Boolean = false,
    val appEnabled: Boolean = true,
    val overlayTransparencyPercent: Int = 28,
    val overlayBackgroundUri: String = "",
    val onboardingCompleted: Boolean = false,
    val excludeFromRecents: Boolean = false,
    val persistentNotificationEnabled: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
)

object BreakRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null

    private val _uiState = MutableStateFlow(BreakUiState())
    val uiState: StateFlow<BreakUiState> = _uiState.asStateFlow()

    fun start() {
        if (tickerJob?.isActive == true) {
            return
        }

        tickerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val current = _uiState.value
                if (!current.appEnabled) {
                    continue
                }
                _uiState.value = current.copy(
                    state = BreakEngine.tick(current.state, current.preferences)
                )
            }
        }
    }

    fun updatePreferences(preferences: BreakPreferences) {
        val current = _uiState.value
        _uiState.value = current.copy(
            preferences = preferences,
            state = current.state.copy(
                secondsToNextBreak = current.state.secondsToNextBreak.coerceAtMost(preferences.smallEvery)
            )
        )
    }

    fun setPauseInListedApps(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(pauseInListedApps = enabled)
    }

    fun setAppPauseActive(active: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(
            state = BreakEngine.setPauseReason(
                state = current.state,
                reason = PauseReason.APP_OPEN,
                active = active,
                preferences = current.preferences,
            )
        )
    }

    fun setMonitoredApps(value: String) {
        val current = _uiState.value
        _uiState.value = current.copy(monitoredApps = value)
    }

    fun setAppEnabled(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = if (enabled) {
            current.copy(
                appEnabled = true,
                state = if (current.state.secondsToNextBreak <= 0) {
                    current.state.copy(secondsToNextBreak = current.preferences.smallEvery)
                } else {
                    current.state
                }
            )
        } else {
            current.copy(
                appEnabled = false,
                state = current.state.copy(
                    mode = SessionMode.NORMAL,
                    phase = null,
                    isBigBreak = false,
                    promptSecondsElapsed = 0,
                    breakSecondsRemaining = 0,
                    pauseReasons = emptySet(),
                    modeBeforePause = null,
                    secondsToNextBreak = current.state.secondsToNextBreak.coerceAtLeast(current.preferences.smallEvery),
                )
            )
        }
    }

    fun setOverlayTransparencyPercent(value: Int) {
        val current = _uiState.value
        _uiState.value = current.copy(overlayTransparencyPercent = value.coerceIn(0, 100))
    }

    fun setOverlayBackgroundUri(value: String) {
        val current = _uiState.value
        _uiState.value = current.copy(overlayBackgroundUri = value)
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(autoStartOnBoot = enabled)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(onboardingCompleted = completed)
    }

    fun setExcludeFromRecents(exclude: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(excludeFromRecents = exclude)
    }

    fun setPersistentNotificationEnabled(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(persistentNotificationEnabled = enabled)
    }

    fun setThemeMode(mode: AppThemeMode) {
        val current = _uiState.value
        _uiState.value = current.copy(themeMode = mode)
    }

    fun requestBreakNow(bigBreak: Boolean = false) {
        val current = _uiState.value
        if (!current.appEnabled) {
            return
        }
        _uiState.value = current.copy(
            state = BreakEngine.requestBreakNow(current.state, current.preferences, bigBreak)
        )
    }

    fun postponeBreak() {
        val current = _uiState.value
        if (!current.appEnabled) {
            return
        }
        _uiState.value = current.copy(
            state = BreakEngine.postponeBreak(current.state, current.preferences)
        )
    }

    fun postponeBreakForSeconds(seconds: Int) {
        val current = _uiState.value
        if (!current.appEnabled) {
            return
        }
        _uiState.value = current.copy(
            state = BreakEngine.postponeBreakForSeconds(current.state, seconds)
        )
    }

    fun interruptBreak() {
        val current = _uiState.value
        if (!current.appEnabled) {
            return
        }
        _uiState.value = current.copy(
            state = BreakEngine.interruptBreak(current.state)
        )
    }

    fun exitPostBreak() {
        val current = _uiState.value
        _uiState.value = current.copy(
            state = BreakEngine.exitPostBreak(current.state, current.preferences)
        )
    }

    fun setScreenLocked(locked: Boolean) {
        val current = _uiState.value
        if (locked) {
            // Screen off: pause the timer with SLEEP reason
            _uiState.value = current.copy(
                state = BreakEngine.setPauseReason(
                    state = current.state,
                    reason = PauseReason.SLEEP,
                    active = true,
                    preferences = current.preferences,
                )
            )
        } else {
            // Screen unlocked: unpause and always reset countdown to full interval
            val unpaused = BreakEngine.setPauseReason(
                state = current.state,
                reason = PauseReason.SLEEP,
                active = false,
                preferences = current.preferences,
            )
            _uiState.value = current.copy(
                state = unpaused.copy(
                    secondsToNextBreak = current.preferences.smallEvery,
                )
            )
        }
    }

    fun restoreSettings(
        preferences: BreakPreferences,
        pauseInListedApps: Boolean,
        monitoredApps: String,
        autoStartOnBoot: Boolean,
        appEnabled: Boolean,
        overlayTransparencyPercent: Int,
        overlayBackgroundUri: String,
        onboardingCompleted: Boolean,
        excludeFromRecents: Boolean,
        persistentNotificationEnabled: Boolean,
        themeMode: AppThemeMode,
    ) {
        val current = _uiState.value
        _uiState.value = current.copy(
            preferences = preferences,
            pauseInListedApps = pauseInListedApps,
            monitoredApps = monitoredApps,
            autoStartOnBoot = autoStartOnBoot,
            appEnabled = appEnabled,
            overlayTransparencyPercent = overlayTransparencyPercent.coerceIn(0, 100),
            overlayBackgroundUri = overlayBackgroundUri,
            onboardingCompleted = onboardingCompleted,
            excludeFromRecents = excludeFromRecents,
            persistentNotificationEnabled = persistentNotificationEnabled,
            themeMode = themeMode,
            state = current.state.copy(
                secondsToNextBreak = current.state.secondsToNextBreak.coerceAtMost(preferences.smallEvery)
            )
        )
    }
}
