package zechs.drive.stream.utils

import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Window
import kotlin.math.abs

/**
 * AFR (Auto Frame Rate Matching) for Android TV / Living Room 10-foot experience.
 * Inspired by Kodi's CRenderManager::AdjustFrameRate.
 * Matches display refresh rate (23.976Hz, 24Hz, 50Hz, 59.94Hz, 60Hz, 120Hz)
 * to media framerate to eliminate 3:2 pulldown judder.
 */
object AutoFrameRatePolicy {
    private const val TAG = "AutoFrameRate"

    data class ModeMatch(
        val modeId: Int,
        val refreshRate: Float,
        val width: Int,
        val height: Int
    )

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

            // Check multiples: 24fps on 120Hz (120 / 24 = 5.0)
            val multiple = rate / targetFps.toFloat()
            val multipleDiff = abs(multiple - Math.round(multiple).toFloat())
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
}
