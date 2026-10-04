package zechs.drive.stream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.utils.PlayerControllerVisibility

class PlayerControllerVisibilityTest {
    @Test
    fun `hidden ExoPlayer controller stays hidden when custom child views remain visible`() {
        // PlayerView hides its controller container but leaves the custom layout children VISIBLE.
        assertFalse(PlayerControllerVisibility.isVisible(controllerVisible = false, isHiding = false))
    }

    @Test
    fun `visible controller is reported while the OSD is open`() {
        assertTrue(PlayerControllerVisibility.isVisible(controllerVisible = true, isHiding = false))
    }

    @Test
    fun `controller transition is not treated as an open OSD`() {
        assertFalse(PlayerControllerVisibility.isVisible(controllerVisible = true, isHiding = true))
    }
}
