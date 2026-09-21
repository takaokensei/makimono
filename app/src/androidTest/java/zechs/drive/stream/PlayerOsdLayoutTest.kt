package zechs.drive.stream

import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import zechs.drive.stream.ui.player.PlayerQuickOptions

class PlayerOsdLayoutTest {
    @Test fun primaryGroupsDoNotOverlapOnPhoneOrTv() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Fullscreen_KodiEstuary)
            val density = context.resources.displayMetrics.density
            for (widthDp in listOf(360, 640, 960)) {
                val root = LayoutInflater.from(context).inflate(R.layout.player_control_view, null)
                PlayerQuickOptions.bind(root)
                val width = (widthDp * density).toInt()
                val height = (640 * density).toInt()
                // The first pass selects compact or wide constraints; the second resolves them.
                repeat(2) {
                    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, width, height)
                }
                val groups = listOf(R.id.leftActions, R.id.mainControls, R.id.rightActions).map {
                    root.findViewById<View>(it).let { v -> Rect(v.left, v.top, v.right, v.bottom) }
                }
                for (i in groups.indices) for (j in i + 1 until groups.size) {
                    assertFalse("OSD overlaps at ${widthDp}dp: ${groups[i]} / ${groups[j]}", Rect.intersects(groups[i], groups[j]))
                }
                for (id in listOf(R.id.btnSpeed, R.id.btnResize, R.id.btnChapter, R.id.btnInfo, R.id.btnRotate)) {
                    assertEquals(View.GONE, root.findViewById<View>(id).visibility)
                }
                assertTrue(root.findViewById<View>(R.id.btnMoreOptions).isFocusable)
            }
        }
    }
}
