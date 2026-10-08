package zechs.drive.stream.utils

import android.view.Display
import android.view.Window
import android.view.WindowManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFrameRatePolicyTest {

    @Test
    fun `findBestDisplayMode returns null when display or targetFps is null or invalid`() {
        assertNull(AutoFrameRatePolicy.findBestDisplayMode(null, 24.0, sdkInt = 23))

        val display = mockk<Display>()
        assertNull(AutoFrameRatePolicy.findBestDisplayMode(display, null, sdkInt = 23))
        assertNull(AutoFrameRatePolicy.findBestDisplayMode(display, 0.0, sdkInt = 23))
        assertNull(AutoFrameRatePolicy.findBestDisplayMode(display, -24.0, sdkInt = 23))
    }

    @Test
    fun `findBestDisplayMode returns exact 24Hz match when available`() {
        val display = mockk<Display>()
        val currentMode = mockk<Display.Mode>()
        every { currentMode.physicalWidth } returns 1920
        every { currentMode.physicalHeight } returns 1080
        every { display.mode } returns currentMode

        val mode60 = mockk<Display.Mode>()
        every { mode60.modeId } returns 1
        every { mode60.physicalWidth } returns 1920
        every { mode60.physicalHeight } returns 1080
        every { mode60.refreshRate } returns 60.0f

        val mode24 = mockk<Display.Mode>()
        every { mode24.modeId } returns 2
        every { mode24.physicalWidth } returns 1920
        every { mode24.physicalHeight } returns 1080
        every { mode24.refreshRate } returns 24.0f

        every { display.supportedModes } returns arrayOf(mode60, mode24)

        val match = AutoFrameRatePolicy.findBestDisplayMode(display, 24.0, sdkInt = 23)
        assertNotNull(match)
        assertEquals(2, match!!.modeId)
        assertEquals(24.0f, match.refreshRate)
    }

    @Test
    fun `findBestDisplayMode matches 23_976fps to 23_976Hz`() {
        val display = mockk<Display>()
        val currentMode = mockk<Display.Mode>()
        every { currentMode.physicalWidth } returns 3840
        every { currentMode.physicalHeight } returns 2160
        every { display.mode } returns currentMode

        val mode60 = mockk<Display.Mode>()
        every { mode60.modeId } returns 1
        every { mode60.physicalWidth } returns 3840
        every { mode60.physicalHeight } returns 2160
        every { mode60.refreshRate } returns 60.0f

        val mode239 = mockk<Display.Mode>()
        every { mode239.modeId } returns 2
        every { mode239.physicalWidth } returns 3840
        every { mode239.physicalHeight } returns 2160
        every { mode239.refreshRate } returns 23.976f

        every { display.supportedModes } returns arrayOf(mode60, mode239)

        val match = AutoFrameRatePolicy.findBestDisplayMode(display, 23.976, sdkInt = 23)
        assertNotNull(match)
        assertEquals(2, match!!.modeId)
        assertEquals(23.976f, match.refreshRate)
    }

    @Test
    fun `applyRefreshRate sets window attributes correctly`() {
        val window = mockk<Window>()
        val params = WindowManager.LayoutParams()
        params.preferredDisplayModeId = 0
        every { window.attributes } returns params
        every { window.attributes = any() } answers { }

        val match = AutoFrameRatePolicy.ModeMatch(modeId = 5, refreshRate = 24.0f, width = 1920, height = 1080)
        val applied = AutoFrameRatePolicy.applyRefreshRate(window, match, sdkInt = 23)

        assertTrue(applied)
        assertEquals(5, params.preferredDisplayModeId)
    }

    @Test
    fun `resetRefreshRate resets window attribute to 0`() {
        val window = mockk<Window>()
        val params = WindowManager.LayoutParams()
        params.preferredDisplayModeId = 5
        every { window.attributes } returns params
        every { window.attributes = any() } answers { }

        val reset = AutoFrameRatePolicy.resetRefreshRate(window, sdkInt = 23)
        assertTrue(reset)
        assertEquals(0, params.preferredDisplayModeId)
    }
}
