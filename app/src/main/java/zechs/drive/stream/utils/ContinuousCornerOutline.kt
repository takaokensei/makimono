package zechs.drive.stream.utils

import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Convex, continuous (superellipse) outline for premium card and panel clipping.
 * Android 11+ receives a sampled superellipse path; older devices gracefully use
 * the platform round-rect outline while preserving clipping and elevation shadows.
 */
class ContinuousCornerOutline(
    private val radiusPx: Float,
    private val exponent: Double = 4.0
) : ViewOutlineProvider() {

    override fun getOutline(view: View, outline: Outline) {
        val bounds = Rect(0, 0, view.width, view.height)
        if (bounds.isEmpty) {
            outline.setEmpty()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            outline.setPath(superellipsePath(bounds, radiusPx, exponent))
        } else {
            outline.setRoundRect(bounds, radiusPx.coerceAtMost(bounds.height() / 2f))
        }
    }

    private fun superellipsePath(bounds: Rect, requestedRadius: Float, power: Double): Path {
        val radius = requestedRadius.coerceAtMost(minOf(bounds.width(), bounds.height()) / 2f)
        val path = Path()
        val corners = listOf(
            Corner(bounds.left + radius, bounds.top + radius, -PI, -PI / 2.0),
            Corner(bounds.right - radius, bounds.top + radius, -PI / 2.0, 0.0),
            Corner(bounds.right - radius, bounds.bottom - radius, 0.0, PI / 2.0),
            Corner(bounds.left + radius, bounds.bottom - radius, PI / 2.0, PI)
        )

        corners.forEachIndexed { index, corner ->
            for (step in 0..SAMPLES_PER_CORNER) {
                if (index > 0 && step == 0) continue
                val fraction = step.toDouble() / SAMPLES_PER_CORNER
                val angle = corner.start + (corner.end - corner.start) * fraction
                val x = corner.centerX + signedPower(cos(angle), 2.0 / power) * radius
                val y = corner.centerY + signedPower(sin(angle), 2.0 / power) * radius
                if (index == 0 && step == 0) path.moveTo(x.toFloat(), y.toFloat())
                else path.lineTo(x.toFloat(), y.toFloat())
            }
        }
        path.close()
        return path
    }

    private fun signedPower(value: Double, power: Double): Double =
        if (value < 0.0) -(-value).pow(power) else value.pow(power)

    private data class Corner(
        val centerX: Float,
        val centerY: Float,
        val start: Double,
        val end: Double
    )

    private companion object {
        const val SAMPLES_PER_CORNER = 12
    }
}
