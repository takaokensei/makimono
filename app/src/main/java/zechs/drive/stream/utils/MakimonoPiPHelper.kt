package zechs.drive.stream.utils

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational

/**
 * FEAT-08: Picture-in-Picture refinement helper.
 * Provides accurate aspect-ratio calculation, safe bounds clamping,
 * and automatic smooth entry on Android 12+ (API 31+).
 */
object MakimonoPiPHelper {

    const val MIN_ASPECT_RATIO = 0.42f
    const val MAX_ASPECT_RATIO = 2.38f

    fun isValidAspectRatio(videoWidth: Int, videoHeight: Int): Boolean {
        if (videoWidth <= 0 || videoHeight <= 0) return false
        val ratio = videoWidth.toFloat() / videoHeight.toFloat()
        return ratio in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO
    }

    val DEFAULT_ASPECT_RATIO by lazy { Rational(16, 9) }

    fun createPiPParams(
        videoWidth: Int = 0,
        videoHeight: Int = 0,
        autoEnter: Boolean = true
    ): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        val aspectRatio = if (isValidAspectRatio(videoWidth, videoHeight)) {
            Rational(videoWidth, videoHeight)
        } else {
            DEFAULT_ASPECT_RATIO
        }

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
        }

        return builder.build()
    }

    fun updatePiPParams(activity: Activity, videoWidth: Int = 0, videoHeight: Int = 0, isPlaying: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = createPiPParams(videoWidth, videoHeight, autoEnter = isPlaying)
            if (params != null) {
                try {
                    activity.setPictureInPictureParams(params)
                } catch (_: Exception) {}
            }
        }
    }

    fun enterPiP(activity: Activity, videoWidth: Int = 0, videoHeight: Int = 0): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = createPiPParams(videoWidth, videoHeight, autoEnter = false)
            return try {
                if (params != null) {
                    activity.enterPictureInPictureMode(params)
                } else {
                    @Suppress("DEPRECATION")
                    activity.enterPictureInPictureMode()
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
        return false
    }
}
