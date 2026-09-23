package zechs.drive.stream

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class SeriesDetailLayoutTest {
    @Test fun backdropAndScrimsShareBoundsWithSparseAndLongMetadata() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (widthDp in listOf(640, 960, 1280)) {
                val configuration = Configuration(instrumentation.targetContext.resources.configuration).apply {
                    orientation = Configuration.ORIENTATION_LANDSCAPE
                    screenWidthDp = widthDp
                    screenHeightDp = 540
                }
                val context = ContextThemeWrapper(instrumentation.targetContext.createConfigurationContext(configuration), R.style.Theme_DriveStream_KodiEstuary)
                val root = LayoutInflater.from(context).inflate(R.layout.fragment_series_detail, null)
                val density = context.resources.displayMetrics.density
                val width = (widthDp * density).toInt()
                val height = (540 * density).toInt()
                for (populated in listOf(false, true)) {
                    root.findViewById<TextView>(R.id.tvRomajiTitle).text = if (populated) "A long series title that spans multiple lines" else "Bakemonogatari"
                    root.findViewById<TextView>(R.id.tvSynopsis).apply {
                        visibility = if (populated) View.VISIBLE else View.GONE
                        text = "A long synopsis with real metadata. ".repeat(15)
                    }
                    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, width, height)
                    val backdrop = root.findViewById<View>(R.id.ivHeroBackdrop)
                    assertTrue(backdrop.height > 0)
                    for (id in listOf(R.id.viewHeroScrim, R.id.viewHeroVignette, R.id.viewDynamicColorTint)) {
                        val overlay = root.findViewById<View>(id)
                        assertEquals("Overlay top at $widthDp", backdrop.top, overlay.top)
                        assertEquals("Overlay bottom at $widthDp", backdrop.bottom, overlay.bottom)
                    }
                    val episodes = root.findViewById<View>(R.id.rvEpisodes)
                    assertTrue("Backdrop leaks behind episodes", backdrop.bottom <= episodes.top)
                    assertTrue(root.findViewById<TextView>(R.id.tvPrimaryActionTitle).text.isNotBlank())
                }
            }
        }
    }
}
