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
            val isTv = DeviceUi.isTenFootExperience(window.context)
            val allowBlur = !isTv && MotionPolicy.shouldUseBlur(
                preference = MotionPolicy.activePreference(),
                systemAnimationsEnabled = MotionPolicy.areSystemAnimationsEnabled(),
                crossWindowBlurEnabled = enabled,
                lowRamDevice = FocusEngine.isLowRam(window.context)
            )
            val glassColor = androidx.core.content.ContextCompat.getColor(
                window.context,
                if (allowBlur) zechs.drive.stream.R.color.obsidian_bg_glass else zechs.drive.stream.R.color.obsidian_bg_surface
            )
            background.setColor(glassColor)
            window.setDimAmount(0.35f)
            if (Build.VERSION.SDK_INT >= 31) {
                window.setBackgroundBlurRadius(if (allowBlur) (24 * density).toInt() else 0)
            }
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
