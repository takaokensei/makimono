package zechs.drive.stream.utils

import android.os.Build
import android.text.Layout
import android.util.TypedValue
import android.widget.TextView

/**
 * Editorial typography policy based on Apple tvOS HIG and Material 3 Display standards.
 * Eliminates awkward line wraps, typographic orphans, and horizontal kerning issues
 * across display and hero titles.
 */
object TitleFormattingPolicy {

    /**
     * Prevents typographic orphans (single word stranded on a trailing line)
     * by inserting a non-breaking space before the final word when appropriate.
     */
    fun preventOrphans(rawTitle: String): String {
        val trimmed = rawTitle.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return ""

        val lastSpaceIndex = trimmed.lastIndexOf(' ')
        if (lastSpaceIndex == -1) return trimmed

        val lastWord = trimmed.substring(lastSpaceIndex + 1)
        // If the last word is 10 chars or fewer, tie it to the preceding word with non-breaking space
        return if (lastWord.length <= 10) {
            trimmed.substring(0, lastSpaceIndex) + "\u00A0" + lastWord
        } else {
            trimmed
        }
    }

    /**
     * Computes the optical tracking (letterSpacing in em) for the given font size in sp.
     * Large display headings require tighter tracking to avoid sprawling glyphs,
     * while standard text requires neutral tracking.
     */
    fun computeDisplayLetterSpacing(textSizeSp: Float): Float {
        return when {
            textSizeSp >= 36f -> -0.020f
            textSizeSp >= 28f -> -0.015f
            textSizeSp >= 20f -> -0.008f
            else -> 0.0f
        }
    }

    /**
     * Resolves the optimal hero title font size in SP based on title length
     * to prevent unneeded wrapping or awkward cutoffs in 10-foot TV layouts.
     */
    fun resolveHeroTitleSizeSp(title: String, isTv: Boolean): Float {
        val length = title.length
        return if (isTv) {
            when {
                length <= 22 -> 40f
                length <= 36 -> 32f
                length <= 50 -> 26f
                else -> 22f
            }
        } else {
            when {
                length <= 22 -> 30f
                length <= 36 -> 24f
                else -> 20f
            }
        }
    }

    /**
     * Applies optical display styling to a TextView representing a Hero or Featured title.
     */
    fun applyHeroTitleStyle(textView: TextView, rawTitle: String, isTv: Boolean = true) {
        val formattedTitle = preventOrphans(rawTitle)
        val targetSizeSp = resolveHeroTitleSizeSp(formattedTitle, isTv)
        val targetLetterSpacing = computeDisplayLetterSpacing(targetSizeSp)

        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, targetSizeSp)
        textView.letterSpacing = targetLetterSpacing
        textView.includeFontPadding = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textView.breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED
            textView.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("WrongConstant")
            textView.breakStrategy = 2
            textView.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }

        textView.text = formattedTitle
    }
}
