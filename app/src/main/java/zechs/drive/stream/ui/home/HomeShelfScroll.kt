package zechs.drive.stream.ui.home

import android.view.View
import androidx.core.widget.NestedScrollView

/** Keeps the active shelf title and focused card together below the fixed Home toolbar. */
internal object HomeShelfScroll {
    fun alignmentDelta(
        headingTop: Int,
        focusedItemTop: Int,
        visibleTop: Int,
        topSpacing: Int
    ): Int {
        val desiredDelta = headingTop - visibleTop - topSpacing
        val maxDeltaBeforeItemLeavesViewport = (focusedItemTop - visibleTop).coerceAtLeast(0)
        return desiredDelta.coerceIn(0, maxDeltaBeforeItemLeavesViewport)
    }

    fun alignHeading(scroll: NestedScrollView, heading: View?, focusedItem: View) {
        val safeSpacing = (12 * focusedItem.resources.displayMetrics.density).toInt()
        focusedItem.postDelayed({
            val sectionHeading = heading ?: return@postDelayed
            if (!scroll.isShown || !focusedItem.isShown || !focusedItem.hasFocus() || !sectionHeading.isShown) {
                return@postDelayed
            }

            val scrollLocation = IntArray(2)
            val headingLocation = IntArray(2)
            val focusedLocation = IntArray(2)
            scroll.getLocationOnScreen(scrollLocation)
            sectionHeading.getLocationOnScreen(headingLocation)
            focusedItem.getLocationOnScreen(focusedLocation)

            val visibleTop = scrollLocation[1] + scroll.paddingTop
            val delta = alignmentDelta(
                headingTop = headingLocation[1],
                focusedItemTop = focusedLocation[1],
                visibleTop = visibleTop,
                topSpacing = safeSpacing
            )
            if (delta > 0) scroll.smoothScrollBy(0, delta)
        }, 180L)
    }
}
