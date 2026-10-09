package zechs.drive.stream.playback.display

import android.view.Display
import android.view.Surface
import android.view.Window
import zechs.drive.stream.utils.AutoFrameRatePolicy

/**
 * ADR-002: DisplayModeController
 * Coordinates display refresh rate matching for 10-foot Android TV playback.
 * Utilizes the official Android 11+ (API 30+) Surface.setFrameRate with seamless
 * HDMI 2.1 QMS switching, falling back to Android 6.0+ (API 23+) preferredDisplayModeId.
 */
class DisplayModeController {

    var currentState: AutoFrameRatePolicy.AfrState? = null
        private set

    /**
     * Matches screen refresh rate to media framerate.
     */
    fun apply(
        window: Window?,
        surface: Surface?,
        display: Display?,
        targetFps: Double?,
        enabled: Boolean = true
    ): AutoFrameRatePolicy.AfrState? {
        val state = AutoFrameRatePolicy.applyAutoFrameRate(
            window = window,
            surface = surface,
            display = display,
            targetFps = targetFps,
            enabled = enabled
        )
        currentState = state
        return state
    }

    /**
     * Resets refresh rate back to system default.
     */
    fun reset(window: Window?, surface: Surface?) {
        AutoFrameRatePolicy.resetAutoFrameRate(window, surface)
        currentState = null
    }
}
