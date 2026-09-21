package zechs.drive.stream.utils

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager

/** Cross-window blur never touches the video surface; older devices get an opaque fallback. */
object FrostedWindow {
    fun apply(window: Window) {
        val density = window.context.resources.displayMetrics.density
        val background = GradientDrawable().apply { cornerRadius = 20 * density }
        window.setBackgroundDrawable(background)
        fun update(enabled: Boolean) {
            background.setColor(if (enabled) 0xB3081828.toInt() else 0xF2081828.toInt())
            window.setDimAmount(if (enabled) 0.25f else 0.6f)
            if (Build.VERSION.SDK_INT >= 31) window.setBackgroundBlurRadius(if (enabled) (24 * density).toInt() else 0)
        }
        update(false)
        if (Build.VERSION.SDK_INT >= 31) {
            val manager = window.context.getSystemService(WindowManager::class.java)
            val listener = java.util.function.Consumer<Boolean> { update(it) }
            manager.addCrossWindowBlurEnabledListener(listener)
            window.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    manager.removeCrossWindowBlurEnabledListener(listener)
                    v.removeOnAttachStateChangeListener(this)
                }
            })
        }
    }
}
