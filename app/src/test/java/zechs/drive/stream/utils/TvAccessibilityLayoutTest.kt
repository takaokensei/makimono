package zechs.drive.stream.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.R

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
    fun `profile art catalog keeps avatar options round and wallpapers panoramic`() {
        assertEquals(5, TvAccessibilityLayout.profileCatalogColumns(isWallpaperView = false, isLandscape = true))
        assertEquals(4, TvAccessibilityLayout.profileCatalogColumns(isWallpaperView = false, isLandscape = false))
        assertEquals(2, TvAccessibilityLayout.profileCatalogColumns(isWallpaperView = true, isLandscape = true))
        assertEquals(2, TvAccessibilityLayout.profileCatalogColumns(isWallpaperView = true, isLandscape = false))
    }

    @Test
    fun `files navigation and search gain room only when text is enlarged`() {
        assertEquals(128, TvAccessibilityLayout.filesRailWidthDp(1f))
        assertEquals(168, TvAccessibilityLayout.filesRailWidthDp(1.3f))
        assertEquals(224, TvAccessibilityLayout.filesRailWidthDp(2f))
        assertEquals(300, TvAccessibilityLayout.filesSearchWidthDp(1f))
        assertEquals(360, TvAccessibilityLayout.filesSearchWidthDp(1.3f))
        assertEquals(420, TvAccessibilityLayout.filesSearchWidthDp(2f))
        assertEquals(R.string.search_files_hint, TvAccessibilityLayout.filesSearchHintResId(1.3f, hasNavigationRail = true))
        assertEquals(R.string.search_files_hint_large, TvAccessibilityLayout.filesSearchHintResId(2f, hasNavigationRail = true))
        assertEquals(R.string.search_files_hint, TvAccessibilityLayout.filesSearchHintResId(2f, hasNavigationRail = false))
    }

    @Test
    fun `details synopsis gets more reading space at enlarged text sizes`() {
        assertEquals(3, TvAccessibilityLayout.detailsSynopsisMaxLines(1f))
        assertEquals(5, TvAccessibilityLayout.detailsSynopsisMaxLines(1.3f))
        assertEquals(8, TvAccessibilityLayout.detailsSynopsisMaxLines(2f))
    }

    @Test
    fun `home hero compacts on TV and wide screens only`() {
        assertEquals(false, TvAccessibilityLayout.usesCompactHomeHero(414, 393))
        assertEquals(true, TvAccessibilityLayout.usesCompactHomeHero(960, 540))
        assertEquals(true, TvAccessibilityLayout.usesCompactHomeHero(800, 600))
    }
}
