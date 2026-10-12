package zechs.drive.stream.utils

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings

/** Resolves the per-profile preference and system accessibility animation scale without removing focus or state feedback (T-601, T-602). */
object MotionPolicy {
    @Volatile
    private var activeProfilePreference: MotionPreference = MotionPreference.SYSTEM

    fun shouldReduceMotion(preference: MotionPreference, systemAnimationsEnabled: Boolean): Boolean =
        preference == MotionPreference.REDUCED || !systemAnimationsEnabled

    /**
     * Pure evaluation of [Settings.Global.ANIMATOR_DURATION_SCALE] (0f = reduced motion)
     * combined with the user's [MotionPreference] (T-601).
     */
    fun isReducedForAnimatorScale(preference: MotionPreference, animatorDurationScale: Float): Boolean =
        preference == MotionPreference.REDUCED || !animatorDurationScale.isFinite() || animatorDurationScale <= 0f

    /**
     * Resolves an animation duration in ms, returning 0L immediately when reduced motion is active (T-602).
     */
    fun resolveDuration(baseDurationMs: Long, reducedMotion: Boolean): Long =
        if (reducedMotion) 0L else baseDurationMs.coerceAtLeast(0L)

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

    /**
     * Queries [Settings.Global.ANIMATOR_DURATION_SCALE] and accessibility animation state from [context] (T-601).
     */
    fun isReduced(context: Context): Boolean {
        if (activeProfilePreference == MotionPreference.REDUCED) return true
        if (!areSystemAnimationsEnabled()) return true
        val scale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
        } catch (_: Exception) {
            1.0f
        }
        return isReducedForAnimatorScale(activeProfilePreference, scale)
    }

    /** Synchronous system-level fallback for views that cannot await per-profile settings. */
    fun isReducedMotion(): Boolean = shouldReduceMotion(
        activeProfilePreference,
        areSystemAnimationsEnabled()
    )
}
