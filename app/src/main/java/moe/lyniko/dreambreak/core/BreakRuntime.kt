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
import moe.lyniko.dreambreak.data.AppSettings
import moe.lyniko.dreambreak.data.AppThemeMode

data class BreakUiState(
    val preferences: BreakPreferences = BreakPreferences(),
    val state: BreakState = BreakState.initial(BreakPreferences()),
    val pauseInListedApps: Boolean = false,
    val monitoredApps: String = "",
    val autoStartOnBoot: Boolean = false,
    val appEnabled: Boolean = true,
    val overlayTransparencyPercent: Int = 28,
    val overlayBackgroundPortraitUri: String = "",
    val overlayBackgroundLandscapeUri: String = "",
    val onboardingCompleted: Boolean = false,
    val excludeFromRecents: Boolean = false,
    val persistentNotificationEnabled: Boolean = false,
    val persistentNotificationUpdateFrequencySeconds: Int = 10,
    val persistentNotificationTitleTemplate: String = DEFAULT_PERSISTENT_NOTIFICATION_TITLE_TEMPLATE,
    val persistentNotificationContentTemplate: String = DEFAULT_PERSISTENT_NOTIFICATION_CONTENT_TEMPLATE,
    val qsTileCountdownAsTitle: Boolean = false,
    val breakShowPostponeButton: Boolean = true,
    val breakShowTitle: Boolean = true,
    val breakShowCountdown: Boolean = true,
    val breakShowExitButton: Boolean = true,
    val breakExitPostponeSeconds: Int = DEFAULT_POSTPONE_DURATION_SECONDS,
    val breakOverlayFadeInDurationMs: Int = 300,
    val breakOverlayFadeOutDurationMs: Int = 300,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val hasVisitedSpecificAppsPage: Boolean = false,
    val hasEnabledPauseInListedAppsOnce: Boolean = false,
    val hasAddedExternalPauseAppOnce: Boolean = false,
    val restoreEnabledStateOnStart: Boolean = false,
    val reenableOnScreenUnlock: Boolean = false,
)

private fun BreakUiState.isBreakCycleEnableUnlocked(): Boolean {
    return hasVisitedSpecificAppsPage &&
        hasEnabledPauseInListedAppsOnce &&
        hasAddedExternalPauseAppOnce
}

fun AppSettings.applyToUiState(current: BreakUiState, isFirstLoad: Boolean = false): BreakUiState {
    val isBreakCycleEnableUnlocked =
        hasVisitedSpecificAppsPage && hasEnabledPauseInListedAppsOnce && hasAddedExternalPauseAppOnce
    // When restoreEnabledStateOnStart is disabled, only the first load decides initial enable state.
    // Afterwards keep runtime appEnabled to avoid disk synchronization races overriding live state.
    val effectiveAppEnabled = when {
        isFirstLoad && !restoreEnabledStateOnStart -> isBreakCycleEnableUnlocked
        !restoreEnabledStateOnStart -> current.appEnabled && isBreakCycleEnableUnlocked
        else -> appEnabled && isBreakCycleEnableUnlocked
    }
    return current.copy(
        preferences = preferences,
        pauseInListedApps = pauseInListedApps,
        monitoredApps = monitoredApps,
        autoStartOnBoot = autoStartOnBoot,
        appEnabled = effectiveAppEnabled,
        overlayTransparencyPercent = overlayTransparencyPercent.coerceIn(OVERLAY_TRANSPARENCY_MIN, OVERLAY_TRANSPARENCY_MAX),
        overlayBackgroundPortraitUri = overlayBackgroundPortraitUri,
        overlayBackgroundLandscapeUri = overlayBackgroundLandscapeUri,
        onboardingCompleted = onboardingCompleted,
        excludeFromRecents = excludeFromRecents,
        persistentNotificationEnabled = persistentNotificationEnabled,
        persistentNotificationUpdateFrequencySeconds =
            persistentNotificationUpdateFrequencySeconds.coerceIn(NOTIFICATION_FREQUENCY_MIN, NOTIFICATION_FREQUENCY_MAX),
        persistentNotificationTitleTemplate = persistentNotificationTitleTemplate,
        persistentNotificationContentTemplate = persistentNotificationContentTemplate,
        qsTileCountdownAsTitle = qsTileCountdownAsTitle,
        breakShowPostponeButton = breakShowPostponeButton,
        breakShowTitle = breakShowTitle,
        breakShowCountdown = breakShowCountdown,
        breakShowExitButton = breakShowExitButton,
        breakExitPostponeSeconds = breakExitPostponeSeconds.coerceIn(BREAK_EXIT_POSTPONE_MIN, BREAK_EXIT_POSTPONE_MAX),
        breakOverlayFadeInDurationMs = breakOverlayFadeInDurationMs.coerceIn(OVERLAY_ANIMATION_DURATION_MIN, OVERLAY_ANIMATION_DURATION_MAX),
        breakOverlayFadeOutDurationMs = breakOverlayFadeOutDurationMs.coerceIn(OVERLAY_ANIMATION_DURATION_MIN, OVERLAY_ANIMATION_DURATION_MAX),
        themeMode = themeMode,
        hasVisitedSpecificAppsPage = hasVisitedSpecificAppsPage,
        hasEnabledPauseInListedAppsOnce = hasEnabledPauseInListedAppsOnce,
        hasAddedExternalPauseAppOnce = hasAddedExternalPauseAppOnce,
        restoreEnabledStateOnStart = restoreEnabledStateOnStart,
        reenableOnScreenUnlock = reenableOnScreenUnlock,
        state = current.state.copy(
            secondsToNextBreak = current.state.secondsToNextBreak.coerceAtMost(preferences.smallEvery)
        )
    )
}

fun BreakUiState.toAppSettings(): AppSettings = AppSettings(
    preferences = preferences,
    pauseInListedApps = pauseInListedApps,
    monitoredApps = monitoredApps,
    autoStartOnBoot = autoStartOnBoot,
    appEnabled = appEnabled,
    overlayTransparencyPercent = overlayTransparencyPercent,
    overlayBackgroundPortraitUri = overlayBackgroundPortraitUri,
    overlayBackgroundLandscapeUri = overlayBackgroundLandscapeUri,
    onboardingCompleted = onboardingCompleted,
    excludeFromRecents = excludeFromRecents,
    persistentNotificationEnabled = persistentNotificationEnabled,
    persistentNotificationUpdateFrequencySeconds = persistentNotificationUpdateFrequencySeconds,
    persistentNotificationTitleTemplate = persistentNotificationTitleTemplate,
    persistentNotificationContentTemplate = persistentNotificationContentTemplate,
    qsTileCountdownAsTitle = qsTileCountdownAsTitle,
    breakShowPostponeButton = breakShowPostponeButton,
    breakShowTitle = breakShowTitle,
    breakShowCountdown = breakShowCountdown,
    breakShowExitButton = breakShowExitButton,
    breakExitPostponeSeconds = breakExitPostponeSeconds,
    breakOverlayFadeInDurationMs = breakOverlayFadeInDurationMs,
    breakOverlayFadeOutDurationMs = breakOverlayFadeOutDurationMs,
    themeMode = themeMode,
    hasVisitedSpecificAppsPage = hasVisitedSpecificAppsPage,
    hasEnabledPauseInListedAppsOnce = hasEnabledPauseInListedAppsOnce,
    hasAddedExternalPauseAppOnce = hasAddedExternalPauseAppOnce,
    restoreEnabledStateOnStart = restoreEnabledStateOnStart,
    reenableOnScreenUnlock = reenableOnScreenUnlock,
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
                val nextState = BreakEngine.tick(current.state, current.preferences)
                _uiState.value = current.copy(state = nextState)
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
        val nextState = BreakEngine.setPauseReason(
            state = current.state,
            reason = PauseReason.APP_OPEN,
            active = active,
            preferences = current.preferences,
        )
        _uiState.value = current.copy(state = nextState)
    }

    fun setMonitoredApps(value: String) {
        val current = _uiState.value
        _uiState.value = current.copy(monitoredApps = value)
    }

    fun setAppEnabled(enabled: Boolean): Boolean {
        val current = _uiState.value
        if (enabled && !current.isBreakCycleEnableUnlocked()) {
            return current.appEnabled
        }

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
        return _uiState.value.appEnabled
    }

    fun markSpecificAppsPageVisited() {
        val current = _uiState.value
        if (current.hasVisitedSpecificAppsPage) {
            return
        }
        _uiState.value = current.copy(hasVisitedSpecificAppsPage = true)
    }

    fun markPauseInListedAppsEnabledOnce() {
        val current = _uiState.value
        if (current.hasEnabledPauseInListedAppsOnce) {
            return
        }
        _uiState.value = current.copy(hasEnabledPauseInListedAppsOnce = true)
    }

    fun markExternalPauseAppAddedOnce() {
        val current = _uiState.value
        if (current.hasAddedExternalPauseAppOnce) {
            return
        }
        _uiState.value = current.copy(hasAddedExternalPauseAppOnce = true)
    }

    fun setOverlayTransparencyPercent(value: Int) {
        val current = _uiState.value
        _uiState.value = current.copy(overlayTransparencyPercent = value.coerceIn(OVERLAY_TRANSPARENCY_MIN, OVERLAY_TRANSPARENCY_MAX))
    }

    fun setOverlayBackgroundPortraitUri(value: String) {
        val current = _uiState.value
        _uiState.value = current.copy(overlayBackgroundPortraitUri = value)
    }

    fun setOverlayBackgroundLandscapeUri(value: String) {
        val current = _uiState.value
        _uiState.value = current.copy(overlayBackgroundLandscapeUri = value)
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(autoStartOnBoot = enabled)
    }

    fun setRestoreEnabledStateOnStart(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(restoreEnabledStateOnStart = enabled)
    }

    fun setReenableOnScreenUnlock(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(reenableOnScreenUnlock = enabled)
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

    fun setPersistentNotificationUpdateFrequencySeconds(value: Int) {
        val current = _uiState.value
        _uiState.value = current.copy(
            persistentNotificationUpdateFrequencySeconds = value.coerceIn(NOTIFICATION_FREQUENCY_MIN, NOTIFICATION_FREQUENCY_MAX)
        )
    }

    fun setPersistentNotificationTitleTemplate(value: String) {
        val current = _uiState.value
        _uiState.value = current.copy(persistentNotificationTitleTemplate = value)
    }

    fun setPersistentNotificationContentTemplate(value: String) {
        val current = _uiState.value
        _uiState.value = current.copy(persistentNotificationContentTemplate = value)
    }

    fun setQsTileCountdownAsTitle(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(qsTileCountdownAsTitle = enabled)
    }

    fun setThemeMode(mode: AppThemeMode) {
        val current = _uiState.value
        _uiState.value = current.copy(themeMode = mode)
    }

    fun setBreakShowPostponeButton(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(breakShowPostponeButton = enabled)
    }

    fun setBreakExitPostponeSeconds(seconds: Int) {
        val current = _uiState.value
        _uiState.value = current.copy(breakExitPostponeSeconds = seconds.coerceIn(BREAK_EXIT_POSTPONE_MIN, BREAK_EXIT_POSTPONE_MAX))
    }

    fun setBreakOverlayFadeInDurationMs(durationMs: Int) {
        val current = _uiState.value
        _uiState.value = current.copy(breakOverlayFadeInDurationMs = durationMs.coerceIn(OVERLAY_ANIMATION_DURATION_MIN, OVERLAY_ANIMATION_DURATION_MAX))
    }

    fun setBreakOverlayFadeOutDurationMs(durationMs: Int) {
        val current = _uiState.value
        _uiState.value = current.copy(breakOverlayFadeOutDurationMs = durationMs.coerceIn(OVERLAY_ANIMATION_DURATION_MIN, OVERLAY_ANIMATION_DURATION_MAX))
    }

    fun setBreakShowTitle(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(breakShowTitle = enabled)
    }

    fun setBreakShowCountdown(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(breakShowCountdown = enabled)
    }

    fun setBreakShowExitButton(enabled: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(breakShowExitButton = enabled)
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
            val stateAfterCompletingBreak = BreakEngine.completeBreakSession(
                state = current.state,
                preferences = current.preferences,
            )

            _uiState.value = current.copy(
                state = BreakEngine.setPauseReason(
                    state = stateAfterCompletingBreak,
                    reason = PauseReason.SLEEP,
                    active = true,
                    preferences = current.preferences,
                )
            )
        } else {
            // If reenableOnScreenUnlock is true and app was disabled, re-enable it
            val shouldReenable = current.reenableOnScreenUnlock && !current.appEnabled
            val stateAfterUnlock = BreakEngine.setPauseReason(
                state = current.state,
                reason = PauseReason.SLEEP,
                active = false,
                preferences = current.preferences,
            )
            val canReenable = current.isBreakCycleEnableUnlocked()
            _uiState.value = if (shouldReenable && canReenable) {
                current.copy(
                    appEnabled = true,
                    state = if (stateAfterUnlock.secondsToNextBreak <= 0) {
                        stateAfterUnlock.copy(secondsToNextBreak = current.preferences.smallEvery)
                    } else {
                        stateAfterUnlock
                    }
                )
            } else {
                current.copy(state = stateAfterUnlock)
            }
        }
    }

    fun restoreSettings(settings: AppSettings, isFirstLoad: Boolean = false) {
        val current = _uiState.value
        val restored = settings.applyToUiState(current, isFirstLoad = isFirstLoad)
        _uiState.value = restored
    }

}
