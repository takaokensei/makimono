package zechs.drive.stream.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleFormattingPolicyTest {

    @Test
    fun `preventOrphans ties last short word with non-breaking space`() {
        val input = "Spider-Man: Brand New Day"
        val output = TitleFormattingPolicy.preventOrphans(input)
        assertEquals("Spider-Man: Brand New\u00A0Day", output)
    }

    @Test
    fun `preventOrphans leaves single word untouched`() {
        val input = "BLEACH"
        val output = TitleFormattingPolicy.preventOrphans(input)
        assertEquals("BLEACH", output)
    }

    @Test
    fun `computeDisplayLetterSpacing returns tighter tracking for large sizes`() {
        val spacing40 = TitleFormattingPolicy.computeDisplayLetterSpacing(40f)
        val spacing32 = TitleFormattingPolicy.computeDisplayLetterSpacing(32f)
        val spacing20 = TitleFormattingPolicy.computeDisplayLetterSpacing(20f)
        val spacing14 = TitleFormattingPolicy.computeDisplayLetterSpacing(14f)

        assertTrue(spacing40 < spacing32)
        assertTrue(spacing32 < spacing20)
        assertTrue(spacing20 < 0f)
        assertEquals(0f, spacing14, 0.0001f)
    }

    @Test
    fun `resolveHeroTitleSizeSp adapts size dynamically to title length on TV`() {
        val shortTitle = "BLEACH"
        val mediumTitle = "Spider-Man: Brand New Day"
        val longTitle = "Mushoku Tensei: Isekai Ittara Honki Dasu Season 2"

        val sizeShort = TitleFormattingPolicy.resolveHeroTitleSizeSp(shortTitle, isTv = true)
        val sizeMedium = TitleFormattingPolicy.resolveHeroTitleSizeSp(mediumTitle, isTv = true)
        val sizeLong = TitleFormattingPolicy.resolveHeroTitleSizeSp(longTitle, isTv = true)

        assertEquals(40f, sizeShort, 0.01f)
        assertEquals(32f, sizeMedium, 0.01f)
        assertEquals(26f, sizeLong, 0.01f)
    }
}
