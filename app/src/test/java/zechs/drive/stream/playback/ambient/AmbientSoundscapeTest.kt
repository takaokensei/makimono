package zechs.drive.stream.playback.ambient

import org.junit.Assert.*
import org.junit.Test
import zechs.drive.stream.utils.NocturnalSchedule
import zechs.drive.stream.utils.NocturnalSanctuaryPolicy

class AmbientSoundscapeTest {

    @Test
    fun testOpusCanonicalChapterCountAndOrder() {
        val tracks = AmbientTrackCatalog.OPUS_CANONICAL_CHAPTERS
        assertEquals(15, tracks.size)
        assertEquals("Intro", tracks[0].title)
        assertEquals("Lack of Love", tracks[1].title)
        assertEquals("Solitude", tracks[2].title)
        assertEquals("Merry Christmas Mr. Lawrence", tracks[13].title)
        assertEquals("opus", tracks[14].title)

        // Verify timing order
        for (i in 0 until tracks.size - 1) {
            assertTrue(tracks[i].startMs <= tracks[i + 1].startMs)
        }
    }

    @Test
    fun testParseTimestampChaptersFromCustomContent() {
        val sampleCue = """
            00:00 Intro
            00:06 Lack of Love
            04:49 Solitude
            01:04:13 opus
        """.trimIndent()

        val parsed = AmbientTrackCatalog.parseTimestampChapters(sampleCue, "Ryuichi Sakamoto")
        assertEquals(4, parsed.size)
        assertEquals("Intro", parsed[0].title)
        assertEquals(0L, parsed[0].startMs)
        assertEquals(6_000L, parsed[0].endMs)

        assertEquals("Solitude", parsed[2].title)
        assertEquals(289_000L, parsed[2].startMs)

        assertEquals("opus", parsed[3].title)
        assertEquals(3853_000L, parsed[3].startMs)
    }

    @Test
    fun testNocturnalSanctuaryPolicySchedule() {
        // Automatic: active at 23:00, 02:00, 04:59
        assertTrue(NocturnalSanctuaryPolicy.isNocturnalActive(NocturnalSchedule.AUTOMATIC, currentHour = 23))
        assertTrue(NocturnalSanctuaryPolicy.isNocturnalActive(NocturnalSchedule.AUTOMATIC, currentHour = 2))
        assertTrue(NocturnalSanctuaryPolicy.isNocturnalActive(NocturnalSchedule.AUTOMATIC, currentHour = 4))

        // Automatic: inactive at 05:00, 12:00, 18:00
        assertFalse(NocturnalSanctuaryPolicy.isNocturnalActive(NocturnalSchedule.AUTOMATIC, currentHour = 5))
        assertFalse(NocturnalSanctuaryPolicy.isNocturnalActive(NocturnalSchedule.AUTOMATIC, currentHour = 14))

        // Always ON / OFF
        assertTrue(NocturnalSanctuaryPolicy.isNocturnalActive(NocturnalSchedule.ALWAYS_ON, currentHour = 14))
        assertFalse(NocturnalSanctuaryPolicy.isNocturnalActive(NocturnalSchedule.OFF, currentHour = 2))
    }
}
