package zechs.drive.stream.utils

import android.content.Context
import android.graphics.Color
import android.view.accessibility.CaptioningManager
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat

/**
 * Crunchyroll StartupActivity pattern:
 * Reads Android TV / Android OS `CaptioningManager` accessibility settings to provide
 * system-aligned default subtitle sizing and edge/color styling when a profile has not
 * saved a custom `SubtitleStyle` override yet.
 */
@OptIn(UnstableApi::class)
object SystemCaptioningBridge {

    fun resolveDefaultSubtitleStyle(
        context: Context?,
        fallbackSizeSp: Float = 20f
    ): SubtitleStyle {
        if (context == null) return SubtitleStyle(sizeSp = fallbackSizeSp)
        return try {
            val cm = context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
                ?: return SubtitleStyle(sizeSp = fallbackSizeSp)
            if (!cm.isEnabled) {
                return SubtitleStyle(sizeSp = fallbackSizeSp)
            }
            val fontScale = cm.fontScale.coerceIn(0.5f, 2.0f)
            val scaledSizeSp = (fallbackSizeSp * fontScale).coerceIn(14f, 32f)
            val userStyle = cm.userStyle
            val compat = CaptionStyleCompat.createFromCaptionStyle(userStyle)

            val fg = if (userStyle.hasForegroundColor()) compat.foregroundColor else Color.WHITE
            val bg = if (userStyle.hasBackgroundColor()) compat.backgroundColor else Color.TRANSPARENT
            val edgeType = if (userStyle.hasEdgeType()) compat.edgeType else CaptionStyleCompat.EDGE_TYPE_OUTLINE
            val edgeColor = if (userStyle.hasEdgeColor()) compat.edgeColor else Color.BLACK

            SubtitleStyle(
                sizeSp = scaledSizeSp,
                foregroundColor = fg,
                backgroundColor = bg,
                edgeType = edgeType,
                edgeColor = edgeColor
            )
        } catch (e: Exception) {
            AppLog.w("SystemCaptioningBridge", "Failed to read CaptioningManager defaults: ${e.message}")
            SubtitleStyle(sizeSp = fallbackSizeSp)
        }
    }
}
