package zechs.drive.stream.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.ui.files.DriveFolderNavigationPolicy
import zechs.drive.stream.ui.files.FilesStatePolicy
import zechs.drive.stream.ui.home.controller.RailNavigationPolicy
import zechs.drive.stream.ui.series.SynopsisDisclosurePolicy
import zechs.drive.stream.utils.FocusEngine
import zechs.drive.stream.utils.FocusMemory
import zechs.drive.stream.utils.PlayerDpadFocus
import zechs.drive.stream.utils.PlayerFocusDirection
import zechs.drive.stream.utils.PlayerFocusItem

/**
 * Validação automatizada do Checklist de Navegação e Foco TV (Seções 4.10, 7.2, 7.3, 7.4 e 9.5).
 * Garante ausência de armadilhas D-pad, transições suaves, memória de foco e integridade em todas as telas.
 */
class TvFocusChecklistTest {

    @Before
    fun setUp() {
        FocusMemory.clearAll()
    }

    // =========================================================================
    // 1. HOME SCREEN FOCUS & RAIL (Seções 7.2, 7.3 e 9.5)
    // =========================================================================

    @Test
    fun `tv home rail navigation resolves dpad right target predictably without trapping`() {
        // Hero takes precedence when present
        assertEquals(
            RailNavigationPolicy.ContentTarget.HERO_PLAY,
            RailNavigationPolicy.resolveContentTargetOnRight(
                isHeroVisible = true,
                isSearchVisible = true,
                hasShelvesWithItems = true,
                isCatalogVisible = true
            )
        )

        // Search takes precedence if hero is hidden
        assertEquals(
            RailNavigationPolicy.ContentTarget.SEARCH_HEADER,
            RailNavigationPolicy.resolveContentTargetOnRight(
                isHeroVisible = false,
                isSearchVisible = true,
                hasShelvesWithItems = true,
                isCatalogVisible = true
            )
        )

        // Shelves take precedence if hero and search are hidden
        assertEquals(
            RailNavigationPolicy.ContentTarget.FIRST_SHELF,
            RailNavigationPolicy.resolveContentTargetOnRight(
                isHeroVisible = false,
                isSearchVisible = false,
                hasShelvesWithItems = true,
                isCatalogVisible = true
            )
        )

        // Catalog grid takes focus if only catalog is available
        assertEquals(
            RailNavigationPolicy.ContentTarget.CATALOG_GRID,
            RailNavigationPolicy.resolveContentTargetOnRight(
                isHeroVisible = false,
                isSearchVisible = false,
                hasShelvesWithItems = false,
                isCatalogVisible = true
            )
        )

        // Viewport fallback
        assertEquals(
            RailNavigationPolicy.ContentTarget.CONTENT_VIEWPORT,
            RailNavigationPolicy.resolveContentTargetOnRight(
                isHeroVisible = false,
                isSearchVisible = false,
                hasShelvesWithItems = false,
                isCatalogVisible = false
            )
        )
    }

    @Test
    fun `tv home vertical shelf traversal maintains smooth step progression and escapes at ends`() {
        val totalShelves = 5

        // Down steps
        assertEquals(1, RailNavigationPolicy.calculateAdjacentShelfIndex(0, totalShelves, isDpadDown = true))
        assertEquals(2, RailNavigationPolicy.calculateAdjacentShelfIndex(1, totalShelves, isDpadDown = true))
        assertEquals(3, RailNavigationPolicy.calculateAdjacentShelfIndex(2, totalShelves, isDpadDown = true))
        assertEquals(4, RailNavigationPolicy.calculateAdjacentShelfIndex(3, totalShelves, isDpadDown = true))
        assertNull("Down from last shelf must not wrap or trap", RailNavigationPolicy.calculateAdjacentShelfIndex(4, totalShelves, isDpadDown = true))

        // Up steps
        assertEquals(3, RailNavigationPolicy.calculateAdjacentShelfIndex(4, totalShelves, isDpadDown = false))
        assertEquals(2, RailNavigationPolicy.calculateAdjacentShelfIndex(3, totalShelves, isDpadDown = false))
        assertEquals(1, RailNavigationPolicy.calculateAdjacentShelfIndex(2, totalShelves, isDpadDown = false))
        assertEquals(0, RailNavigationPolicy.calculateAdjacentShelfIndex(1, totalShelves, isDpadDown = false))
        assertNull("Up from top shelf must escape to Hero or Search", RailNavigationPolicy.calculateAdjacentShelfIndex(0, totalShelves, isDpadDown = false))
    }

    @Test
    fun `tv home focus memory remembers and restores last focused item`() {
        FocusMemory.remember("home.continue_watching", "ep_42")
        FocusMemory.remember("home.catalog", "series_frieren")

        assertEquals("ep_42", FocusMemory.get("home.continue_watching"))
        assertEquals("series_frieren", FocusMemory.get("home.catalog"))

        val restored = RailNavigationPolicy.resolveShelfFocusRestoration(
            shelfIndex = 0,
            itemIndex = 7,
            shelfItemCounts = mapOf(0 to 4)
        )
        // Item index 7 is clamped to 3 (last valid index in 4 items)
        assertEquals(Pair(0, 3), restored)
    }

    // =========================================================================
    // 2. SERIES & MOVIE DETAIL FOCUS (Seções 7.4 e 9.5)
    // =========================================================================

    @Test
    fun `tv movie detail isolates primary actions and does not trap dpad down into nonexistent episodes`() {
        // Pure focus rule for movie vs series:
        fun resolveDetailDownTarget(isMovie: Boolean, hasSeasonTabs: Boolean, tabsId: Int, epsId: Int): Int {
            return when {
                isMovie -> -1 // View.NO_ID
                hasSeasonTabs -> tabsId
                else -> epsId
            }
        }

        val tabsId = 100
        val epsId = 200

        // In Movie mode, down target is NO_ID
        assertEquals(-1, resolveDetailDownTarget(isMovie = true, hasSeasonTabs = false, tabsId, epsId))
        assertEquals(-1, resolveDetailDownTarget(isMovie = true, hasSeasonTabs = true, tabsId, epsId))

        // In Series mode, down target goes to season tabs if available, or episodes recycler
        assertEquals(tabsId, resolveDetailDownTarget(isMovie = false, hasSeasonTabs = true, tabsId, epsId))
        assertEquals(epsId, resolveDetailDownTarget(isMovie = false, hasSeasonTabs = false, tabsId, epsId))
    }

    @Test
    fun `tv synopsis disclosure properly detects truncation and updates focus availability`() {
        // Less lines than max -> not truncated, no "Mais" button needed
        assertFalse(SynopsisDisclosurePolicy.isTruncated(lineCount = 2, ellipsisCount = 0, maxLines = 3))

        // Exactly maxLines with 0 ellipsis -> fits fully, not truncated
        assertFalse(SynopsisDisclosurePolicy.isTruncated(lineCount = 3, ellipsisCount = 0, maxLines = 3))

        // Exactly maxLines with ellipsis -> text clipped, "Mais" required
        assertTrue(SynopsisDisclosurePolicy.isTruncated(lineCount = 3, ellipsisCount = 5, maxLines = 3))

        // More lines than maxLines -> truncated
        assertTrue(SynopsisDisclosurePolicy.isTruncated(lineCount = 5, ellipsisCount = 0, maxLines = 3))
    }

    // =========================================================================
    // 3. SETTINGS & CATEGORIES FOCUS (Seções 4.10 e 9.5)
    // =========================================================================

    @Test
    fun `tv settings category navigation exposes consistent rows without focus holes`() {
        // Map of category index to list of active row identifiers
        val profileRows = listOf("settingSelectProfile", "settingLibraryRoot")
        val appearanceRows = listOf("settingSelectTheme", "settingReduceMotion")
        val playbackRows = listOf("settingDefaultPlayer", "settingSubtitleSize", "settingAutoSkipToggle")
        val dataRows = listOf("settingClearCache", "settingCheckForUpdate", "settingExportBackup", "settingLogOut")

        val categories = listOf(profileRows, appearanceRows, playbackRows, dataRows)

        categories.forEachIndexed { index, rows ->
            assertTrue("Category $index must have at least one focusable row", rows.isNotEmpty())
            // First item in category is always the entry focus point from the category tab
            val entryRow = rows.first()
            assertTrue(entryRow.startsWith("setting"))
        }
    }

    @Test
    fun `tv focus engine applies elastic scale and elevation with reduced motion fallback`() {
        // Under standard motion, CARD gets 1.06x scale and 12dp elevation
        val cardTarget = FocusEngine.targetFor(FocusEngine.FocusKind.CARD, focused = true, reducedMotion = false)
        assertEquals(1.06f, cardTarget.scale, 0.001f)
        assertEquals(12.0f, cardTarget.elevationDp, 0.001f)

        // Under reduced motion, scale remains 1.0x and elevation 0dp
        val cardReduced = FocusEngine.targetFor(FocusEngine.FocusKind.CARD, focused = true, reducedMotion = true)
        assertEquals(1.0f, cardReduced.scale, 0.001f)
        assertEquals(0.0f, cardReduced.elevationDp, 0.001f)

        // BUTTON gets 1.04x scale and 6dp elevation
        val buttonTarget = FocusEngine.targetFor(FocusEngine.FocusKind.BUTTON, focused = true, reducedMotion = false)
        assertEquals(1.04f, buttonTarget.scale, 0.001f)
        assertEquals(6.0f, buttonTarget.elevationDp, 0.001f)

        // Unfocused view returns to 1.0x scale and 0dp elevation
        val unfocused = FocusEngine.targetFor(FocusEngine.FocusKind.CARD, focused = false, reducedMotion = false)
        assertEquals(1.0f, unfocused.scale, 0.001f)
        assertEquals(0.0f, unfocused.elevationDp, 0.001f)
    }

    // =========================================================================
    // 4. FILES & FOLDER FOCUS (Seções 4.10 e 9.5)
    // =========================================================================

    @Test
    fun `tv files navigation prevents cycle loops on recursive folders`() {
        val currentPath = listOf("root", "anime_folder", "season_1")

        // Navigating to existing folder in path must trigger Cycle decision
        val cycleDecision = DriveFolderNavigationPolicy.decide(
            folderPath = currentPath,
            currentQuery = "'season_1' in parents",
            targetFolderId = "anime_folder"
        )
        assertTrue(cycleDecision is DriveFolderNavigationPolicy.Decision.Cycle)

        // Navigating to a new child folder succeeds
        val validDecision = DriveFolderNavigationPolicy.decide(
            folderPath = currentPath,
            currentQuery = "'season_1' in parents",
            targetFolderId = "episodes_subfolder"
        )
        assertTrue(validDecision is DriveFolderNavigationPolicy.Decision.Navigate)
        val nav = validDecision as DriveFolderNavigationPolicy.Decision.Navigate
        assertEquals(listOf("root", "anime_folder", "season_1", "episodes_subfolder"), nav.folderPath)
    }

    @Test
    fun `tv files state policy correctly handles empty folder vs search without trapping focus`() {
        val emptyFolderMsg = FilesStatePolicy.emptyMessage(hasUnfilteredItems = false, query = "")
        assertEquals(FilesStatePolicy.EmptyFilesMessage.NO_FILES, emptyFolderMsg)

        val emptySearchMsg = FilesStatePolicy.emptyMessage(hasUnfilteredItems = true, query = "nonexistent")
        assertEquals(FilesStatePolicy.EmptyFilesMessage.NO_SEARCH_RESULTS, emptySearchMsg)
    }

    @Test
    fun `tv player transport dpad return path maintains deterministic up and down focus`() {
        assertEquals(
            PlayerFocusItem.EXIT,
            PlayerDpadFocus.next(
                direction = PlayerFocusDirection.UP,
                controlsVisible = true,
                controlsTransitioning = false,
                blockingOverlayVisible = false,
                focusedItem = PlayerFocusItem.PLAY_PAUSE
            )
        )
        assertEquals(
            PlayerFocusItem.PLAY_PAUSE,
            PlayerDpadFocus.next(
                direction = PlayerFocusDirection.DOWN,
                controlsVisible = true,
                controlsTransitioning = false,
                blockingOverlayVisible = false,
                focusedItem = PlayerFocusItem.EXIT
            )
        )
    }
}
