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

    @Test
    fun `files navigation and search gain room only when text is enlarged`() {
        assertEquals(128, TvAccessibilityLayout.filesRailWidthDp(1f))
        assertEquals(168, TvAccessibilityLayout.filesRailWidthDp(1.3f))
        assertEquals(224, TvAccessibilityLayout.filesRailWidthDp(2f))
        assertEquals(300, TvAccessibilityLayout.filesSearchWidthDp(1f))
        assertEquals(360, TvAccessibilityLayout.filesSearchWidthDp(1.3f))
        assertEquals(420, TvAccessibilityLayout.filesSearchWidthDp(2f))
    }

    @Test
    fun `details synopsis gets more reading space at enlarged text sizes`() {
        assertEquals(3, TvAccessibilityLayout.detailsSynopsisMaxLines(1f))
        assertEquals(5, TvAccessibilityLayout.detailsSynopsisMaxLines(1.3f))
        assertEquals(8, TvAccessibilityLayout.detailsSynopsisMaxLines(2f))
    }
}
