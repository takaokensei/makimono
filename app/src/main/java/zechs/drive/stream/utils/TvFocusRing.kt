package zechs.drive.stream.utils

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import zechs.drive.stream.R

/** An overlay leaves existing click, focus, ripple and card animations untouched. */
object TvFocusRing {
    private val clearCallbacks = java.util.WeakHashMap<View, () -> Unit>()

    /** Clear active focus ring on or within the specified root view */
    fun clear(root: View) {
        clearCallbacks[root]?.invoke()
    }

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

        clearCallbacks[root] = { clear() }

        fun belongsToRoot(view: View): Boolean {
            var current: View? = view
            while (current != null) {
                if (current === root) return true
                current = current.parent as? View
            }
            return false
        }

        fun isValidFocusTarget(v: View?): Boolean {
            if (v == null) return false
            if (v === root) return false // Never draw focus ring on the root container/player surface
            if (!v.isShown) return false // Must be visible and have all visible ancestors
            if (v.visibility != View.VISIBLE) return false
            if (v.alpha < 0.1f) return false
            if (v.width <= 0 || v.height <= 0) return false

            // Never draw focus ring on video surfaces or fullscreen player containers
            val className = v.javaClass.name
            if (className.contains("PlayerView") ||
                className.contains("MPVView") ||
                className.contains("SurfaceView") ||
                className.contains("TextureView")
            ) {
                return false
            }
            if (v.id == R.id.player_view) return false

            return belongsToRoot(v)
        }

        fun update(focused: View?) {
            clear()
            if (isValidFocusTarget(focused)) {
                val targetView = focused!!
                target = targetView
                ring = ContextCompat.getDrawable(root.context, R.drawable.shelf_item_focus_border)?.mutate()?.apply {
                    state = intArrayOf(android.R.attr.state_focused)
                    setBounds(0, 0, targetView.width, targetView.height)
                    targetView.overlay.add(this)
                }
            }
        }

        val listener = ViewTreeObserver.OnGlobalFocusChangeListener { _, focused -> update(focused) }
        val layout = ViewTreeObserver.OnGlobalLayoutListener {
            val currentFocus = root.findFocus()
            if (target !== currentFocus || (target != null && !isValidFocusTarget(target))) {
                update(currentFocus)
            } else {
                target?.let { ring?.setBounds(0, 0, it.width, it.height) }
            }
        }
        root.viewTreeObserver.addOnGlobalFocusChangeListener(listener)
        root.viewTreeObserver.addOnGlobalLayoutListener(layout)
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                clear()
                clearCallbacks.remove(v)
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
