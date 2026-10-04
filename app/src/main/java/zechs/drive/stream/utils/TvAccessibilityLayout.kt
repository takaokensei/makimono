package zechs.drive.stream.utils

import android.content.Context
import android.widget.HorizontalScrollView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt
import zechs.drive.stream.R

/** Keeps the Home hero actions inside the visible hero when system text is enlarged. */
object TvAccessibilityLayout {
    fun adaptFilesChrome(
        context: Context,
        navigationRail: View?,
        navigationRows: List<View?>,
        searchContainer: View?,
        searchInput: EditText?,
        searchActions: List<View?>
    ) {
        val fontScale = context.resources.configuration.fontScale
        if (fontScale < 1.3f) return

        val density = context.resources.displayMetrics.density
        fun dp(value: Float) = (value * density).roundToInt()

        navigationRail?.let { rail ->
            rail.layoutParams = rail.layoutParams.apply {
                width = dp(filesRailWidthDp(fontScale).toFloat())
            }
        }
        val rowMinimum = dp(if (fontScale >= 1.8f) 68f else 56f)
        navigationRows.filterNotNull().forEach { row ->
            row.layoutParams = row.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            row.minimumHeight = rowMinimum
            if (row is ViewGroup) {
                (0 until row.childCount).map { row.getChildAt(it) }
                    .filterIsInstance<TextView>()
                    .forEach { label ->
                        label.isSingleLine = false
                        label.maxLines = 2
                        label.ellipsize = android.text.TextUtils.TruncateAt.END
                        val params = label.layoutParams as? LinearLayout.LayoutParams
                        if (params != null) {
                            params.width = 0
                            params.weight = 1f
                            label.layoutParams = params
                        }
                    }
            }
        }

        searchContainer?.let { container ->
            container.layoutParams = container.layoutParams.apply {
                if (navigationRail != null) width = dp(filesSearchWidthDp(fontScale).toFloat())
                height = dp(if (fontScale >= 1.8f) 52f else 44f)
            }
        }
        if (navigationRail != null) {
            val actionSize = dp(if (fontScale >= 1.8f) 48f else 44f)
            searchActions.filterNotNull().forEach { action ->
                action.layoutParams = action.layoutParams.apply {
                    width = actionSize
                    height = actionSize
                }
            }
        }
        searchInput?.apply {
            maxLines = 1
            setHint(filesSearchHintResId(fontScale, hasNavigationRail = navigationRail != null))
        }
    }

    fun filesRailWidthDp(fontScale: Float): Int = when {
        fontScale >= 1.8f -> 224
        fontScale >= 1.3f -> 168
        else -> 128
    }

    fun filesSearchWidthDp(fontScale: Float): Int = when {
        fontScale >= 1.8f -> 420
        fontScale >= 1.3f -> 360
        else -> 300
    }

    fun filesSearchHintResId(fontScale: Float, hasNavigationRail: Boolean): Int =
        if (hasNavigationRail && fontScale >= 1.8f) {
            R.string.search_files_hint_large
        } else {
            R.string.search_files_hint
        }

    fun adaptDetailsSynopsis(context: Context, synopsis: TextView?) {
        val fontScale = context.resources.configuration.fontScale
        if (fontScale < 1.3f || synopsis == null) return

        val density = context.resources.displayMetrics.density
        synopsis.maxLines = detailsSynopsisMaxLines(fontScale)
        synopsis.ellipsize = android.text.TextUtils.TruncateAt.END
        synopsis.maxWidth = (680f * density).roundToInt()
    }

    fun detailsSynopsisMaxLines(fontScale: Float): Int = when {
        fontScale >= 1.8f -> 8
        fontScale >= 1.3f -> 5
        else -> 3
    }

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

    fun profileCatalogColumns(fontScale: Float, isWallpaperView: Boolean): Int =
        if (isWallpaperView || fontScale >= 1.3f) 2 else 4

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
