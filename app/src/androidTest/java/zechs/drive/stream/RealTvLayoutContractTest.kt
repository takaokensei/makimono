package zechs.drive.stream

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Geometry checks against the actual TV configuration, without catalog fixtures. */
@RunWith(AndroidJUnit4::class)
class RealTvLayoutContractTest {
    @Test fun contentStartsAfterCollapsedRailAndItsSafeInset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resources = context.resources
        val gutter = resources.getDimensionPixelSize(R.dimen.home_content_margin)
        val rail = resources.getDimensionPixelSize(R.dimen.tv_nav_rail_width)
        val inset = resources.getDimensionPixelSize(R.dimen.tv_rail_safe_inset)
        val density = resources.displayMetrics.density
        assertTrue("TV gutter must leave space for the rail, overscan and a focus ring", gutter >= inset + rail + 16 * density)
        assertTrue("TV lateral overscan", inset >= 48 * density - 1)
        assertTrue("TV vertical overscan", resources.getDimensionPixelSize(R.dimen.tv_rail_safe_vertical) >= 27 * density - 1)
    }

    @Test fun pairingQrHasAtLeast360DpOfUnfilteredImageSpace() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_DriveStream_Obsidian)
            val root = LayoutInflater.from(context).inflate(R.layout.fragment_sign_in, null)
            val metrics = context.resources.displayMetrics
            root.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
            val frame = root.findViewById<View>(R.id.flQrContainer)
            assertTrue("QR width >= 360dp", frame.width - frame.paddingLeft - frame.paddingRight >= 360 * metrics.density - 1)
            assertTrue("QR height >= 360dp", frame.height - frame.paddingTop - frame.paddingBottom >= 360 * metrics.density - 1)
            assertTrue("Pairing content fits on the TV screen", frame.bottom <= metrics.heightPixels)
        }
    }
}
