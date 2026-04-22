package moe.lyniko.dreambreak.core

import moe.lyniko.dreambreak.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRestoreTest {
    @Test
    fun `first load resets default countdown from preferences`() {
        val current = BreakUiState()
        val settings = AppSettings(
            preferences = BreakPreferences(smallEvery = 5),
        )

        val restored = settings.applyToUiState(current, isFirstLoad = true)

        assertEquals(5, restored.state.secondsToNextBreak)
    }

    @Test
    fun `first load keeps non-pristine countdown`() {
        val current = BreakUiState(
            preferences = BreakPreferences(smallEvery = 20 * 60),
            state = BreakState(
                mode = SessionMode.NORMAL,
                phase = null,
                secondsToNextBreak = 123,
                secondsSinceLastBreak = 10,
                secondsPaused = 0,
                breakCycleCount = 0,
                isBigBreak = false,
                promptSecondsElapsed = 0,
                breakSecondsRemaining = 0,
                pauseReasons = emptySet(),
                modeBeforePause = null,
                completedSmallBreaks = 0,
                completedBigBreaks = 0,
            ),
        )
        val settings = AppSettings(
            preferences = BreakPreferences(smallEvery = 5),
        )

        val restored = settings.applyToUiState(current, isFirstLoad = true)

        assertEquals(123, restored.state.secondsToNextBreak)
    }
}

