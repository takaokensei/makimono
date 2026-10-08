package zechs.drive.stream.utils

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/**
 * Keeps touch activation independent from D-pad focus acquisition for compact TV navigation rails.
 * The regular click listener remains the single source of navigation behavior.
 */
object SingleTapActivation {
    fun install(view: View, disallowParentIntercept: Boolean = true) {
        val slop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
        val gesture = TapGestureState()
        var downX = 0f
        var downY = 0f

        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    gesture.begin()
                    target.isPressed = target.isEnabled && target.isClickable
                    if (disallowParentIntercept) {
                        target.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val inside = event.x >= -slop && event.x < target.width + slop &&
                        event.y >= -slop && event.y < target.height + slop
                    val withinSlop = kotlin.math.abs(event.x - downX) <= slop &&
                        kotlin.math.abs(event.y - downY) <= slop
                    if (!inside || !withinSlop) {
                        gesture.cancelActivation()
                        target.isPressed = false
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val activate = gesture.end() && target.isEnabled && target.isClickable && target.isPressed
                    target.isPressed = false
                    if (activate) target.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    gesture.cancel()
                    target.isPressed = false
                    true
                }

                else -> true
            }
        }
    }
}

/** Small state machine so an interrupted/out-of-bounds gesture can never activate navigation. */
internal class TapGestureState {
    private var tracking = false
    private var activationAllowed = false

    fun begin() {
        tracking = true
        activationAllowed = true
    }

    fun cancelActivation() {
        if (tracking) activationAllowed = false
    }

    fun end(): Boolean {
        val shouldActivate = tracking && activationAllowed
        cancel()
        return shouldActivate
    }

    fun cancel() {
        tracking = false
        activationAllowed = false
    }
}
