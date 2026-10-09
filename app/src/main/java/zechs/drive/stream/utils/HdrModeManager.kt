package zechs.drive.stream.utils

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.Display
import androidx.media3.common.C
import androidx.media3.common.Format

/**
 * Apple TV / Netflix Grade HDR Mode Manager (Match Dynamic Range).
 * Automatically detects HDR10 (PQ/ST2084), Dolby Vision, HLG, and HDR10+,
 * evaluates display HDR capabilities, dynamically switches the Android Activity
 * window color mode to COLOR_MODE_HDR on API 26+, and restores SDR on exit.
 */
object HdrModeManager {
    private const val TAG = "HdrModeManager"

    enum class DynamicRange(
        val label: String,
        val badgeText: String,
        val isHdr: Boolean
    ) {
        SDR("SDR", "", false),
        HDR10("HDR10", "HDR10", true),
        DOLBY_VISION("Dolby Vision", "VISION", true),
        HLG("HLG", "HLG", true),
        HDR10_PLUS("HDR10+", "HDR10+", true)
    }

    data class HdrStatus(
        val dynamicRange: DynamicRange,
        val isDisplaySupported: Boolean,
        val isHdrActive: Boolean,
        val colorSpaceDesc: String
    )

    /**
     * Inspects ExoPlayer Format to detect HDR transfer and color characteristics.
     */
    fun detectExoPlayerHdr(format: Format?): DynamicRange {
        if (format == null) return DynamicRange.SDR

        val codecs = format.codecs?.lowercase() ?: ""
        if (codecs.contains("dvhe") || codecs.contains("dvh1") || codecs.contains("dva1") || codecs.contains("dvav")) {
            return DynamicRange.DOLBY_VISION
        }

        val colorInfo = format.colorInfo ?: return DynamicRange.SDR
        return when (colorInfo.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> {
                if (colorInfo.hdrStaticInfo != null && codecs.contains("hdr10plus")) {
                    DynamicRange.HDR10_PLUS
                } else {
                    DynamicRange.HDR10
                }
            }
            C.COLOR_TRANSFER_HLG -> DynamicRange.HLG
            else -> {
                if (colorInfo.colorSpace == C.COLOR_SPACE_BT2020) {
                    DynamicRange.HDR10
                } else {
                    DynamicRange.SDR
                }
            }
        }
    }

    /**
     * Inspects MPV video properties to detect HDR transfer and color characteristics.
     */
    fun detectMpvHdr(gamma: String?, colormatrix: String?, codec: String?): DynamicRange {
        val codecLower = codec?.lowercase() ?: ""
        if (codecLower.contains("dovi") || codecLower.contains("dvhe") || codecLower.contains("dolby")) {
            return DynamicRange.DOLBY_VISION
        }
        val gammaLower = gamma?.lowercase() ?: ""
        if (gammaLower == "pq") return DynamicRange.HDR10
        if (gammaLower == "hlg") return DynamicRange.HLG

        val matrixLower = colormatrix?.lowercase() ?: ""
        if (matrixLower.contains("2020") && gammaLower.contains("pq")) return DynamicRange.HDR10

        return DynamicRange.SDR
    }

    /**
     * Checks if the connected TV or display supports HDR.
     */
    fun isDisplayHdrCapable(display: Display?): Boolean {
        if (display == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hdrCaps = display.hdrCapabilities ?: return false
            val types = hdrCaps.supportedHdrTypes ?: intArrayOf()
            if (types.isNotEmpty()) return true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val hdrCaps = @Suppress("DEPRECATION") display.hdrCapabilities ?: return false
            val types = @Suppress("DEPRECATION") hdrCaps.supportedHdrTypes ?: intArrayOf()
            if (types.isNotEmpty()) return true
        }
        return false
    }

    /**
     * Applies HDR mode to the window if enabled and supported.
     */
    fun applyHdrMode(
        activity: Activity,
        dynamicRange: DynamicRange,
        enabled: Boolean,
        display: Display?
    ): HdrStatus {
        val isCapable = isDisplayHdrCapable(display)
        val shouldActivate = enabled && dynamicRange.isHdr && (isCapable || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                activity.window.colorMode = if (shouldActivate) {
                    ActivityInfo.COLOR_MODE_HDR
                } else {
                    ActivityInfo.COLOR_MODE_DEFAULT
                }
                AppLog.d(TAG, "Applied window colorMode: ${if (shouldActivate) "COLOR_MODE_HDR" else "COLOR_MODE_DEFAULT"} (range=${dynamicRange.label})")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to apply window colorMode", e)
            }
        }

        val colorSpaceDesc = when (dynamicRange) {
            DynamicRange.DOLBY_VISION -> "Dolby Vision (BT.2020 PQ)"
            DynamicRange.HDR10 -> "HDR10 (BT.2020 PQ)"
            DynamicRange.HDR10_PLUS -> "HDR10+ Dynamic (BT.2020 PQ)"
            DynamicRange.HLG -> "HLG (BT.2020 HLG)"
            DynamicRange.SDR -> "SDR (Rec.709)"
        }

        return HdrStatus(
            dynamicRange = dynamicRange,
            isDisplaySupported = isCapable,
            isHdrActive = shouldActivate,
            colorSpaceDesc = colorSpaceDesc
        )
    }

    /**
     * Resets window colorMode back to COLOR_MODE_DEFAULT (SDR).
     */
    fun resetHdrMode(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                activity.window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
                AppLog.d(TAG, "Reset window colorMode to COLOR_MODE_DEFAULT")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to reset window colorMode", e)
            }
        }
    }
}
