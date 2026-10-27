package zechs.drive.stream.utils

import androidx.media3.common.text.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleCueComposerTest {

    @Test
    fun testComposeDualStacksPrimaryAndSecondaryCues() {
        val primaryCue = Cue.Builder().setText("Legenda primária em português").build()
        val secondaryCue = Cue.Builder().setText("Original Japanese dialogue").build()
        val style = SubtitleStyle(positionFraction = 0.95f)

        val dualCues = SubtitleCueComposer.composeDual(
            primaryCues = listOf(primaryCue),
            secondaryCues = listOf(secondaryCue),
            style = style
        )

        assertEquals(2, dualCues.size)
        assertEquals("Legenda primária em português", dualCues[0].text?.toString())
        assertEquals("Original Japanese dialogue", dualCues[1].text?.toString())
        // Verify secondary is placed higher than primary
        assertTrue(dualCues[1].line < dualCues[0].line)
    }
}
