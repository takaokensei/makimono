package zechs.drive.stream.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleElevationPolicyTest {

    @Test
    fun osdVisible_elevatesSubtitlesAboveBottomCapsule() {
        val config = SubtitleElevationPolicy.resolve(osdVisible = true)
        assertEquals(0.22f, config.exoPaddingFraction, 0.001f)
        assertEquals(140, config.mpvMarginY)
    }

    @Test
    fun osdHidden_restoresStandardSubtitlePosition() {
        val config = SubtitleElevationPolicy.resolve(osdVisible = false)
        assertEquals(0.08f, config.exoPaddingFraction, 0.001f)
        assertEquals(30, config.mpvMarginY)
    }
}
