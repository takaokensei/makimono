package zechs.drive.stream.utils

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import zechs.drive.stream.R

/** An overlay leaves existing click, focus, ripple and card animations untouched. */
object TvFocusRing {
    fun install(root: View) {
        var target: View? = null
        var ring: Drawable? = null
        fun clear() {
            ring?.let { target?.overlay?.remove(it) }
            target = null
            ring = null
        }
        fun belongsToRoot(view: View): Boolean {
            var current: View? = view
            while (current != null) {
                if (current === root) return true
                current = current.parent as? View
            }
            return false
        }
        val listener = ViewTreeObserver.OnGlobalFocusChangeListener { _, focused ->
            clear()
            if (focused != null && belongsToRoot(focused)) {
                target = focused
                ring = ContextCompat.getDrawable(root.context, R.drawable.shelf_item_focus_border)?.mutate()?.apply {
                    state = intArrayOf(android.R.attr.state_focused)
                    setBounds(0, 0, focused.width, focused.height)
                    focused.overlay.add(this)
                }
            }
        }
        val layout = ViewTreeObserver.OnGlobalLayoutListener {
            target?.let { ring?.setBounds(0, 0, it.width, it.height) }
        }
        root.viewTreeObserver.addOnGlobalFocusChangeListener(listener)
        root.viewTreeObserver.addOnGlobalLayoutListener(layout)
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                clear()
                if (v.viewTreeObserver.isAlive) {
                    v.viewTreeObserver.removeOnGlobalFocusChangeListener(listener)
                    v.viewTreeObserver.removeOnGlobalLayoutListener(layout)
                }
                v.removeOnAttachStateChangeListener(this)
            }
        })
    }
}
