package zechs.drive.stream

import android.content.res.Configuration
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeroLayoutTest {
    @Test fun longMetadataKeepsBothActionsInsideHero() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val configuration = Configuration(instrumentation.targetContext.resources.configuration).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
                screenWidthDp = 960
                screenHeightDp = 540
            }
            val context = ContextThemeWrapper(instrumentation.targetContext.createConfigurationContext(configuration), R.style.Theme_DriveStream_KodiEstuary)
            val root = LayoutInflater.from(context).inflate(R.layout.fragment_home, null)
            val hero = root.findViewById<ViewGroup>(R.id.featuredHeroContainer)
            hero.visibility = View.VISIBLE
            root.findViewById<TextView>(R.id.tvFeaturedTitle).text = "Sousou no Frieren: Beyond Journey's End"
            root.findViewById<TextView>(R.id.tvFeaturedJapaneseTitle).text = "A long native title for this series"
            root.findViewById<TextView>(R.id.tvFeaturedSynopsis).text = "A complete synopsis with enough text to fill all three lines. ".repeat(8)
            root.findViewById<LinearLayout>(R.id.layoutFeaturedGenres).addView(TextView(context).apply {
                text = "Fantasy   Adventure   Drama"
                textSize = 12f
                setPadding(8, 8, 8, 8)
            })
            val density = context.resources.displayMetrics.density
            val width = (960 * density).toInt()
            val height = (540 * density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            for (id in listOf(R.id.btnFeaturedPlay, R.id.btnFeaturedInfo)) {
                val action = root.findViewById<View>(id)
                val bounds = Rect(0, 0, action.width, action.height)
                hero.offsetDescendantRectToMyCoords(action, bounds)
                assertTrue("Action clipped by hero: $bounds / ${hero.height}", bounds.height() > 0 && bounds.top >= 0 && bounds.bottom <= hero.height)
            }
        }
    }
}
