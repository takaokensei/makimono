package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.utils.EpisodeParser

class EpisodeParserTest {

    @Test
    fun parsesStandardFansubRelease() {
        val filename = "[SubsPlease] Sousou no Frieren - 01 (1080p) [9876FEDC].mkv"
        val parsed = EpisodeParser.parse(filename)

        assertEquals("Sousou no Frieren", parsed.showTitle)
        assertEquals(1.0, parsed.episode ?: 0.0, 0.001)
        assertEquals("01", parsed.episodeBadge)
        assertFalse(parsed.isSpecial)
    }

    @Test
    fun parsesSeasonEpisodeFormat() {
        val filename = "Attack on Titan S02E05 (1080p HEVC).mkv"
        val parsed = EpisodeParser.parse(filename)

        assertEquals("Attack on Titan", parsed.showTitle)
        assertEquals(2, parsed.season)
        assertEquals(5.0, parsed.episode ?: 0.0, 0.001)
        assertEquals("05", parsed.episodeBadge)
        assertTrue(parsed.episodeLabel.startsWith("S02E05"))
        assertFalse(parsed.isSpecial)
    }

    @Test
    fun parsesSeasonOneEpisodeFormat() {
        val filename = "Jujutsu Kaisen S01E12.mp4"
        val parsed = EpisodeParser.parse(filename)

        assertEquals("Jujutsu Kaisen", parsed.showTitle)
        assertEquals(1, parsed.season)
        assertEquals(12.0, parsed.episode ?: 0.0, 0.001)
        assertEquals("Ep. 12", parsed.episodeLabel)
        assertEquals("12", parsed.episodeBadge)
    }

    @Test
    fun parsesDecimalEpisodes() {
        val filename = "[Erai-raws] Mushoku Tensei - 12.5 (1080p).mkv"
        val parsed = EpisodeParser.parse(filename)

        assertEquals("Mushoku Tensei", parsed.showTitle)
        assertEquals(12.5, parsed.episode ?: 0.0, 0.001)
        assertEquals("12.5", parsed.episodeBadge)
    }

    @Test
    fun parsesSpecialOrOva() {
        val filename = "ReZero S01SP02 (1080p).mkv"
        val parsed = EpisodeParser.parse(filename)

        assertTrue(parsed.isSpecial)
        assertEquals(1, parsed.season)
        assertEquals(2.0, parsed.episode ?: 0.0, 0.001)
        assertEquals("SP", parsed.episodeBadge)
    }

    @Test
    fun parsesPromoCreditlessExtra() {
        val filename = "Chainsaw Man NCOP 01 (1080p).mkv"
        val parsed = EpisodeParser.parse(filename)

        assertTrue(parsed.isSpecial)
        assertEquals("OP", parsed.episodeBadge)
        assertEquals(1.0, parsed.episode ?: 0.0, 0.001)
    }

    @Test
    fun cleansShowTitleCorrectly() {
        val messyTitle = "[Judas] Kimetsu no Yaiba - Yuukaku-hen - 01 (1080p) [12345678]"
        val parsed = EpisodeParser.parse(messyTitle)

        assertEquals("Kimetsu no Yaiba - Yuukaku-hen", parsed.showTitle)
        assertEquals(1.0, parsed.episode ?: 0.0, 0.001)
    }

    @Test
    fun parsesSimpleNumberedFile() {
        val simple = "08.mkv"
        val parsed = EpisodeParser.parse(simple)

        assertEquals(8.0, parsed.episode ?: 0.0, 0.001)
        assertEquals("08", parsed.episodeBadge)
        assertFalse(parsed.isSpecial)
    }
}
