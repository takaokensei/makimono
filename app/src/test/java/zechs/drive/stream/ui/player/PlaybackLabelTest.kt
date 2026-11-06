package zechs.drive.stream.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.R

class PlaybackLabelTest {

    @Test
    fun movieReturnsFullMovieRegardlessOfParsedEpisodeNumber() {
        assertEquals(R.string.playback_movie_full, PlaybackLabel.subtitleRes(isMovie = true, episode = null))
        assertEquals("Filme Completo", PlaybackLabel.format(isMovie = true, episode = null))

        // Even if year or extraneous number like 2026 or 1 is parsed, isMovie preserves movie label
        assertEquals(R.string.playback_movie_full, PlaybackLabel.subtitleRes(isMovie = true, episode = 2026))
        assertEquals("Filme Completo", PlaybackLabel.format(isMovie = true, episode = 2026))

        assertEquals(R.string.playback_movie_full, PlaybackLabel.subtitleRes(isMovie = true, episode = 1))
        assertEquals("Filme Completo", PlaybackLabel.format(isMovie = true, episode = 1))
    }

    @Test
    fun tvShowEpisodeFormatsWithTwoDigits() {
        assertEquals(R.string.playback_episode_number, PlaybackLabel.subtitleRes(isMovie = false, episode = 1))
        assertEquals("Episódio 01", PlaybackLabel.format(isMovie = false, episode = 1))

        assertEquals(R.string.playback_episode_number, PlaybackLabel.subtitleRes(isMovie = false, episode = 12))
        assertEquals("Episódio 12", PlaybackLabel.format(isMovie = false, episode = 12))
    }

    @Test
    fun tvShowWithoutEpisodeNumberFallsBackToFullMovie() {
        assertEquals(R.string.playback_movie_full, PlaybackLabel.subtitleRes(isMovie = false, episode = null))
        assertEquals("Filme Completo", PlaybackLabel.format(isMovie = false, episode = null))

        assertEquals(R.string.playback_movie_full, PlaybackLabel.subtitleRes(isMovie = false, episode = 0))
        assertEquals("Filme Completo", PlaybackLabel.format(isMovie = false, episode = 0))
    }

    @Test
    fun sanitizeEpisodeTitleRedundantTitlesCollapsed() {
        // Redundant "Episódio 02" when epNumber = 2 collapses to not distinct
        val (title1, isDistinct1) = PlaybackLabel.sanitizeEpisodeTitle("Episódio 02", 2, isMovie = false)
        assertFalse(isDistinct1)
        assertEquals("", title1)

        // Mojibake "Epis?dio 02" when epNumber = 2 is sanitized and collapsed
        val (title2, isDistinct2) = PlaybackLabel.sanitizeEpisodeTitle("Epis?dio 02", 2, isMovie = false)
        assertFalse(isDistinct2)
        assertEquals("", title2)

        // Simple "02" when epNumber = 2 collapses
        val (title3, isDistinct3) = PlaybackLabel.sanitizeEpisodeTitle("02", 2, isMovie = false)
        assertFalse(isDistinct3)
        assertEquals("", title3)
    }

    @Test
    fun sanitizeEpisodeTitleExtractsDistinctTitles() {
        // "Episódio 01 - The Day I Became a Shinigami" -> "The Day I Became a Shinigami"
        val (title1, isDistinct1) = PlaybackLabel.sanitizeEpisodeTitle(
            "Episódio 01 - The Day I Became a Shinigami",
            1,
            isMovie = false
        )
        assertTrue(isDistinct1)
        assertEquals("The Day I Became a Shinigami", title1)

        // Raw torrent filename with group tags and extensions stripped
        val (title2, isDistinct2) = PlaybackLabel.sanitizeEpisodeTitle(
            "[shincaps] BLEACH Sennen Kessen-hen ~Kashin-tan~ - 01 (BS 1440x1080 MPEG2 AAC).ts",
            1,
            isMovie = false
        )
        assertTrue(isDistinct2)
        assertEquals("BLEACH Sennen Kessen-hen ~Kashin-tan~", title2)
    }
}
