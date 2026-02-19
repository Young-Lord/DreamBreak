package moe.lyniko.dreambreak.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PostponeDurationsTest {
    @Test
    fun `normalize strips spaces converts comma sorts and deduplicates`() {
        val normalized = normalizePostponeDurationInput(" 300，60,  60,900 ")
        assertEquals("60,300,900", normalized)
    }

    @Test
    fun `parse uses fallback when input is empty`() {
        val parsed = parsePostponeDurations("", fallback = listOf(180, 120, 120))
        assertEquals(listOf(120, 180), parsed)
    }

    @Test
    fun `parse or empty returns empty for invalid input`() {
        val parsed = parsePostponeDurationsOrEmpty("，,0,  ")
        assertEquals(emptyList<Int>(), parsed)
    }

    @Test
    fun `format uses comma with trailing space`() {
        val formatted = formatPostponeDurations(listOf(600, 60, 300, 300))
        assertEquals("60, 300, 600", formatted)
    }
}
