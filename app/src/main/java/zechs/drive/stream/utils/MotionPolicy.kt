package zechs.drive.stream.utils

import android.animation.ValueAnimator
import android.os.Build

/** Resolves the per-profile preference without removing focus or state feedback. */
object MotionPolicy {
    @Volatile
    private var activeProfilePreference: MotionPreference = MotionPreference.SYSTEM

    fun shouldReduceMotion(preference: MotionPreference, systemAnimationsEnabled: Boolean): Boolean =
        preference == MotionPreference.REDUCED || !systemAnimationsEnabled

    fun shouldUseBlur(
        preference: MotionPreference,
        systemAnimationsEnabled: Boolean,
        crossWindowBlurEnabled: Boolean,
        lowRamDevice: Boolean
    ): Boolean = crossWindowBlurEnabled &&
        !shouldReduceMotion(preference, systemAnimationsEnabled) &&
        !lowRamDevice

    /** Keep synchronous View callbacks aligned with the active profile's persisted setting. */
    fun updateActiveProfilePreference(preference: MotionPreference) {
        activeProfilePreference = preference
    }

    fun activePreference(): MotionPreference = activeProfilePreference

    /** Animator scale is managed by Android; API 26+ also exposes its effective state. */
    fun areSystemAnimationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    /** Synchronous system-level fallback for views that cannot await per-profile settings. */
    fun isReducedMotion(): Boolean = shouldReduceMotion(
        activeProfilePreference,
        areSystemAnimationsEnabled()
    )
}
