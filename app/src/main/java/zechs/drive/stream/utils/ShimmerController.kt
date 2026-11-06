package zechs.drive.stream.utils

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View

/**
 * Shimmer controller providing Apple TV style gentle breathing luminance animation (T2.7)
 * for loading skeleton cards, with graceful static fallback under Reduced Motion.
 */
object ShimmerController {

    data class ShimmerSpec(
        val minAlpha: Float,
        val maxAlpha: Float,
        val durationMs: Long,
        val isAnimated: Boolean
    )

    fun resolveSpec(reducedMotion: Boolean): ShimmerSpec {
        return if (reducedMotion) {
            ShimmerSpec(
                minAlpha = 0.08f,
                maxAlpha = 0.08f,
                durationMs = 0L,
                isAnimated = false
            )
        } else {
            ShimmerSpec(
                minAlpha = 0.06f,
                maxAlpha = 0.12f,
                durationMs = 1200L,
                isAnimated = true
            )
        }
    }

    /**
     * Starts gentle breathing shimmer on the target View or hierarchy.
     */
    fun start(view: View, minAlpha: Float? = null, maxAlpha: Float? = null): ValueAnimator? {
        val reduced = MotionPolicy.isReducedMotion()
        val defaultSpec = resolveSpec(reduced)
        val actualMinAlpha = if (reduced) (minAlpha ?: defaultSpec.minAlpha) else (minAlpha ?: defaultSpec.minAlpha)
        val actualMaxAlpha = if (reduced) actualMinAlpha else (maxAlpha ?: defaultSpec.maxAlpha)

        view.alpha = actualMinAlpha
        if (!defaultSpec.isAnimated) {
            return null
        }

        return ObjectAnimator.ofFloat(view, "alpha", actualMinAlpha, actualMaxAlpha).apply {
            duration = defaultSpec.durationMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }
}
