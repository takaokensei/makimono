package zechs.drive.stream.ui.home.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.ui.home.controller.RailNavigationPolicy.ContentTarget
import zechs.drive.stream.ui.home.controller.RailNavigationPolicy.RailTab

class RailNavigationFocusTest {

    @Test
    fun `ten foot TV experience always keeps rail permanent without overlay sidebar`() {
        val isOverlay = RailNavigationPolicy.isOverlaySidebarActive(
            isTenFootExperience = true,
            hasDimOverlay = true,
            isCollapsibleRailConfig = true
        )
        assertFalse(isOverlay)
    }

    @Test
    fun `mobile or tablet uses overlay sidebar only when configured and overlay view is present`() {
        val activeWithDim = RailNavigationPolicy.isOverlaySidebarActive(
            isTenFootExperience = false,
            hasDimOverlay = true,
            isCollapsibleRailConfig = true
        )
        assertTrue(activeWithDim)

        val inactiveWithoutDim = RailNavigationPolicy.isOverlaySidebarActive(
            isTenFootExperience = false,
            hasDimOverlay = false,
            isCollapsibleRailConfig = true
        )
        assertFalse(inactiveWithoutDim)

        val inactiveConfig = RailNavigationPolicy.isOverlaySidebarActive(
            isTenFootExperience = false,
            hasDimOverlay = true,
            isCollapsibleRailConfig = false
        )
        assertFalse(inactiveConfig)
    }

    @Test
    fun `dpad right navigation resolves primary focus to Hero play button when hero is present`() {
        val target = RailNavigationPolicy.resolveContentTargetOnRight(
            isHeroVisible = true,
            isSearchVisible = true,
            hasShelvesWithItems = true,
            isCatalogVisible = true
        )
        assertEquals(ContentTarget.HERO_PLAY, target)
    }

    @Test
    fun `dpad right navigation resolves to search bar when hero is hidden`() {
        val target = RailNavigationPolicy.resolveContentTargetOnRight(
            isHeroVisible = false,
            isSearchVisible = true,
            hasShelvesWithItems = true,
            isCatalogVisible = true
        )
        assertEquals(ContentTarget.SEARCH_HEADER, target)
    }

    @Test
    fun `dpad right navigation resolves to first shelf when hero and search are hidden`() {
        val target = RailNavigationPolicy.resolveContentTargetOnRight(
            isHeroVisible = false,
            isSearchVisible = false,
            hasShelvesWithItems = true,
            isCatalogVisible = true
        )
        assertEquals(ContentTarget.FIRST_SHELF, target)
    }

    @Test
    fun `dpad right navigation resolves to catalog grid when only catalog is shown`() {
        val target = RailNavigationPolicy.resolveContentTargetOnRight(
            isHeroVisible = false,
            isSearchVisible = false,
            hasShelvesWithItems = false,
            isCatalogVisible = true
        )
        assertEquals(ContentTarget.CATALOG_GRID, target)
    }

    @Test
    fun `dpad right navigation falls back to content viewport when everything is empty`() {
        val target = RailNavigationPolicy.resolveContentTargetOnRight(
            isHeroVisible = false,
            isSearchVisible = false,
            hasShelvesWithItems = false,
            isCatalogVisible = false
        )
        assertEquals(ContentTarget.CONTENT_VIEWPORT, target)
    }

    @Test
    fun `vertical dpad calculates next shelf correctly within boundaries`() {
        val totalShelves = 5

        // Down from shelf 0 -> shelf 1
        assertEquals(1, RailNavigationPolicy.calculateAdjacentShelfIndex(0, totalShelves, isDpadDown = true))
        // Down from shelf 3 -> shelf 4
        assertEquals(4, RailNavigationPolicy.calculateAdjacentShelfIndex(3, totalShelves, isDpadDown = true))
        // Down from last shelf 4 -> null (end of list, no trap)
        assertNull(RailNavigationPolicy.calculateAdjacentShelfIndex(4, totalShelves, isDpadDown = true))

        // Up from shelf 4 -> shelf 3
        assertEquals(3, RailNavigationPolicy.calculateAdjacentShelfIndex(4, totalShelves, isDpadDown = false))
        // Up from shelf 1 -> shelf 0
        assertEquals(0, RailNavigationPolicy.calculateAdjacentShelfIndex(1, totalShelves, isDpadDown = false))
        // Up from top shelf 0 -> null (escapes upward to Hero/Search)
        assertNull(RailNavigationPolicy.calculateAdjacentShelfIndex(0, totalShelves, isDpadDown = false))
    }

    @Test
    fun `invalid shelf index returns null`() {
        assertNull(RailNavigationPolicy.calculateAdjacentShelfIndex(-1, 5, isDpadDown = true))
        assertNull(RailNavigationPolicy.calculateAdjacentShelfIndex(10, 5, isDpadDown = false))
    }

    @Test
    fun `back press is consumed when sidebar is expanded and allowed to pass through when closed`() {
        assertTrue(RailNavigationPolicy.shouldConsumeBackPress(isSidebarExpanded = true))
        assertFalse(RailNavigationPolicy.shouldConsumeBackPress(isSidebarExpanded = false))
    }

    @Test
    fun `tab visibility preserves hero and shelves on Inicio and isolates catalog on category tabs`() {
        val inicio = RailNavigationPolicy.resolveTabVisibility(RailTab.INICIO, hasHeroData = true, hasShelvesData = true)
        assertTrue(inicio.isHeroVisible)
        assertTrue(inicio.areShelvesVisible)
        assertTrue(inicio.isCatalogVisible)
        assertFalse(inicio.isFilterToggleVisible)

        val animes = RailNavigationPolicy.resolveTabVisibility(RailTab.ANIMES, hasHeroData = true, hasShelvesData = true)
        assertFalse(animes.isHeroVisible)
        assertFalse(animes.areShelvesVisible)
        assertTrue(animes.isCatalogVisible)
        assertTrue(animes.isFilterToggleVisible)

        val pastas = RailNavigationPolicy.resolveTabVisibility(RailTab.PASTAS, hasHeroData = false, hasShelvesData = false)
        assertFalse(pastas.isHeroVisible)
        assertFalse(pastas.areShelvesVisible)
        assertFalse(pastas.isCatalogVisible)
        assertFalse(pastas.isFilterToggleVisible)
    }
}
