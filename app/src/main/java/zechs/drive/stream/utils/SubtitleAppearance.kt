package zechs.drive.stream.utils

import android.util.TypedValue
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.google.android.exoplayer2.ui.SubtitleView

object SubtitleAppearance {

    fun applyToExo(subtitleView: SubtitleView?, style: SubtitleStyle) {
        if (subtitleView == null) return
        val captionStyle = CaptionStyleCompat(
            style.foregroundColor,
            style.backgroundColor,
            android.graphics.Color.TRANSPARENT,
            style.edgeType,
            style.edgeColor,
            style.typeface()
        )
        subtitleView.setStyle(captionStyle)
        subtitleView.setApplyEmbeddedStyles(false)
        subtitleView.setApplyEmbeddedFontSizes(false)
        subtitleView.setBottomPaddingFraction(style.bottomPaddingFraction)
        if (style.sizeSp > 0f) {
            subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, style.sizeSp)
        }
    }
    
    @Suppress("UNUSED_PARAMETER")
    fun applyPositionToExo(subtitleView: SubtitleView?, style: SubtitleStyle) {
        if (subtitleView == null) return
        // Position is handled through Cue composition in SubtitleCueComposer
        // This is a placeholder for any direct view-level positioning if needed
    }

    fun mpvFontSize(sizeSp: Float): Int = when {
        sizeSp <= 16f -> 38
        sizeSp <= 20f -> 48
        sizeSp <= 24f -> 58
        else -> 68
    }
}
