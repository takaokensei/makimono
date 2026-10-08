package zechs.drive.stream.utils

import android.app.ActivityManager
import android.content.Context
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import zechs.drive.stream.R

/**
 * Universal Focus Engine implementing Apple tvOS style physical focus response.
 * Applies elastic spring scaling, elevation and depth when focused,
 * with graceful fallback under Reduced Motion or low-RAM environments.
 */
object FocusEngine {

    enum class FocusKind {
        CARD,
        BUTTON,
        LIST_ROW,
        NONE
    }

    data class FocusTarget(
        val scale: Float,
        val elevationDp: Float
    )

    /**
     * Pure policy calculation determining the scale and elevation targets.
     */
    fun targetFor(kind: FocusKind, focused: Boolean, reducedMotion: Boolean): FocusTarget {
        if (!focused || kind == FocusKind.NONE) {
            return FocusTarget(scale = 1.0f, elevationDp = 0.0f)
        }
        if (reducedMotion) {
            // In reduced motion mode, maintain 1.0x scale and 0dp elevation, letting static highlight suffice
            return FocusTarget(scale = 1.0f, elevationDp = 0.0f)
        }
        return when (kind) {
            FocusKind.CARD -> FocusTarget(scale = 1.08f, elevationDp = 12.0f)
            FocusKind.BUTTON -> FocusTarget(scale = 1.04f, elevationDp = 6.0f)
            FocusKind.LIST_ROW -> FocusTarget(scale = 1.0f, elevationDp = 0.0f)
            FocusKind.NONE -> FocusTarget(scale = 1.0f, elevationDp = 0.0f)
        }
    }

    /**
     * Checks if the device has low RAM or animations are disabled.
     */
    fun isLowRam(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.isLowRamDevice == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Attaches physics-driven focus interaction to a target View.
     */
    fun attach(view: View, kind: FocusKind, onFocusChange: ((View, Boolean) -> Unit)? = null) {
        if (kind == FocusKind.NONE) return

        view.setTag(R.id.focus_engine_attached, true)

        val density = view.resources.displayMetrics.density

        val springX = SpringAnimation(view, DynamicAnimation.SCALE_X).apply {
            spring = SpringForce().apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = 0.8f
            }
        }
        val springY = SpringAnimation(view, DynamicAnimation.SCALE_Y).apply {
            spring = SpringForce().apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = 0.8f
            }
        }

        view.setOnFocusChangeListener { v, hasFocus ->
            val context = v.context
            val isReduced = MotionPolicy.isReducedMotion() || isLowRam(context)
            val target = targetFor(kind, hasFocus, isReduced)

            // Cancel any pending animations to prevent frame thrashing
            springX.cancel()
            springY.cancel()

            if (isReduced) {
                v.scaleX = target.scale
                v.scaleY = target.scale
                v.translationZ = target.elevationDp * density
            } else {
                springX.animateToFinalPosition(target.scale)
                springY.animateToFinalPosition(target.scale)
                v.animate()
                    .translationZ(target.elevationDp * density)
                    .setDuration(150L)
                    .start()
            }
            onFocusChange?.invoke(v, hasFocus)
        }
    }
}
