package moe.lyniko.dreambreak.core

import kotlin.math.max

enum class SessionMode {
    NORMAL,
    PAUSED,
    BREAK,
}

enum class BreakPhase {
    PROMPT,
    FULL_SCREEN,
    POST,
}

enum class PauseReason {
    IDLE,
    ON_BATTERY,
    APP_OPEN,
    SLEEP,
}

data class BreakPreferences(
    val smallEvery: Int = 20 * 60,
    val smallFor: Int = 20,
    val bigAfter: Int = 3,
    val bigFor: Int = 60,
    val flashFor: Int = 8,
    val postponeFor: Int = 5 * 60,
    val resetIntervalAfterPause: Int = 20 * 60,
    val resetCycleAfterPause: Int = 40 * 60,
)

data class BreakState(
    val mode: SessionMode = SessionMode.NORMAL,
    val phase: BreakPhase? = null,
    val secondsToNextBreak: Int,
    val secondsSinceLastBreak: Int = 0,
    val secondsPaused: Int = 0,
    val breakCycleCount: Int = 0,
    val isBigBreak: Boolean = false,
    val promptSecondsElapsed: Int = 0,
    val breakSecondsRemaining: Int = 0,
    val pauseReasons: Set<PauseReason> = emptySet(),
    val modeBeforePause: SessionMode? = null,
) {
    companion object {
        fun initial(preferences: BreakPreferences): BreakState {
            return BreakState(secondsToNextBreak = preferences.smallEvery)
        }
    }
}

object BreakEngine {
    fun tick(state: BreakState, preferences: BreakPreferences): BreakState {
        return when (state.mode) {
            SessionMode.NORMAL -> tickNormal(state, preferences)
            SessionMode.PAUSED -> state.copy(secondsPaused = state.secondsPaused + 1)
            SessionMode.BREAK -> tickBreak(state, preferences)
        }
    }

    fun setPauseReason(
        state: BreakState,
        reason: PauseReason,
        active: Boolean,
        preferences: BreakPreferences,
    ): BreakState {
        val updatedReasons = state.pauseReasons.toMutableSet().apply {
            if (active) {
                add(reason)
            } else {
                remove(reason)
            }
        }

        if (updatedReasons.isNotEmpty()) {
            return if (state.mode == SessionMode.PAUSED) {
                state.copy(pauseReasons = updatedReasons)
            } else {
                val shouldPause = state.mode != SessionMode.BREAK || updatedReasons.any { it != PauseReason.IDLE }
                if (!shouldPause) {
                    state.copy(pauseReasons = updatedReasons)
                } else {
                    state.copy(
                        mode = SessionMode.PAUSED,
                        modeBeforePause = state.mode,
                        pauseReasons = updatedReasons,
                        secondsPaused = 0,
                    )
                }
            }
        }

        if (state.mode != SessionMode.PAUSED) {
            return state.copy(pauseReasons = emptySet())
        }

        val resetInterval = state.secondsPaused >= preferences.resetIntervalAfterPause
        val resetCycle = state.secondsPaused >= preferences.resetCycleAfterPause

        val resumedMode = state.modeBeforePause ?: SessionMode.NORMAL
        var resumedState = state.copy(
            mode = resumedMode,
            modeBeforePause = null,
            pauseReasons = emptySet(),
            secondsPaused = 0,
        )

        if (resetInterval && resumedMode == SessionMode.NORMAL) {
            resumedState = resumedState.copy(secondsToNextBreak = preferences.smallEvery)
        }

        if (resetCycle && resumedMode == SessionMode.NORMAL) {
            resumedState = resumedState.copy(breakCycleCount = 0)
        }

        return resumedState
    }

    fun requestBreakNow(state: BreakState, preferences: BreakPreferences, bigBreak: Boolean = false): BreakState {
        return startPrompt(state, preferences, forceBigBreak = bigBreak)
    }

    fun postponeBreak(state: BreakState, preferences: BreakPreferences): BreakState {
        return postponeBreakForSeconds(state, preferences.postponeFor)
    }

    fun postponeBreakForSeconds(state: BreakState, seconds: Int): BreakState {
        val safeSeconds = max(seconds, 1)
        if (state.mode != SessionMode.BREAK) {
            return state.copy(secondsToNextBreak = max(state.secondsToNextBreak, safeSeconds))
        }

        return state.copy(
            mode = SessionMode.NORMAL,
            phase = null,
            isBigBreak = false,
            promptSecondsElapsed = 0,
            breakSecondsRemaining = 0,
            secondsToNextBreak = safeSeconds,
        )
    }

    fun interruptBreak(state: BreakState): BreakState {
        if (state.mode != SessionMode.BREAK) {
            return state
        }

        return state.copy(
            phase = BreakPhase.PROMPT,
            promptSecondsElapsed = 0,
            breakSecondsRemaining = 0,
            secondsToNextBreak = 0,
        )
    }

    fun exitPostBreak(state: BreakState, preferences: BreakPreferences): BreakState {
        if (state.mode != SessionMode.BREAK || state.phase != BreakPhase.POST) {
            return state
        }

        return finishBreakAndStartNextCycle(state, preferences)
    }

    private fun tickNormal(state: BreakState, preferences: BreakPreferences): BreakState {
        val next = state.copy(
            secondsToNextBreak = state.secondsToNextBreak - 1,
            secondsSinceLastBreak = state.secondsSinceLastBreak + 1,
        )

        return if (next.secondsToNextBreak <= 0) {
            startPrompt(next, preferences)
        } else {
            next
        }
    }

    private fun tickBreak(state: BreakState, preferences: BreakPreferences): BreakState {
        return when (state.phase) {
            BreakPhase.PROMPT -> {
                val elapsed = state.promptSecondsElapsed + 1
                val idleInPauseReasons = state.pauseReasons.contains(PauseReason.IDLE)
                if (elapsed >= preferences.flashFor || idleInPauseReasons) {
                    state.copy(
                        phase = BreakPhase.FULL_SCREEN,
                        promptSecondsElapsed = elapsed,
                        breakSecondsRemaining = if (state.isBigBreak) preferences.bigFor else preferences.smallFor,
                    )
                } else {
                    state.copy(promptSecondsElapsed = elapsed)
                }
            }

            BreakPhase.FULL_SCREEN -> {
                val remaining = state.breakSecondsRemaining - 1
                if (remaining <= 0) {
                    finishBreakAndStartNextCycle(
                        state.copy(breakSecondsRemaining = 0),
                        preferences,
                    )
                } else {
                    state.copy(breakSecondsRemaining = remaining)
                }
            }

            BreakPhase.POST -> finishBreakAndStartNextCycle(state, preferences)
            null -> state
        }
    }

    private fun startPrompt(
        state: BreakState,
        preferences: BreakPreferences,
        forceBigBreak: Boolean = false,
    ): BreakState {
        val currentCycle = state.breakCycleCount + 1
        val shouldUseBigBreak = forceBigBreak || (preferences.bigAfter > 0 && currentCycle % preferences.bigAfter == 0)
        return state.copy(
            mode = SessionMode.BREAK,
            phase = BreakPhase.PROMPT,
            isBigBreak = shouldUseBigBreak,
            promptSecondsElapsed = 0,
            breakSecondsRemaining = 0,
            breakCycleCount = currentCycle,
            secondsToNextBreak = 0,
        )
    }

    private fun finishBreakAndStartNextCycle(state: BreakState, preferences: BreakPreferences): BreakState {
        return state.copy(
            mode = SessionMode.NORMAL,
            phase = null,
            secondsToNextBreak = preferences.smallEvery,
            secondsSinceLastBreak = 0,
            isBigBreak = false,
            promptSecondsElapsed = 0,
            breakSecondsRemaining = 0,
        )
    }
}
