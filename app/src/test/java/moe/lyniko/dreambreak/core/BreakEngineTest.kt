package moe.lyniko.dreambreak.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakEngineTest {
    private val preferences = BreakPreferences(
        smallEvery = 5,
        smallFor = 3,
        bigAfter = 2,
        bigFor = 6,
        flashFor = 2,
        postponeFor = 4,
        resetIntervalAfterPause = 3,
        resetCycleAfterPause = 5,
    )

    @Test
    fun `countdown enters prompt break`() {
        var state = BreakState.initial(preferences)

        repeat(5) {
            state = BreakEngine.tick(state, preferences)
        }

        assertEquals(SessionMode.BREAK, state.mode)
        assertEquals(BreakPhase.PROMPT, state.phase)
        assertTrue(!state.isBigBreak)
    }

    @Test
    fun `prompt transitions to fullscreen after flash window`() {
        var state = BreakState.initial(preferences)

        repeat(5) {
            state = BreakEngine.tick(state, preferences)
        }
        repeat(2) {
            state = BreakEngine.tick(state, preferences)
        }

        assertEquals(BreakPhase.FULL_SCREEN, state.phase)
        assertEquals(preferences.smallFor, state.breakSecondsRemaining)
    }

    @Test
    fun `idle pause reason forces fullscreen during prompt`() {
        var state = BreakState.initial(preferences)
        repeat(5) {
            state = BreakEngine.tick(state, preferences)
        }

        state = BreakEngine.setPauseReason(state, PauseReason.IDLE, active = true, preferences = preferences)
        state = BreakEngine.tick(state, preferences)

        assertEquals(SessionMode.BREAK, state.mode)
        assertEquals(BreakPhase.FULL_SCREEN, state.phase)
    }

    @Test
    fun `small break fullscreen auto exits to next cycle`() {
        var state = BreakEngine.requestBreakNow(BreakState.initial(preferences), preferences)
        repeat(2) {
            state = BreakEngine.tick(state, preferences)
        }
        repeat(3) {
            state = BreakEngine.tick(state, preferences)
        }

        assertEquals(SessionMode.NORMAL, state.mode)
        assertEquals(preferences.smallEvery, state.secondsToNextBreak)
        assertEquals(0, state.secondsSinceLastBreak)
    }

    @Test
    fun `big break fullscreen auto exits to next cycle`() {
        var state = BreakEngine.requestBreakNow(BreakState.initial(preferences), preferences, bigBreak = true)
        repeat(2) {
            state = BreakEngine.tick(state, preferences)
        }
        repeat(6) {
            state = BreakEngine.tick(state, preferences)
        }

        assertEquals(SessionMode.NORMAL, state.mode)
        assertEquals(preferences.smallEvery, state.secondsToNextBreak)
        assertEquals(0, state.secondsSinceLastBreak)
    }

    @Test
    fun `interrupt break returns to prompt stage`() {
        var state = BreakEngine.requestBreakNow(BreakState.initial(preferences), preferences)
        repeat(2) {
            state = BreakEngine.tick(state, preferences)
        }

        state = BreakEngine.interruptBreak(state)

        assertEquals(SessionMode.BREAK, state.mode)
        assertEquals(BreakPhase.PROMPT, state.phase)
        assertEquals(0, state.promptSecondsElapsed)
        assertEquals(0, state.breakSecondsRemaining)
    }

    @Test
    fun `postpone with custom duration exits current break`() {
        var state = BreakEngine.requestBreakNow(BreakState.initial(preferences), preferences)
        repeat(2) {
            state = BreakEngine.tick(state, preferences)
        }

        state = BreakEngine.postponeBreakForSeconds(state, 900)

        assertEquals(SessionMode.NORMAL, state.mode)
        assertEquals(900, state.secondsToNextBreak)
    }

    @Test
    fun `resuming from long pause resets interval`() {
        var state = BreakState.initial(preferences)
        state = BreakEngine.setPauseReason(state, PauseReason.APP_OPEN, active = true, preferences = preferences)

        repeat(3) {
            state = BreakEngine.tick(state, preferences)
        }

        state = BreakEngine.setPauseReason(state, PauseReason.APP_OPEN, active = false, preferences = preferences)
        assertEquals(SessionMode.NORMAL, state.mode)
        assertEquals(preferences.smallEvery, state.secondsToNextBreak)
    }
}
