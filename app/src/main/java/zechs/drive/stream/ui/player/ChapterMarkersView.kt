package zechs.drive.stream.ui.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

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
