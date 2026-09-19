package zechs.drive.stream.utils

import android.graphics.Color
import android.graphics.Typeface
import com.google.android.exoplayer2.ui.CaptionStyleCompat

/**
 * Per-profile subtitle appearance and timing normalization preferences.
 */
data class SubtitleStyle(
    val fontFamily: SubtitleFontFamily = SubtitleFontFamily.SANS,
    val sizeSp: Float = 20f,
    val bold: Boolean = true,
    val italic: Boolean = false,
    val foregroundColor: Int = Color.WHITE,
    val edgeType: Int = CaptionStyleCompat.EDGE_TYPE_OUTLINE,
    val edgeColor: Int = Color.BLACK,
    val backgroundColor: Int = Color.TRANSPARENT,
    val bottomPaddingFraction: Float = 0.035f,
    /** Merge multiple simultaneous bottom cues (common in ASS) into one block. */
    val mergeOverlappingBottomCues: Boolean = true,
    /** Bridge small gaps between consecutive cues when converting ASS → SRT. */
    val bridgeGapMs: Long = 450L,
    /** Position of subtitles: 0.0 = top, 0.5 = center, 1.0 = bottom */
    val positionFraction: Float = 0.95f,
    /** Additional delay/advance for subtitles in milliseconds (positive = delay, negative = advance) */
    val subtitleDelayMs: Long = 0L
) {
    fun summaryLabel(): String {
        val sizeLabel = when {
            sizeSp <= 16.5f -> "Pequeno"
            sizeSp <= 20.5f -> "Médio"
            sizeSp <= 24.5f -> "Grande"
            else -> "Extra grande"
        }
        return "${fontFamily.displayName} • $sizeLabel • ${edgeTypeLabel()}"
    }

    private fun edgeTypeLabel(): String = when (edgeType) {
        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW -> "Sombra"
        CaptionStyleCompat.EDGE_TYPE_NONE -> "Sem contorno"
        CaptionStyleCompat.EDGE_TYPE_OUTLINE -> "Contorno"
        else -> "Caixa"
    }

    fun typeface(): Typeface {
        val base = when (fontFamily) {
            SubtitleFontFamily.SANS -> Typeface.SANS_SERIF
            SubtitleFontFamily.SERIF -> Typeface.SERIF
            SubtitleFontFamily.MONO -> Typeface.MONOSPACE
            SubtitleFontFamily.ROUNDED -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, style)
    }
}

enum class SubtitleFontFamily(val key: String, val displayName: String, val mpvFont: String) {
    SANS("sans", "Sans-serif", "sans-serif"),
    SERIF("serif", "Serif", "serif"),
    MONO("mono", "Monoespaçada", "monospace"),
    ROUNDED("rounded", "Arredondada", "sans-serif-medium");

    companion object {
        fun fromKey(key: String?): SubtitleFontFamily =
            entries.find { it.key == key } ?: SANS
    }
}
