package zechs.drive.stream.utils

import android.content.Context
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt
import zechs.drive.stream.R

/** Keeps the Home hero actions inside the visible hero when system text is enlarged. */
object TvAccessibilityLayout {
    fun adaptHomeHero(
        context: Context,
        hero: View?,
        primaryAction: View?,
        secondaryAction: View?
    ) {
        val scale = context.resources.configuration.fontScale.coerceIn(1f, 2f)
        if (scale <= 1f) return

        hero?.let {
            it.layoutParams = it.layoutParams.apply {
                height = (context.resources.getDimensionPixelSize(R.dimen.home_hero_height) * scale)
                    .roundToInt()
            }
        }

        val actionHeight = (context.resources.getDimensionPixelSize(R.dimen.home_hero_action_height) * scale)
            .roundToInt()
        listOfNotNull(primaryAction, secondaryAction).forEach { action ->
            action.layoutParams = action.layoutParams.apply { height = actionHeight }
        }
    }

    fun adaptSettingsRows(context: Context, rows: List<View?>) {
        if (context.resources.configuration.fontScale <= 1f) return

        val density = context.resources.displayMetrics.density
        val minimumHeight = (64f * density).roundToInt()
        val verticalPadding = (12f * density).roundToInt()
        rows.filterNotNull().forEach { row ->
            row.layoutParams = row.layoutParams.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            row.minimumHeight = minimumHeight
            if (row.paddingTop < verticalPadding || row.paddingBottom < verticalPadding) {
                row.setPadding(row.paddingLeft, verticalPadding, row.paddingRight, verticalPadding)
            }
        }
    }

    fun adaptProfileActions(context: Context, actions: List<View?>) {
        if (context.resources.configuration.fontScale <= 1f) return

        val density = context.resources.displayMetrics.density
        actions.filterNotNull().forEach { action ->
            val largestTextHeight = findTextViews(action)
                .maxOfOrNull { (it.paint.fontMetrics.descent - it.paint.fontMetrics.ascent).roundToInt() }
                ?: 0
            val minimumHeight = (48f * density).roundToInt()
            val contentHeight = largestTextHeight + (24f * density).roundToInt()
            action.layoutParams = action.layoutParams.apply {
                height = maxOf(minimumHeight, contentHeight)
            }
        }
    }

    fun adaptSeriesActions(
        context: Context,
        actionsViewport: HorizontalScrollView?,
        actionsRow: LinearLayout?,
        actions: List<View?>
    ) {
        val fontScale = context.resources.configuration.fontScale
        if (fontScale < 1.3f) return

        val density = context.resources.displayMetrics.density
        actionsViewport?.isFillViewport = true
        actionsRow?.apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT }
        }

        actions.filterNotNull().forEach { action ->
            val params = action.layoutParams as? ViewGroup.MarginLayoutParams
                ?: ViewGroup.MarginLayoutParams(action.layoutParams)
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.topMargin = (8f * density).roundToInt()
            params.marginStart = 0
            action.layoutParams = params
            action.minimumHeight = (52f * density).roundToInt()
            action.setPadding(
                action.paddingLeft,
                maxOf(action.paddingTop, (10f * density).roundToInt()),
                action.paddingRight,
                maxOf(action.paddingBottom, (10f * density).roundToInt())
            )
            setTextMaxLines(action, 2)
        }
    }

    fun gridColumnsForFontScale(baseColumns: Int, fontScale: Float): Int {
        if (fontScale < 1.3f) return baseColumns.coerceAtLeast(1)
        val reduction = if (fontScale >= 1.8f) 2 else 1
        return (baseColumns - reduction).coerceAtLeast(1)
    }

    private fun setTextMaxLines(view: View, maxLines: Int) {
        when (view) {
            is TextView -> {
                view.isSingleLine = false
                view.maxLines = maxLines
            }
            is ViewGroup -> (0 until view.childCount).forEach { setTextMaxLines(view.getChildAt(it), maxLines) }
        }
    }

    private fun findTextViews(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { findTextViews(view.getChildAt(it)) }
        else -> emptyList()
    }
}
