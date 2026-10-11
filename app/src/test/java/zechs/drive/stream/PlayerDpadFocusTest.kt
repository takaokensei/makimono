package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import zechs.drive.stream.utils.PlayerDpadFocus
import zechs.drive.stream.utils.PlayerFocusDirection
import zechs.drive.stream.utils.PlayerFocusItem

class PlayerDpadFocusTest {
    @Test
    fun primaryTransportAndExitHaveAnExplicitVerticalReturnPath() {
        assertEquals(PlayerFocusItem.EXIT, PlayerDpadFocus.next(
            PlayerFocusDirection.UP, true, false, false, PlayerFocusItem.PLAY_PAUSE
        ))
        assertEquals(PlayerFocusItem.EXIT, PlayerDpadFocus.next(
            PlayerFocusDirection.UP, true, false, false, PlayerFocusItem.OTHER
        ))
        assertEquals(PlayerFocusItem.PLAY_PAUSE, PlayerDpadFocus.next(
            PlayerFocusDirection.DOWN, true, false, false, PlayerFocusItem.EXIT
        ))
    }

    @Test
    fun overlaysHiddenControlsTransitionsAndOtherItemsKeepTheirOwnNavigation() {
        assertNull(PlayerDpadFocus.next(
            PlayerFocusDirection.UP, true, false, true, PlayerFocusItem.PLAY_PAUSE
        ))
        assertNull(PlayerDpadFocus.next(
            PlayerFocusDirection.UP, false, false, false, PlayerFocusItem.PLAY_PAUSE
        ))
        assertNull(PlayerDpadFocus.next(
            PlayerFocusDirection.UP, true, true, false, PlayerFocusItem.PLAY_PAUSE
        ))
        assertNull(PlayerDpadFocus.next(
            PlayerFocusDirection.DOWN, true, false, false, PlayerFocusItem.OTHER
        ))
    }
}
