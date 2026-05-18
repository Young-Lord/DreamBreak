package moe.lyniko.dreambreak.core

import moe.lyniko.dreambreak.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRestoreTest {
    @Test
    fun `first load without persisted state uses preferences smallEvery`() {
        val current = BreakUiState()
        val settings = AppSettings(
            preferences = BreakPreferences(smallEvery = 5),
            persistedSecondsToNextBreak = -1,
        )

        val restored = settings.applyToUiState(current, isFirstLoad = true)

        assertEquals(5, restored.state.secondsToNextBreak)
    }

    @Test
    fun `first load with persisted state restores countdown`() {
        val current = BreakUiState()
        val settings = AppSettings(
            preferences = BreakPreferences(smallEvery = 5),
            persistedSecondsToNextBreak = 123,
            persistedBreakCycleCount = 42,
            persistedCompletedSmallBreaks = 10,
            persistedCompletedBigBreaks = 3,
            persistedBreakStateTimestampEpochMs = System.currentTimeMillis(),
        )

        val restored = settings.applyToUiState(current, isFirstLoad = true)

        assertEquals(123, restored.state.secondsToNextBreak)
        assertEquals(42, restored.state.breakCycleCount)
        assertEquals(10, restored.state.completedSmallBreaks)
        assertEquals(3, restored.state.completedBigBreaks)
    }

    @Test
    fun `first load with stale persisted state adjusts for elapsed time`() {
        val current = BreakUiState()
        val staleTimestamp = System.currentTimeMillis() - 10_000
        val settings = AppSettings(
            preferences = BreakPreferences(smallEvery = 300),
            persistedSecondsToNextBreak = 200,
            persistedBreakCycleCount = 1,
            persistedBreakStateTimestampEpochMs = staleTimestamp,
        )

        val restored = settings.applyToUiState(current, isFirstLoad = true)

        // 10 seconds elapsed; 200 - 10 = 190
        assertEquals(190, restored.state.secondsToNextBreak)
        assertEquals(1, restored.state.breakCycleCount)
    }

    @Test
    fun `first load with very stale persisted state clamps to zero`() {
        val current = BreakUiState()
        val staleTimestamp = System.currentTimeMillis() - 3600_000
        val settings = AppSettings(
            preferences = BreakPreferences(smallEvery = 300),
            persistedSecondsToNextBreak = 30,
            persistedBreakStateTimestampEpochMs = staleTimestamp,
        )

        val restored = settings.applyToUiState(current, isFirstLoad = true)

        // 3600 seconds elapsed; 30 - 3600 < 0 → clamp to 0
        assertEquals(0, restored.state.secondsToNextBreak)
    }

    @Test
    fun `subsequent load keeps current runtime countdown`() {
        val current = BreakUiState(
            state = BreakState(
                mode = SessionMode.NORMAL,
                phase = null,
                secondsToNextBreak = 888,
                secondsSinceLastBreak = 10,
                secondsPaused = 0,
                breakCycleCount = 7,
                isBigBreak = false,
                promptSecondsElapsed = 0,
                breakSecondsRemaining = 0,
                pauseReasons = emptySet(),
                modeBeforePause = null,
                completedSmallBreaks = 5,
                completedBigBreaks = 2,
            ),
        )
        val settings = AppSettings(
            preferences = BreakPreferences(smallEvery = 5),
            persistedSecondsToNextBreak = 999,
        )

        val restored = settings.applyToUiState(current, isFirstLoad = false)

        assertEquals(888, restored.state.secondsToNextBreak)
        assertEquals(7, restored.state.breakCycleCount)
    }
}

