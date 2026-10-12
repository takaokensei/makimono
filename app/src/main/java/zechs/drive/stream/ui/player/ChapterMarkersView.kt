package zechs.drive.stream.ui.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import zechs.drive.stream.utils.MatroskaChapterParser.ParsedChapter

data class ChapterMarker(
    val startMs: Long,
    val endMs: Long? = null,
    val color: Int = 0xCCFFFFFF.toInt()
)

data class MarkerBounds(
    val startX: Float,
    val endX: Float,
    val color: Int
)

data class ChapterMenuEntry(
    val id: String,
    val title: String,
    val timestampLabel: String,
    val startMs: Long,
    val isCurrent: Boolean,
    val isUserBookmark: Boolean = false
)

object ChapterMarkersPolicy {
    fun calculateBounds(
        widthPx: Int,
        durationMs: Long,
        markers: List<ChapterMarker>
    ): List<MarkerBounds> {
        if (widthPx <= 0 || durationMs <= 0 || markers.isEmpty()) {
            return emptyList()
        }
        return markers.map { marker ->
            val startRatio = (marker.startMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
            val startX = (startRatio * widthPx).toFloat()
            val endX = if (marker.endMs != null && marker.endMs > marker.startMs) {
                val endRatio = (marker.endMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
                (endRatio * widthPx).toFloat()
            } else {
                startX
            }
            MarkerBounds(startX = startX, endX = endX, color = marker.color)
        }
    }

    /**
     * Merges container chapters and user-added bookmarks in chronological order,
     * marking the active entry at [currentPositionMs] (P-16).
     */
    fun buildChapterMenuEntries(
        chapters: List<ParsedChapter>,
        userBookmarksMs: List<Long> = emptyList(),
        currentPositionMs: Long
    ): List<ChapterMenuEntry> {
        data class RawEntry(
            val title: String,
            val startMs: Long,
            val isBookmark: Boolean
        )
        val combined = buildList {
            chapters.forEachIndexed { idx, ch ->
                add(
                    RawEntry(
                        title = ch.title.ifBlank { "Capítulo ${idx + 1}" },
                        startMs = ch.startTimeMs.coerceAtLeast(0L),
                        isBookmark = false
                    )
                )
            }
            userBookmarksMs.sorted().forEachIndexed { idx, ms ->
                val cleanMs = ms.coerceAtLeast(0L)
                add(
                    RawEntry(
                        title = "Marcador ${idx + 1} (${formatTimestamp(cleanMs)})",
                        startMs = cleanMs,
                        isBookmark = true
                    )
                )
            }
        }.sortedBy { it.startMs }

        if (combined.isEmpty()) return emptyList()

        val activeIndex = combined.indexOfLast { currentPositionMs >= it.startMs }.coerceAtLeast(0)
        return combined.mapIndexed { index, raw ->
            ChapterMenuEntry(
                id = if (raw.isBookmark) "bookmark_$index" else "chapter_$index",
                title = raw.title,
                timestampLabel = formatTimestamp(raw.startMs),
                startMs = raw.startMs,
                isCurrent = index == activeIndex,
                isUserBookmark = raw.isBookmark
            )
        }
    }

    /**
     * Adds a user bookmark at [positionMs] unless one already exists within [dedupeWindowMs] (P-16).
     */
    fun addBookmark(
        existingBookmarksMs: List<Long>,
        positionMs: Long,
        dedupeWindowMs: Long = 2_000L
    ): List<Long> {
        val clamped = positionMs.coerceAtLeast(0L)
        if (existingBookmarksMs.any { kotlin.math.abs(it - clamped) <= dedupeWindowMs }) {
            return existingBookmarksMs.sorted()
        }
        return (existingBookmarksMs + clamped).sorted()
    }

    fun formatTimestamp(timeMs: Long): String {
        val totalSec = (timeMs.coerceAtLeast(0L) / 1000L)
        val hours = totalSec / 3600L
        val minutes = (totalSec % 3600L) / 60L
        val seconds = totalSec % 60L
        return if (hours > 0L) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}

/**
 * Custom overlay view rendering chapter and AniSkip markers (2dp x 8dp ticks)
 * along the player progress scrubber (T4.2).
 */
class ChapterMarkersView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val intervalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var durationMs: Long = 0L
    private var markers: List<ChapterMarker> = emptyList()

    fun setMarkers(durationMs: Long, markers: List<ChapterMarker>) {
        this.durationMs = durationMs
        this.markers = markers
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || durationMs <= 0 || markers.isEmpty()) return

        val density = resources.displayMetrics.density
        val tickWidth = 2f * density
        val tickHeight = 8f * density
        val top = (height - tickHeight) / 2f
        val bottom = top + tickHeight

        val boundsList = ChapterMarkersPolicy.calculateBounds(width, durationMs, markers)
        for (b in boundsList) {
            if (b.endX > b.startX + tickWidth) {
                // Highlight interval (such as AniSkip opening/ending span)
                intervalPaint.color = (b.color and 0x00FFFFFF) or 0x4D000000 // 30% alpha
                canvas.drawRect(b.startX, top, b.endX, bottom, intervalPaint)
            }
            // Draw 2dp tick mark at start
            tickPaint.color = b.color
            canvas.drawRect(
                b.startX - (tickWidth / 2f),
                top,
                b.startX + (tickWidth / 2f),
                bottom,
                tickPaint
            )
        }
    }
}
