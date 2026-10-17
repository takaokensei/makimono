package zechs.drive.stream.utils

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView
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
        disablePlatformContainerHighlights(root)
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
            // Profile cards put selection/focus on the round avatar itself. A rectangular overlay
            // around the card recreates the gray tile treatment the profile picker is designed to avoid.
            if (v.id == R.id.cardProfileRoot) return false
            if (!v.isShown) return false // Must be visible and have all visible ancestors
            if (v.visibility != View.VISIBLE) return false
            if (v.alpha < 0.1f) return false
            if (v.width <= 0 || v.height <= 0) return false

            // Never draw focus ring on video surfaces or fullscreen player containers
            val className = v.javaClass.name
            if (className.contains("PlayerView") ||
                className.contains("MPVView") ||
                className.contains("SurfaceView") ||
                className.contains("TextureView") ||
                className.contains("RecyclerView") ||
                className.contains("ScrollView") ||
                className.contains("ViewPager")
            ) {
                return false
            }
            if (v.id == R.id.player_view || v.id == R.id.etSearchAnime || v.id == R.id.etSearch) return false
            // FocusEngine provides motion/depth, not a visible outline. Keep the shared ring for
            // its targets unless their own background already declares a focused state.
            // Profile cards are excluded above because their circular avatar owns the indicator.
            if (hasFocusedBackground(v.background)) return false

            return belongsToRoot(v)
        }

        fun update(focused: View?) {
            clear()
            if (isValidFocusTarget(focused)) {
                val targetView = focused!!
                target = targetView
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    // This overlay owns focus visuals when the component background has no
                    // focused state; the platform's rectangular highlight would clash with it.
                    targetView.defaultFocusHighlightEnabled = false
                }
                val accent = root.context.resolveThemeColor(R.attr.colorAccentPrimary)
                val fill = root.context.resolveThemeColor(R.attr.colorFocusFill)
                val density = root.resources.displayMetrics.density
                val isAvatarArt = targetView.id == R.id.cardArtRoot &&
                    targetView.width <= targetView.height * 1.25f
                val cornerRadius = when {
                    isAvatarArt -> minOf(targetView.width, targetView.height) / 2f
                    targetView.height <= 56 * density -> targetView.height / 2f
                    else -> 12 * density
                }
                ring = GradientDrawable().apply {
                    setColor(if (targetView.height <= 56 * density) fill else Color.TRANSPARENT)
                    setStroke((3 * density).toInt().coerceAtLeast(1), accent)
                    setCornerRadius(cornerRadius)
                    setBounds(0, 0, targetView.width, targetView.height)
                    targetView.overlay.add(this)
                    targetView.invalidate()
                }
            }
        }

        val listener = ViewTreeObserver.OnGlobalFocusChangeListener { _, focused -> update(focused) }
        val layout = ViewTreeObserver.OnGlobalLayoutListener {
            disablePlatformContainerHighlights(root)
            val currentFocus = root.findFocus()
            if (target !== currentFocus || (target != null && !isValidFocusTarget(target))) {
                update(currentFocus)
            } else {
                target?.let {
                    ring?.setBounds(0, 0, it.width, it.height)
                    it.invalidate()
                }
            }
        }
        root.viewTreeObserver.addOnGlobalFocusChangeListener(listener)
        root.viewTreeObserver.addOnGlobalLayoutListener(layout)
        root.post { update(root.findFocus()) }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                root.post { update(root.findFocus()) }
            }
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

    private fun disablePlatformContainerHighlights(root: View) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        if (root is RecyclerView) root.defaultFocusHighlightEnabled = false
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) disablePlatformContainerHighlights(root.getChildAt(index))
        }
    }

    private fun hasFocusedBackground(drawable: Drawable?): Boolean {
        val selector = drawable as? StateListDrawable ?: return false
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return false
        return Api29Impl.hasFocusedState(selector)
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private object Api29Impl {
        @androidx.annotation.DoNotInline
        fun hasFocusedState(selector: StateListDrawable): Boolean {
            return FocusSelectorPolicy.hasFocusedState(
                stateSpecs = (0 until selector.stateCount).map(selector::getStateSet),
                focusedStateAttribute = android.R.attr.state_focused
            )
        }
    }

    private fun android.content.Context.resolveThemeColor(attribute: Int): Int {
        val value = TypedValue()
        return if (theme.resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) ContextCompat.getColor(this, value.resourceId) else value.data
        } else {
            Color.WHITE
        }
    }
}
