package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.ui.player.PlayerModalTransition
import zechs.drive.stream.ui.player.PlayerModalVisibilityState

class PlayerModalVisibilityStateTest {
    @Test
    fun firstModalHidesVisibleOsdAndLastDismissRestoresIt() {
        val state = PlayerModalVisibilityState()

        assertEquals(PlayerModalTransition.HIDE_OSD, state.onModalOpened(osdWasVisible = true))
        assertEquals(PlayerModalTransition.RESTORE_OSD, state.onModalClosed())
    }

    @Test
    fun nestedModalKeepsOsdHiddenUntilEveryModalCloses() {
        val state = PlayerModalVisibilityState()

        assertEquals(PlayerModalTransition.HIDE_OSD, state.onModalOpened(osdWasVisible = true))
        assertEquals(PlayerModalTransition.NONE, state.onModalOpened(osdWasVisible = true))
        assertEquals(PlayerModalTransition.NONE, state.onModalClosed())
        assertEquals(PlayerModalTransition.RESTORE_OSD, state.onModalClosed())
    }

    @Test
    fun closingModalDoesNotShowAnOsdThatWasAlreadyHidden() {
        val state = PlayerModalVisibilityState()

        assertEquals(PlayerModalTransition.NONE, state.onModalOpened(osdWasVisible = false))
        assertEquals(PlayerModalTransition.NONE, state.onModalClosed())
        assertEquals(PlayerModalTransition.NONE, state.onModalClosed())
    }
}
