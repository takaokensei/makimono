package zechs.drive.stream.data.translation

import org.junit.Assert.*
import org.junit.Test

class AssTagPreservingCodecTest {

    @Test
    fun testExtractAndReconstructAssPreservesTagsAndStyles() {
        val originalAss = listOf(
            "[Script Info]",
            "Title: Test Anime",
            "ScriptType: v4.00+",
            "",
            "[V4+ Styles]",
            "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding",
            "Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1",
            "",
            "[Events]",
            "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
            "Dialogue: 0,0:01:10.00,0:01:13.50,Default,,0,0,0,,{\\an8\\pos(960,120)}Top sign: School Gate",
            "Dialogue: 0,0:01:15.00,0:01:18.00,Default,,0,0,0,,Hello!\\NHow are you today?",
            "Dialogue: 0,0:01:20.00,0:01:22.00,Default,,0,0,0,,{\\i1}Italic whispering{\\i0}"
        )

        val cues = AssTagPreservingCodec.extractFromAss(originalAss)
        assertEquals(3, cues.size)

        // Cue 1: Top sign
        assertEquals(1, cues[0].id)
        assertTrue(cues[0].cleanText.contains("Top sign: School Gate"))
        assertTrue(cues[0].tagsMap.values.any { it.contains("\\an8\\pos(960,120)") })

        // Cue 2: Split line
        assertEquals(2, cues[1].id)
        assertEquals("Hello!\nHow are you today?", cues[1].cleanText)

        // Simulated translations
        val translations = mapOf(
            1 to "Placa superior: Portão da Escola",
            2 to "Olá!\nComo você está hoje?",
            3 to "Sussurro em itálico"
        )

        val reconstructed = AssTagPreservingCodec.reconstructAss(originalAss, translations)

        assertTrue(reconstructed.contains("[Script Info]"))
        assertTrue(reconstructed.contains("[V4+ Styles]"))
        // Check that tag \an8\pos(960,120) was re-injected in front of the Portuguese text
        assertTrue(reconstructed.contains("{\\an8\\pos(960,120)}Placa superior: Portão da Escola"))
        // Check that \n was turned into \N for ASS standard
        assertTrue(reconstructed.contains("Olá!\\NComo você está hoje?"))
    }

    @Test
    fun testExtractAndReconstructSrt() {
        val srtContent = """
            1
            00:00:10,000 --> 00:00:13,000
            <i>Look over there!</i>
            
            2
            00:00:15,500 --> 00:00:18,000
            It's a beautiful night.
        """.trimIndent()

        val cues = AssTagPreservingCodec.extractFromSrt(srtContent)
        assertEquals(2, cues.size)

        val translations = mapOf(
            1 to "[[TAG_0]]Olhe para lá![[TAG_1]]",
            2 to "Está uma noite linda."
        )

        val reconstructed = AssTagPreservingCodec.reconstructSrt(cues, translations)
        assertTrue(reconstructed.contains("00:00:10,000 --> 00:00:13,000"))
        assertTrue(reconstructed.contains("<i>Olhe para lá!</i>"))
        assertTrue(reconstructed.contains("Está uma noite linda."))
    }
}
