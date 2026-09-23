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
        if (root.getTag(R.id.tv_focus_ring_installed) == true) return
        root.setTag(R.id.tv_focus_ring_installed, true)
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
        fun update(focused: View?) {
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
        val listener = ViewTreeObserver.OnGlobalFocusChangeListener { _, focused -> update(focused) }
        val layout = ViewTreeObserver.OnGlobalLayoutListener {
            if (target !== root.findFocus()) update(root.findFocus())
            target?.let { ring?.setBounds(0, 0, it.width, it.height) }
        }
        root.viewTreeObserver.addOnGlobalFocusChangeListener(listener)
        root.viewTreeObserver.addOnGlobalLayoutListener(layout)
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                clear()
                v.setTag(R.id.tv_focus_ring_installed, null)
                if (v.viewTreeObserver.isAlive) {
                    v.viewTreeObserver.removeOnGlobalFocusChangeListener(listener)
                    v.viewTreeObserver.removeOnGlobalLayoutListener(layout)
                }
                v.removeOnAttachStateChangeListener(this)
            }
        })
    }
}
