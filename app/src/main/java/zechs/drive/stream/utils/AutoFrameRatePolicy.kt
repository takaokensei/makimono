package zechs.drive.stream.utils

import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.Window
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * AFR (Auto Frame Rate Matching) for Android TV / Living Room 10-foot experience.
 * Inspired by Kodi's CRenderManager::AdjustFrameRate and Apple TV Match Frame Rate.
 * Matches display refresh rate (23.976Hz, 24Hz, 50Hz, 59.94Hz, 60Hz, 120Hz)
 * to media framerate to eliminate 3:2 pulldown judder.
 *
 * Supports dual-layer switching:
 * 1. Android 11+ (API 30+) Surface.setFrameRate (seamless HDMI 2.1 QMS where supported)
 * 2. Android 6.0+ (API 23+) Display.Mode / preferredDisplayModeId matching
 */
object AutoFrameRatePolicy {
    private const val TAG = "AutoFrameRate"

    data class ModeMatch(
        val modeId: Int,
        val refreshRate: Float,
        val width: Int,
        val height: Int
    )

    data class AfrState(
        val targetFps: Double,
        val matchedHz: Float,
        val modeId: Int?,
        val appliedSurface: Boolean,
        val appliedDisplayMode: Boolean
    ) {
        val label: String
            get() {
                val rounded = (matchedHz * 100.0).roundToInt() / 100.0
                return "${rounded}Hz"
            }

        val technicalSummary: String
            get() = String.format(Locale.US, "%.3f fps -> %.3f Hz", targetFps, matchedHz)
    }

    /**
     * Finds the optimal display mode for a given media framerate.
     * Prioritizes exact matches, then integral multiples (e.g. 24fps on 120Hz = 5x).
     */
    fun findBestDisplayMode(
        display: Display?,
        targetFps: Double?,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): ModeMatch? {
        if (display == null || targetFps == null || targetFps <= 0.0) return null
        if (sdkInt < Build.VERSION_CODES.M) return null

        val supportedModes = display.supportedModes ?: return null
        if (supportedModes.isEmpty()) return null

        val currentMode = display.mode
        val targetWidth = currentMode?.physicalWidth ?: 1920
        val targetHeight = currentMode?.physicalHeight ?: 1080

        // Filter to same physical resolution to avoid unexpected resolution changes
        val sameResolutionModes = supportedModes.filter {
            it.physicalWidth == targetWidth && it.physicalHeight == targetHeight
        }

        val candidateModes = if (sameResolutionModes.isNotEmpty()) sameResolutionModes else supportedModes.toList()

        var bestMode: Display.Mode? = null
        var minDiff = Float.MAX_VALUE

        for (mode in candidateModes) {
            val rate = mode.refreshRate
            val diff = abs(rate - targetFps.toFloat())

            // Exact match (e.g. 23.976fps on 23.976Hz or 24fps on 24Hz)
            if (diff < 0.05f) {
                bestMode = mode
                break
            }

            // Check multiples: 24fps on 120Hz (120 / 24 = 5.0) or 24fps on 48Hz
            val multiple = rate / targetFps.toFloat()
            val multipleDiff = abs(multiple - multiple.roundToInt().toFloat())
            if (multipleDiff < 0.02f && diff < minDiff) {
                minDiff = diff
                bestMode = mode
            }
        }

        return bestMode?.let {
            ModeMatch(
                modeId = it.modeId,
                refreshRate = it.refreshRate,
                width = it.physicalWidth,
                height = it.physicalHeight
            )
        }
    }

    /**
     * Applies Surface.setFrameRate on Android 11+ (API 30+).
     * This provides seamless rate adaptation without screen flicker on HDMI 2.1 / QMS displays.
     */
    fun applySurfaceFrameRate(
        surface: Surface?,
        targetFps: Double?,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (surface == null || !surface.isValid || targetFps == null || targetFps <= 0.0) return false
        if (sdkInt < Build.VERSION_CODES.R) return false

        return try {
            surface.setFrameRate(
                targetFps.toFloat(),
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ALWAYS
            )
            Log.d(TAG, "Applied Surface.setFrameRate: ${targetFps}fps")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed Surface.setFrameRate", e)
            false
        }
    }

    /**
     * Clears Surface frame rate on Android 11+ (API 30+).
     */
    fun clearSurfaceFrameRate(
        surface: Surface?,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (surface == null || !surface.isValid) return false
        if (sdkInt < Build.VERSION_CODES.R) return false

        return try {
            surface.clearFrameRate()
            Log.d(TAG, "Cleared Surface frame rate")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed Surface.clearFrameRate", e)
            false
        }
    }

    /**
     * Applies the matched display mode to the given Window.
     */
    fun applyRefreshRate(
        window: Window?,
        modeMatch: ModeMatch?,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (window == null || modeMatch == null) return false
        if (sdkInt < Build.VERSION_CODES.M) return false

        return try {
            val params = window.attributes
            if (params.preferredDisplayModeId != modeMatch.modeId) {
                params.preferredDisplayModeId = modeMatch.modeId
                window.attributes = params
                Log.d(TAG, "Applied display mode ${modeMatch.modeId} (${modeMatch.refreshRate}Hz)")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set preferredDisplayModeId", e)
            false
        }
    }

    /**
     * Dual-layer Auto Frame Rate application (Surface + Window Display.Mode fallback).
     */
    fun applyAutoFrameRate(
        window: Window?,
        surface: Surface?,
        display: Display?,
        targetFps: Double?,
        enabled: Boolean = true
    ): AfrState? {
        if (!enabled || targetFps == null || targetFps <= 0.0) {
            resetAutoFrameRate(window, surface)
            return null
        }

        val appliedSurface = applySurfaceFrameRate(surface, targetFps)
        val modeMatch = findBestDisplayMode(display, targetFps)
        val appliedDisplay = applyRefreshRate(window, modeMatch)

        val matchedHz = modeMatch?.refreshRate ?: targetFps.toFloat()
        val state = AfrState(
            targetFps = targetFps,
            matchedHz = matchedHz,
            modeId = modeMatch?.modeId,
            appliedSurface = appliedSurface,
            appliedDisplayMode = appliedDisplay
        )
        Log.d(TAG, "Auto Frame Rate applied: ${state.technicalSummary} (surface=$appliedSurface, displayMode=$appliedDisplay)")
        return state
    }

    /**
     * Resets the display mode back to default (0).
     */
    fun resetRefreshRate(
        window: Window?,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (window == null) return false
        if (sdkInt < Build.VERSION_CODES.M) return false

        return try {
            val params = window.attributes
            if (params.preferredDisplayModeId != 0) {
                params.preferredDisplayModeId = 0
                window.attributes = params
                Log.d(TAG, "Reset display mode to default")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reset preferredDisplayModeId", e)
            false
        }
    }

    /**
     * Complete AFR reset for both Window and Surface.
     */
    fun resetAutoFrameRate(
        window: Window?,
        surface: Surface?
    ) {
        clearSurfaceFrameRate(surface)
        resetRefreshRate(window)
    }
}
