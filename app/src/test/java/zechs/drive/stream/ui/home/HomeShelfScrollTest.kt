package zechs.drive.stream.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeShelfScrollTest {
    @Test
    fun alignsActiveHeadingBelowToolbarWhenFocusedCardHasRoom() {
        assertEquals(
            292,
            HomeShelfScroll.alignmentDelta(
                headingTop = 420,
                focusedItemTop = 482,
                visibleTop = 98,
                topSpacing = 30
            )
        )
    }

    @Test
    fun doesNotScrollWhenHeadingAlreadyHasSafeSpacing() {
        assertEquals(
            0,
            HomeShelfScroll.alignmentDelta(
                headingTop = 110,
                focusedItemTop = 160,
                visibleTop = 98,
                topSpacing = 12
            )
        )
    }

    @Test
    fun keepsFocusedCardInsideViewportWhenHeadingIsFarAway() {
        assertEquals(
            24,
            HomeShelfScroll.alignmentDelta(
                headingTop = 420,
                focusedItemTop = 122,
                visibleTop = 98,
                topSpacing = 12
            )
        )
    }
}
