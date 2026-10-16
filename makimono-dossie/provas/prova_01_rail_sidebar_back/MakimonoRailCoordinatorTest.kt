package makimono.dossie.provas.rail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MakimonoRailCoordinatorTest {

    private lateinit var coordinator: MakimonoRailCoordinator

    @Before
    fun setUp() {
        coordinator = MakimonoRailCoordinator()
    }

    @Test
    fun initialStateIsCollapsedInContent() {
        assertEquals(RailFocusState.CollapsedInContent, coordinator.state)
    }

    @Test
    fun movingFocusToRailExpandsTo264dp() {
        val width = coordinator.onRailFocused(2)
        assertEquals(264, width)
        assertEquals(RailFocusState.ExpandedInRail(2), coordinator.state)
    }

    @Test
    fun movingFocusToContentCollapsesTo80dp() {
        coordinator.onRailFocused(0)
        val width = coordinator.onContentFocused()
        assertEquals(80, width)
        assertEquals(RailFocusState.CollapsedInContent, coordinator.state)
    }

    @Test
    fun backPressFromContentAnchorsToRailWithoutExiting() {
        coordinator.onContentFocused()
        val result = coordinator.handleBackPress()

        assertEquals(BackPressResult.ConsumedAnchorToRail, result)
        assertTrue(coordinator.state is RailFocusState.ExpandedInRail)
    }

    @Test
    fun backPressFromRailOpensExitConfirmationDialog() {
        coordinator.onRailFocused(0)
        val result = coordinator.handleBackPress()

        assertEquals(BackPressResult.ConsumedShowExitDialog, result)
        assertEquals(RailFocusState.ExitConfirmationDialogVisible, coordinator.state)
    }

    @Test
    fun backPressWhileExitDialogIsVisibleDismissesDialogAndRestoresRail() {
        coordinator.onRailFocused(0)
        coordinator.handleBackPress() // Opens dialog
        assertEquals(RailFocusState.ExitConfirmationDialogVisible, coordinator.state)

        val result = coordinator.handleBackPress() // Dismisses dialog
        assertEquals(BackPressResult.ConsumedDismissExitDialog, result)
        assertTrue(coordinator.state is RailFocusState.ExpandedInRail)
    }
}
