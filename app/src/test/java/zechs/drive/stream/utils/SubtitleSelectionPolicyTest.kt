package zechs.drive.stream.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSelectionPolicyTest {

    @Test
    fun `portuguese language codes are detected`() {
        listOf("pt", "por", "pt-BR", "pob", "portuguese").forEach {
            assertTrue("lang=$it", SubtitleSelectionPolicy.isPortuguese(it, null))
        }
        assertFalse(SubtitleSelectionPolicy.isPortuguese("en", null))
    }

    @Test
    fun `japanese detection falls back to label text`() {
        assertTrue(SubtitleSelectionPolicy.isJapanese(null, "JPN Audio"))
        assertFalse(SubtitleSelectionPolicy.isJapanese(null, "English Commentary"))
    }

    @Test
    fun `drive subtitles outrank everything else`() {
        assertEquals(25, SubtitleSelectionPolicy.scorePortuguese("★ [Drive] Português"))
        assertTrue(SubtitleSelectionPolicy.scorePortuguese("PT-BR") >
                SubtitleSelectionPolicy.scorePortuguese("Português"))
        assertTrue(SubtitleSelectionPolicy.scorePortuguese("Português") >
                SubtitleSelectionPolicy.scorePortuguese("forced signs songs"))
    }
}
