package zechs.drive.stream.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TvAccessibilityLayoutTest {
    @Test
    fun `grid remains unchanged at standard and moderate text sizes`() {
        assertEquals(4, TvAccessibilityLayout.gridColumnsForFontScale(4, 1f))
        assertEquals(3, TvAccessibilityLayout.gridColumnsForFontScale(4, 1.3f))
    }

    @Test
    fun `grid reduces columns for very large text without reaching zero`() {
        assertEquals(2, TvAccessibilityLayout.gridColumnsForFontScale(4, 2f))
        assertEquals(1, TvAccessibilityLayout.gridColumnsForFontScale(2, 2f))
    }
}
