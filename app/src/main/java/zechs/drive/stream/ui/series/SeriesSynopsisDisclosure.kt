package zechs.drive.stream.ui.series

import android.graphics.Rect
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import androidx.core.widget.NestedScrollView
import zechs.drive.stream.utils.SingleTapActivation

/** Adds an explicit, accessible disclosure only when the collapsed synopsis is actually clipped. */
internal class SeriesSynopsisDisclosure(
    private val scrollView: NestedScrollView,
    private val synopsis: TextView,
    private val toggle: TextView,
    private val fallbackFocus: View,
    private val expandLabel: CharSequence,
    private val collapseLabel: CharSequence,
    private val onAvailabilityChanged: (Boolean) -> Unit = {}
) {
    private val collapsedMaxLines = synopsis.maxLines
        .takeIf { it in 1 until Int.MAX_VALUE }
        ?: DEFAULT_COLLAPSED_MAX_LINES
    private var expanded = false
    private var isTruncated = false
    private var scrollYBeforeExpansion: Int? = null

    init {
        toggle.visibility = View.GONE
        SingleTapActivation.install(toggle)
        toggle.setOnClickListener {
            if (!isTruncated) return@setOnClickListener
            if (!expanded) scrollYBeforeExpansion = scrollView.scrollY
            expanded = !expanded
            applyExpandedState()
        }
    }

    fun bind(text: CharSequence?) {
        val disclosureHadFocus = toggle.hasFocus()
        expanded = false
        isTruncated = false
        val formatted = SeriesMediaMetadataPolicy.formatHeroSynopsis(text?.toString())
        if (disclosureHadFocus) fallbackFocus.requestFocus()
        if (formatted.isNullOrBlank()) {
            synopsis.text = null
            synopsis.visibility = View.GONE
            toggle.visibility = View.GONE
            onAvailabilityChanged(false)
            return
        }

        synopsis.visibility = View.VISIBLE
        synopsis.maxLines = collapsedMaxLines
        synopsis.ellipsize = null
        synopsis.text = formatted
        toggle.visibility = View.GONE
        onAvailabilityChanged(false)
    }

    private fun inspectCollapsedLayout() {
        val layout = synopsis.layout ?: run {
            synopsis.doOnNextLayout { synopsis.post { inspectCollapsedLayout() } }
            return
        }
        val lastVisibleLine = (layout.lineCount - 1).coerceAtLeast(0)
        isTruncated = SynopsisDisclosurePolicy.isTruncated(
            lineCount = layout.lineCount,
            ellipsisCount = layout.getEllipsisCount(lastVisibleLine),
            maxLines = collapsedMaxLines
        )
        val newVisibility = if (isTruncated) View.VISIBLE else View.GONE
        if (toggle.visibility != newVisibility) {
            toggle.visibility = newVisibility
            (toggle.parent as? View)?.requestLayout()
            scrollView.requestLayout()
        }
        toggle.text = expandLabel
        toggle.contentDescription = expandLabel
        onAvailabilityChanged(isTruncated)
    }

    private fun applyExpandedState() {
        synopsis.maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines
        synopsis.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
        toggle.text = if (expanded) collapseLabel else expandLabel
        toggle.contentDescription = toggle.text
        synopsis.requestLayout()
        (synopsis.parent as? View)?.requestLayout()
        scrollView.requestLayout()
        if (expanded) {
            toggle.post { scrollToggleIntoViewport() }
        } else {
            toggle.post {
                scrollYBeforeExpansion?.let { scrollView.smoothScrollTo(0, it) }
                scrollYBeforeExpansion = null
            }
        }
    }

    private fun scrollToggleIntoViewport() {
        val bounds = Rect(0, 0, toggle.width, toggle.height)
        scrollView.offsetDescendantRectToMyCoords(toggle, bounds)
        val safeMargin = (16 * scrollView.resources.displayMetrics.density).toInt()
        val visibleBottom = scrollView.height - scrollView.paddingBottom - safeMargin
        val visibleTop = scrollView.paddingTop
        val scrollDelta = when {
            bounds.bottom > visibleBottom -> bounds.bottom - visibleBottom
            bounds.top < visibleTop -> bounds.top - visibleTop
            else -> 0
        }
        if (scrollDelta != 0) scrollView.smoothScrollBy(0, scrollDelta)
    }

    private companion object {
        const val DEFAULT_COLLAPSED_MAX_LINES = 3
    }
}

internal object SynopsisDisclosurePolicy {
    fun isTruncated(lineCount: Int, ellipsisCount: Int, maxLines: Int): Boolean =
        lineCount > maxLines || (lineCount == maxLines && ellipsisCount > 0)
}
