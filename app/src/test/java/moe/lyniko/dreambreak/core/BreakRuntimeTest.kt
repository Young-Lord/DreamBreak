package moe.lyniko.dreambreak.core

import moe.lyniko.dreambreak.data.AppSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Pins the "first load is exactly once per process" contract in [BreakRuntime].
 *
 * Regression: opening the app (fresh MainActivity) used to re-trigger a first load because the
 * Activity passed isFirstLoad=true, which reset the live countdown to smallEvery — even while
 * the process was still alive (e.g. after the QS tile had started the countdown).
 */
class BreakRuntimeTest {

    @After
    fun tearDown() {
        BreakRuntime.resetInitialSettingsFlagForTesting()
    }

    @Test
    fun `first restoreSettings acts as first load and initializes countdown`() {
        BreakRuntime.restoreSettings(AppSettings(preferences = BreakPreferences(smallEvery = 60)))

        assertEquals(60, BreakRuntime.uiState.value.state.secondsToNextBreak)
    }

    @Test
    fun `subsequent restoreSettings preserves the live countdown`() {
        BreakRuntime.restoreSettings(AppSettings(preferences = BreakPreferences(smallEvery = 60)))
        BreakRuntime.setSecondsToNextBreakForTesting(42)

        BreakRuntime.restoreSettings(AppSettings(preferences = BreakPreferences(smallEvery = 60)))

        assertEquals(42, BreakRuntime.uiState.value.state.secondsToNextBreak)
    }

    @Test
    fun `subsequent restoreSettings preserves a live appEnabled toggle`() {
        BreakRuntime.restoreSettings(AppSettings(preferences = BreakPreferences(smallEvery = 60)))
        BreakRuntime.setAppEnabled(false)

        BreakRuntime.restoreSettings(AppSettings(preferences = BreakPreferences(smallEvery = 60)))

        assertFalse(BreakRuntime.uiState.value.appEnabled)
    }

    @Test
    fun `subsequent restoreSettings keeps cycle counts that changed at runtime`() {
        BreakRuntime.restoreSettings(
            AppSettings(
                preferences = BreakPreferences(smallEvery = 60),
                persistedBreakCycleCount = 1,
            )
        )
        BreakRuntime.setSecondsToNextBreakForTesting(10)

        BreakRuntime.restoreSettings(
            AppSettings(
                preferences = BreakPreferences(smallEvery = 60),
                persistedBreakCycleCount = 99,
            )
        )

        assertEquals(10, BreakRuntime.uiState.value.state.secondsToNextBreak)
    }
}
