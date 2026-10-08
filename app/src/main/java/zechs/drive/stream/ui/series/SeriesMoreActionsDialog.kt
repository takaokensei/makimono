package zechs.drive.stream.ui.series

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import zechs.drive.stream.R

/** Compact, D-pad-friendly action surface for secondary series actions. */
object SeriesMoreActionsDialog {
    fun show(
        context: Context,
        focusAnchor: View,
        isFollowed: Boolean,
        onTrailer: () -> Unit,
        onFollowToggle: () -> Unit,
        onMarkWatched: () -> Unit
    ) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val panelWidth = dp(320)
        val rowHeight = dp(64)
        val panelHeight = dp(16) + (rowHeight * 3)

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = ContextCompat.getDrawable(context, R.drawable.bg_context_menu_surface)
            elevation = dp(18).toFloat()
            clipToPadding = false
            clipChildren = false
            isFocusable = true
            isFocusableInTouchMode = true
            contentDescription = context.getString(R.string.series_more_actions)
        }

        var popup: PopupWindow? = null
        fun addAction(label: String, action: () -> Unit) {
            val item = TextView(context).apply {
                text = label
                textSize = 18f
                setTextColor(ContextCompat.getColor(context, R.color.obsidian_text_primary))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                minHeight = rowHeight
                maxLines = 2
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                background = ContextCompat.getDrawable(context, R.drawable.bg_context_menu_item)
                setOnClickListener {
                    popup?.dismiss()
                    action()
                }
            }
            panel.addView(
                item,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight)
            )
        }

        addAction(context.getString(R.string.series_action_trailer), onTrailer)
        addAction(
            context.getString(if (isFollowed) R.string.action_unfollow else R.string.action_follow),
            onFollowToggle
        )
        addAction(context.getString(R.string.series_action_mark_watched), onMarkWatched)

        popup = PopupWindow(
            panel,
            panelWidth,
            panelHeight,
            true
        ).apply {
            isOutsideTouchable = true
            isTouchable = true
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(20).toFloat()
            })
            elevation = dp(20).toFloat()
            setOnDismissListener { focusAnchor.requestFocus() }
        }

        // Align the panel's trailing edge with the action button; Android keeps it on-screen.
        val visibleFrame = Rect()
        focusAnchor.getWindowVisibleDisplayFrame(visibleFrame)
        val anchorLocation = IntArray(2).also(focusAnchor::getLocationOnScreen)
        val rootLocation = IntArray(2).also(focusAnchor.rootView::getLocationOnScreen)
        val popupHeight = panelHeight
        val belowTop = anchorLocation[1] + focusAnchor.height + dp(8)
        val aboveTop = anchorLocation[1] - popupHeight - dp(8)
        val topOnScreen = if (belowTop + popupHeight <= visibleFrame.bottom) belowTop else aboveTop
        val preferredLeft = maxOf(
            anchorLocation[0] + focusAnchor.width + dp(12),
            visibleFrame.right - dp(24) - panelWidth
        )
        val leftOnScreen = preferredLeft.coerceAtMost(visibleFrame.right - panelWidth - dp(8))
            .coerceAtLeast(visibleFrame.left)
        popup.showAtLocation(
            focusAnchor.rootView,
            Gravity.TOP or Gravity.START,
            leftOnScreen - rootLocation[0],
            topOnScreen.coerceAtLeast(visibleFrame.top) - rootLocation[1]
        )
        panel.requestFocus()
        (panel.getChildAt(0) as? View)?.requestFocus()
    }
}
