package zechs.drive.stream.ui.player

import org.junit.Assert.assertEquals
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
}
