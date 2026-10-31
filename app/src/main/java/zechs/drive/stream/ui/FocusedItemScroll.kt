package zechs.drive.stream.ui

import android.view.View
import android.view.ViewTreeObserver
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView

/** Keeps D-pad focused collection items visible without replacing adapter focus listeners. */
object FocusedItemScroll {
    fun install(viewport: NestedScrollView, rows: List<RecyclerView>) {
        if (rows.isEmpty()) return

        var registeredObserver: ViewTreeObserver? = null
        val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, focused ->
            var ancestor = focused
            var belongsToCollection = false
            while (ancestor != null) {
                if (rows.any { it === ancestor }) {
                    belongsToCollection = true
                    break
                }
                ancestor = ancestor.parent as? View
            }
            if (!belongsToCollection) return@OnGlobalFocusChangeListener

            focused?.post {
                if (!viewport.isShown || !focused.isShown || viewport.height == 0) return@post
                val viewportLocation = IntArray(2)
                val focusedLocation = IntArray(2)
                viewport.getLocationOnScreen(viewportLocation)
                focused.getLocationOnScreen(focusedLocation)
                val focusedTop = focusedLocation[1]
                val focusedBottom = focusedTop + focused.height
                val safeInset = if (zechs.drive.stream.utils.DeviceUi.isTenFootExperience(viewport.context))
                    viewport.resources.getDimensionPixelSize(zechs.drive.stream.R.dimen.tv_overscan_margin_vertical) else 0
                val visibleTop = viewportLocation[1] + maxOf(viewport.paddingTop, safeInset)
                val visibleBottom = viewportLocation[1] + viewport.height - maxOf(viewport.paddingBottom, safeInset)
                val availableHeight = visibleBottom - visibleTop
                val scrollDelta = when {
                    focused.height > availableHeight -> focusedTop - visibleTop
                    focusedTop < visibleTop -> focusedTop - visibleTop
                    focusedBottom > visibleBottom -> focusedBottom - visibleBottom
                    else -> 0
                }
                if (scrollDelta != 0) viewport.smoothScrollBy(0, scrollDelta)
            }
        }

        fun registerFocusListener() {
            val currentObserver = viewport.viewTreeObserver
            if (currentObserver.isAlive && registeredObserver !== currentObserver) {
                registeredObserver?.takeIf { it.isAlive }?.removeOnGlobalFocusChangeListener(focusListener)
                currentObserver.addOnGlobalFocusChangeListener(focusListener)
                registeredObserver = currentObserver
            }
        }

        fun unregisterFocusListener() {
            registeredObserver?.takeIf { it.isAlive }?.removeOnGlobalFocusChangeListener(focusListener)
            registeredObserver = null
        }

        viewport.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = registerFocusListener()

            override fun onViewDetachedFromWindow(view: View) = unregisterFocusListener()
        })
        registerFocusListener()
    }
}
