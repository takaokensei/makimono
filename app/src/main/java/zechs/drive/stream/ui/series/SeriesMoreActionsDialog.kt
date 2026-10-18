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
        val panelWidth = dp(340)
        val rowHeight = dp(56)
        val panelHeight = dp(20) + ((rowHeight + dp(4)) * 3)

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = ContextCompat.getDrawable(context, R.drawable.bg_context_menu_surface)
            elevation = dp(20).toFloat()
            clipToPadding = false
            clipChildren = false
            isFocusable = true
            isFocusableInTouchMode = true
            contentDescription = context.getString(R.string.series_more_actions)
        }

        var popup: PopupWindow? = null
        fun dismissWithAnimation() {
            panel.animate()
                .alpha(0f)
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(140)
                .withEndAction { popup?.dismiss() }
                .start()
        }

        fun addAction(label: String, iconRes: Int, action: () -> Unit) {
            val item = TextView(context).apply {
                text = label
                androidx.core.widget.TextViewCompat.setTextAppearance(
                    this,
                    R.style.TextAppearance_Makimono_TV_Body
                )
                setTextColor(ContextCompat.getColor(context, R.color.obsidian_text_primary))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
                compoundDrawablePadding = dp(14)
                val icon = ContextCompat.getDrawable(context, iconRes)?.mutate()?.apply {
                    setBounds(0, 0, dp(22), dp(22))
                    setTint(ContextCompat.getColor(context, R.color.obsidian_text_secondary))
                }
                setCompoundDrawables(icon, null, null, null)
                minHeight = rowHeight
                maxLines = 2
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                background = ContextCompat.getDrawable(context, R.drawable.bg_context_menu_item)
                setOnFocusChangeListener { v, hasFocus ->
                    icon?.setTint(
                        ContextCompat.getColor(
                            context,
                            if (hasFocus) R.color.obsidian_accent_primary else R.color.obsidian_text_secondary
                        )
                    )
                    v.animate()
                        .scaleX(if (hasFocus) 1.035f else 1.0f)
                        .scaleY(if (hasFocus) 1.035f else 1.0f)
                        .translationZ(if (hasFocus) dp(4).toFloat() else 0f)
                        .setDuration(160)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
                setOnClickListener {
                    dismissWithAnimation()
                    action()
                }
            }
            panel.addView(
                item,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight).apply {
                    bottomMargin = dp(4)
                }
            )
        }

        addAction(context.getString(R.string.series_action_trailer), R.drawable.ic_trailer_24, onTrailer)
        addAction(
            context.getString(if (isFollowed) R.string.action_unfollow else R.string.action_follow),
            if (isFollowed) R.drawable.ic_star_filled_24 else R.drawable.ic_star_outline_24,
            onFollowToggle
        )
        addAction(context.getString(R.string.series_action_mark_watched), R.drawable.ic_check_24, onMarkWatched)

        popup = PopupWindow(
            panel,
            panelWidth,
            panelHeight,
            true
        ).apply {
            animationStyle = android.R.style.Animation_Dialog
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

        panel.alpha = 0f
        panel.scaleX = 0.92f
        panel.scaleY = 0.92f
        panel.pivotX = (panelWidth / 2).toFloat()
        panel.pivotY = 0f

        popup.showAtLocation(
            focusAnchor.rootView,
            Gravity.TOP or Gravity.START,
            leftOnScreen - rootLocation[0],
            topOnScreen.coerceAtLeast(visibleFrame.top) - rootLocation[1]
        )
        panel.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        panel.requestFocus()
        (panel.getChildAt(0) as? View)?.requestFocus()
    }
}
