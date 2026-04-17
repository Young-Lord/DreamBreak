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
import moe.lyniko.dreambreak.data.AppListMode
import moe.lyniko.dreambreak.data.AppSettings
import moe.lyniko.dreambreak.data.AppThemeMode
import moe.lyniko.dreambreak.data.QsTileClickAction

data class BreakUiState(
    val preferences: BreakPreferences = BreakPreferences(),
    val state: BreakState = BreakState.initial(BreakPreferences()),
    val pauseInListedApps: Boolean = false,
    val appListMode: AppListMode = AppListMode.WHITELIST,
    val monitoredApps: String = "",
    val monitoredAppsBlacklist: String = "",
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
    val hasAddedQsTile: Boolean = false,
    val qsTileCountdownAsTitle: Boolean = false,
    val qsTileClickAction: QsTileClickAction = QsTileClickAction.TOGGLE_ENABLED,
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
        appListMode = appListMode,
        monitoredApps = monitoredApps,
        monitoredAppsBlacklist = monitoredAppsBlacklist,
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
        hasAddedQsTile = hasAddedQsTile,
        qsTileCountdownAsTitle = qsTileCountdownAsTitle,
        qsTileClickAction = qsTileClickAction,
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
            // Keep live countdown (including postpone). Only ensure it's non-negative.
            secondsToNextBreak = current.state.secondsToNextBreak.coerceAtLeast(0)
        )
    )
}

fun BreakUiState.toAppSettings(): AppSettings = AppSettings(
    preferences = preferences,
    pauseInListedApps = pauseInListedApps,
    appListMode = appListMode,
    monitoredApps = monitoredApps,
    monitoredAppsBlacklist = monitoredAppsBlacklist,
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
    hasAddedQsTile = hasAddedQsTile,
    qsTileCountdownAsTitle = qsTileCountdownAsTitle,
    qsTileClickAction = qsTileClickAction,
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
    private val stateLock = Any()

    private val _uiState = MutableStateFlow(BreakUiState())
    val uiState: StateFlow<BreakUiState> = _uiState.asStateFlow()

    private inline fun updateUiState(
        transform: (BreakUiState) -> BreakUiState,
    ): BreakUiState {
        return synchronized(stateLock) {
            val nextState = transform(_uiState.value)
            _uiState.value = nextState
            nextState
        }
    }

    fun start() {
        if (tickerJob?.isActive == true) {
            return
        }

        tickerJob = scope.launch {
            while (isActive) {
                delay(1000)
                updateUiState { current ->
                    if (!current.appEnabled) {
                        return@updateUiState current
                    }
                    val nextState = BreakEngine.tick(current.state, current.preferences)
                    current.copy(state = nextState)
                }
            }
        }
    }

    fun updatePreferences(preferences: BreakPreferences) {
        updateUiState { current ->
            current.copy(
            preferences = preferences,
            state = current.state.copy(
                // Do not clamp to smallEvery; postpone can legitimately extend the countdown.
                secondsToNextBreak = current.state.secondsToNextBreak.coerceAtLeast(0)
            )
        )
        }
    }

    fun setPauseInListedApps(enabled: Boolean) {
        updateUiState { current -> current.copy(pauseInListedApps = enabled) }
    }

    fun setAppPauseActive(active: Boolean) {
        updateUiState { current ->
            val nextState = BreakEngine.setPauseReason(
                state = current.state,
                reason = PauseReason.APP_OPEN,
                active = active,
                preferences = current.preferences,
            )
            current.copy(state = nextState)
        }
    }

    fun setMonitoredApps(value: String) {
        updateUiState { current -> current.copy(monitoredApps = value) }
    }

    fun setAppListMode(mode: AppListMode) {
        updateUiState { current -> current.copy(appListMode = mode) }
    }

    fun setMonitoredAppsBlacklist(value: String) {
        updateUiState { current -> current.copy(monitoredAppsBlacklist = value) }
    }

    fun setAppEnabled(enabled: Boolean): Boolean {
        val updated = updateUiState { current ->
            if (enabled && !current.isBreakCycleEnableUnlocked()) {
                return@updateUiState current
            }

            if (enabled) {
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
        return updated.appEnabled
    }

    fun markSpecificAppsPageVisited() {
        updateUiState { current ->
            if (current.hasVisitedSpecificAppsPage) {
                return@updateUiState current
            }
            current.copy(hasVisitedSpecificAppsPage = true)
        }
    }

    fun markPauseInListedAppsEnabledOnce() {
        updateUiState { current ->
            if (current.hasEnabledPauseInListedAppsOnce) {
                return@updateUiState current
            }
            current.copy(hasEnabledPauseInListedAppsOnce = true)
        }
    }

    fun markExternalPauseAppAddedOnce() {
        updateUiState { current ->
            if (current.hasAddedExternalPauseAppOnce) {
                return@updateUiState current
            }
            current.copy(hasAddedExternalPauseAppOnce = true)
        }
    }

    fun setOverlayTransparencyPercent(value: Int) {
        updateUiState { current ->
            current.copy(
                overlayTransparencyPercent = value.coerceIn(
                    OVERLAY_TRANSPARENCY_MIN,
                    OVERLAY_TRANSPARENCY_MAX,
                )
            )
        }
    }

    fun setOverlayBackgroundPortraitUri(value: String) {
        updateUiState { current -> current.copy(overlayBackgroundPortraitUri = value) }
    }

    fun setOverlayBackgroundLandscapeUri(value: String) {
        updateUiState { current -> current.copy(overlayBackgroundLandscapeUri = value) }
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        updateUiState { current -> current.copy(autoStartOnBoot = enabled) }
    }

    fun setRestoreEnabledStateOnStart(enabled: Boolean) {
        updateUiState { current -> current.copy(restoreEnabledStateOnStart = enabled) }
    }

    fun setReenableOnScreenUnlock(enabled: Boolean) {
        updateUiState { current -> current.copy(reenableOnScreenUnlock = enabled) }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        updateUiState { current -> current.copy(onboardingCompleted = completed) }
    }

    fun setExcludeFromRecents(exclude: Boolean) {
        updateUiState { current -> current.copy(excludeFromRecents = exclude) }
    }

    fun setPersistentNotificationEnabled(enabled: Boolean) {
        updateUiState { current -> current.copy(persistentNotificationEnabled = enabled) }
    }

    fun setPersistentNotificationUpdateFrequencySeconds(value: Int) {
        updateUiState { current ->
            current.copy(
                persistentNotificationUpdateFrequencySeconds = value.coerceIn(
                    NOTIFICATION_FREQUENCY_MIN,
                    NOTIFICATION_FREQUENCY_MAX,
                )
            )
        }
    }

    fun setPersistentNotificationTitleTemplate(value: String) {
        updateUiState { current -> current.copy(persistentNotificationTitleTemplate = value) }
    }

    fun setPersistentNotificationContentTemplate(value: String) {
        updateUiState { current -> current.copy(persistentNotificationContentTemplate = value) }
    }

    fun setQsTileCountdownAsTitle(enabled: Boolean) {
        updateUiState { current -> current.copy(qsTileCountdownAsTitle = enabled) }
    }

    fun setQsTileClickAction(action: QsTileClickAction) {
        updateUiState { current -> current.copy(qsTileClickAction = action) }
    }

    fun setHasAddedQsTile(added: Boolean) {
        updateUiState { current -> current.copy(hasAddedQsTile = added) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        updateUiState { current -> current.copy(themeMode = mode) }
    }

    fun setBreakShowPostponeButton(enabled: Boolean) {
        updateUiState { current -> current.copy(breakShowPostponeButton = enabled) }
    }

    fun setBreakExitPostponeSeconds(seconds: Int) {
        updateUiState { current ->
            current.copy(
                breakExitPostponeSeconds = seconds.coerceIn(
                    BREAK_EXIT_POSTPONE_MIN,
                    BREAK_EXIT_POSTPONE_MAX,
                )
            )
        }
    }

    fun setBreakOverlayFadeInDurationMs(durationMs: Int) {
        updateUiState { current ->
            current.copy(
                breakOverlayFadeInDurationMs = durationMs.coerceIn(
                    OVERLAY_ANIMATION_DURATION_MIN,
                    OVERLAY_ANIMATION_DURATION_MAX,
                )
            )
        }
    }

    fun setBreakOverlayFadeOutDurationMs(durationMs: Int) {
        updateUiState { current ->
            current.copy(
                breakOverlayFadeOutDurationMs = durationMs.coerceIn(
                    OVERLAY_ANIMATION_DURATION_MIN,
                    OVERLAY_ANIMATION_DURATION_MAX,
                )
            )
        }
    }

    fun setBreakShowTitle(enabled: Boolean) {
        updateUiState { current -> current.copy(breakShowTitle = enabled) }
    }

    fun setBreakShowCountdown(enabled: Boolean) {
        updateUiState { current -> current.copy(breakShowCountdown = enabled) }
    }

    fun setBreakShowExitButton(enabled: Boolean) {
        updateUiState { current -> current.copy(breakShowExitButton = enabled) }
    }

    fun requestBreakNow(bigBreak: Boolean = false) {
        updateUiState { current ->
            if (!current.appEnabled) {
                return@updateUiState current
            }
            current.copy(
                state = BreakEngine.requestBreakNow(current.state, current.preferences, bigBreak)
            )
        }
    }

    fun postponeBreak() {
        updateUiState { current ->
            if (!current.appEnabled) {
                return@updateUiState current
            }
            current.copy(
                state = BreakEngine.postponeBreak(current.state, current.preferences)
            )
        }
    }

    fun postponeBreakForSeconds(seconds: Int) {
        updateUiState { current ->
            if (!current.appEnabled) {
                return@updateUiState current
            }
            current.copy(
                state = BreakEngine.postponeBreakForSeconds(current.state, seconds)
            )
        }
    }

    fun interruptBreak() {
        updateUiState { current ->
            if (!current.appEnabled) {
                return@updateUiState current
            }
            current.copy(
                state = BreakEngine.interruptBreak(current.state)
            )
        }
    }

    fun exitPostBreak() {
        updateUiState { current ->
            current.copy(
                state = BreakEngine.exitPostBreak(current.state, current.preferences)
            )
        }
    }

    fun setScreenLocked(locked: Boolean) {
        updateUiState { current ->
            if (locked) {
                val stateAfterCompletingBreak = BreakEngine.completeBreakSession(
                    state = current.state,
                    preferences = current.preferences,
                )

                current.copy(
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
                if (shouldReenable && canReenable) {
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
    }

    fun restoreSettings(settings: AppSettings, isFirstLoad: Boolean = false) {
        updateUiState { current ->
            settings.applyToUiState(current, isFirstLoad = isFirstLoad)
        }
    }

}
