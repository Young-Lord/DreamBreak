package moe.lyniko.dreambreak.core

import moe.lyniko.dreambreak.data.AppSettings
import moe.lyniko.dreambreak.data.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRestoreTest {
    @Test
    fun `first load initializes countdown to preferences smallEvery`() {
        val current = BreakUiState()
        val settings = AppSettings(
            preferences = BreakPreferences(smallEvery = 5),
        )

        val restored = settings.applyToUiState(current, isFirstLoad = true)

        assertEquals(5, restored.state.secondsToNextBreak)
    }

    @Test
    fun `first load restores cycle counts from disk`() {
        val current = BreakUiState()
        val settings = AppSettings(
            preferences = BreakPreferences(smallEvery = 5),
            persistedBreakCycleCount = 42,
            persistedCompletedSmallBreaks = 10,
            persistedCompletedBigBreaks = 3,
        )

        val restored = settings.applyToUiState(current, isFirstLoad = true)

        assertEquals(5, restored.state.secondsToNextBreak)
        assertEquals(42, restored.state.breakCycleCount)
        assertEquals(10, restored.state.completedSmallBreaks)
        assertEquals(3, restored.state.completedBigBreaks)
    }

    @Test
    fun `subsequent load keeps runtime countdown and cycle counts`() {
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
            persistedBreakCycleCount = 999,
            persistedCompletedSmallBreaks = 111,
            persistedCompletedBigBreaks = 222,
        )

        val restored = settings.applyToUiState(current, isFirstLoad = false)

        assertEquals(888, restored.state.secondsToNextBreak)
        assertEquals(7, restored.state.breakCycleCount)
        assertEquals(5, restored.state.completedSmallBreaks)
        assertEquals(2, restored.state.completedBigBreaks)
    }

    @Test
    fun `first load decides appEnabled from unlock flags when restoreEnabledStateOnStart is off`() {
        val current = BreakUiState()
        val settings = AppSettings(
            restoreEnabledStateOnStart = false,
            appEnabled = false,
            hasVisitedSpecificAppsPage = true,
            hasEnabledPauseInListedAppsOnce = true,
            hasAddedExternalPauseAppOnce = true,
        )

        val restored = settings.applyToUiState(current, isFirstLoad = true)

        assertTrue(restored.appEnabled)
    }

    @Test
    fun `subsequent load preserves live appEnabled`() {
        val current = BreakUiState(
            appEnabled = false,
        )
        val settings = AppSettings(
            restoreEnabledStateOnStart = false,
            appEnabled = true,
            hasVisitedSpecificAppsPage = true,
            hasEnabledPauseInListedAppsOnce = true,
            hasAddedExternalPauseAppOnce = true,
        )

        val restored = settings.applyToUiState(current, isFirstLoad = false)

        assertFalse(restored.appEnabled)
    }

    @Test
    fun `config fields always apply on subsequent loads`() {
        val current = BreakUiState()
        val settings = AppSettings(
            preferences = BreakPreferences(smallEvery = 5),
            pauseInListedApps = true,
            themeMode = AppThemeMode.DARK,
        )

        val restored = settings.applyToUiState(current, isFirstLoad = false)

        assertEquals(BreakPreferences(smallEvery = 5), restored.preferences)
        assertTrue(restored.pauseInListedApps)
        assertEquals(AppThemeMode.DARK, restored.themeMode)
    }
}
