package zechs.drive.stream.utils

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.annotation.OptIn
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi

/**
 * Normalizes ExoPlayer cues for a stable, Netflix-like presentation:
 * bottom dialogue at a fixed line, top signs preserved, overlapping bottom lines merged.
 */
@OptIn(UnstableApi::class)
object SubtitleCueComposer {

    private const val DEFAULT_BOTTOM_LINE = 0.95f

    fun compose(cues: List<Cue>, style: SubtitleStyle): List<Cue> {
        if (cues.isEmpty()) return emptyList()

        val topSigns = mutableListOf<Cue>()
        val bottom = mutableListOf<Cue>()

        for (cue in cues) {
            if (isTopSign(cue)) {
                topSigns.add(cue)
            } else {
                bottom.add(enhanceTypography(cue, style))
            }
        }

        val bottomOut = when {
            bottom.isEmpty() -> emptyList()
            style.mergeOverlappingBottomCues && bottom.size > 1 -> listOf(mergeBottomCues(bottom, style))
            else -> bottom.map { pinToPosition(it, style.positionFraction) }
        }

        return topSigns + bottomOut
    }

    /**
     * Composes dual subtitles simultaneously (primary PT-BR on bottom + secondary original above).
     */
    fun composeDual(
        primaryCues: List<Cue>,
        secondaryCues: List<Cue>,
        style: SubtitleStyle
    ): List<Cue> {
        val primary = compose(primaryCues, style)
        if (secondaryCues.isEmpty()) return primary

        val secondaryStyle = style.copy(
            positionFraction = (style.positionFraction - 0.08f).coerceAtLeast(0.12f),
            bold = false
        )
        val secondary = compose(secondaryCues, secondaryStyle)
        return primary + secondary
    }

    private fun isTopSign(cue: Cue): Boolean =
        cue.lineType == Cue.LINE_TYPE_FRACTION &&
            cue.line in 0.0f..0.35f &&
            cue.lineAnchor == Cue.ANCHOR_TYPE_START

    private fun pinToPosition(cue: Cue, position: Float): Cue =
        cue.buildUpon()
            .setLine(position, Cue.LINE_TYPE_FRACTION)
            .setLineAnchor(Cue.ANCHOR_TYPE_END)
            .build()

    private fun enhanceTypography(cue: Cue, style: SubtitleStyle): Cue {
        val builder = cue.buildUpon()
        builder.setLine(style.positionFraction, Cue.LINE_TYPE_FRACTION)
        builder.setLineAnchor(Cue.ANCHOR_TYPE_END)

        val text = cue.text ?: return builder.build()
        if (text.isEmpty()) return builder.build()

        val styledText: CharSequence = if (style.bold) {
            try {
                val spannable = SpannableStringBuilder.valueOf(text)
                val hasBold = spannable?.getSpans(0, spannable.length, StyleSpan::class.java)
                    ?.any { it.style == Typeface.BOLD } == true
                if (!hasBold && spannable != null) {
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        spannable.length,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    )
                }
                spannable ?: text
            } catch (_: Exception) {
                text
            }
        } else {
            text
        }
        builder.setText(styledText)
        return builder.build()
    }

    private fun mergeBottomCues(bottom: List<Cue>, style: SubtitleStyle): Cue {
        val lines = linkedSetOf<String>()
        for (cue in bottom) {
            val raw = cue.text?.toString()?.trim().orEmpty()
            if (raw.isBlank()) continue
            raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { lines.add(it) }
        }
        val combined = lines.joinToString("\n")
        val base = bottom.maxByOrNull { it.text?.length ?: 0 } ?: bottom.first()
        val builder = base.buildUpon()
            .setLine(style.positionFraction, Cue.LINE_TYPE_FRACTION)
            .setLineAnchor(Cue.ANCHOR_TYPE_END)
        if (combined.isNotBlank()) {
            val styledText: CharSequence = if (style.bold) {
                try {
                    val spannable = SpannableStringBuilder(combined)
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        spannable.length,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    )
                    spannable
                } catch (_: Exception) {
                    combined
                }
            } else {
                combined
            }
            builder.setText(styledText)
        }
        return builder.build()
    }
}
