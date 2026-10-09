package zechs.drive.stream.playback.display

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DisplayModeControllerTest {

    private lateinit var controller: DisplayModeController

    @Before
    fun setup() {
        controller = DisplayModeController()
    }

    @Test
    fun `initial state is null`() {
        assertNull(controller.currentState)
    }

    @Test
    fun `apply with null fps resets and returns null`() {
        val result = controller.apply(
            window = null,
            surface = null,
            display = null,
            targetFps = null,
            enabled = true
        )
        assertNull(result)
        assertNull(controller.currentState)
    }

    @Test
    fun `apply with disabled flag resets and returns null`() {
        val result = controller.apply(
            window = null,
            surface = null,
            display = null,
            targetFps = 24.0,
            enabled = false
        )
        assertNull(result)
        assertNull(controller.currentState)
    }

    @Test
    fun `reset clears currentState`() {
        controller.reset(null, null)
        assertNull(controller.currentState)
    }
}
