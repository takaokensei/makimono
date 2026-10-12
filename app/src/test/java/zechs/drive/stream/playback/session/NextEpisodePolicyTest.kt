package zechs.drive.stream.playback.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.model.PlaylistItem

class NextEpisodePolicyTest {

    @Test
    fun `calculateRemainingSeconds rounds up partial seconds and clamps at zero`() {
        assertEquals(10, NextEpisodePolicy.calculateRemainingSeconds(9_500L))
        assertEquals(1, NextEpisodePolicy.calculateRemainingSeconds(100L))
        assertEquals(0, NextEpisodePolicy.calculateRemainingSeconds(0L))
        assertEquals(0, NextEpisodePolicy.calculateRemainingSeconds(-500L))
    }

    @Test
    fun `calculateCountdownProgress maps remaining seconds to 0-100 percentage`() {
        assertEquals(100, NextEpisodePolicy.calculateCountdownProgress(10, 10))
        assertEquals(50, NextEpisodePolicy.calculateCountdownProgress(5, 10))
        assertEquals(0, NextEpisodePolicy.calculateCountdownProgress(0, 10))
        assertEquals(100, NextEpisodePolicy.calculateCountdownProgress(15, 10))
    }

    @Test
    fun `shouldAdvanceImmediately triggers at 1 second or less`() {
        assertFalse(NextEpisodePolicy.shouldAdvanceImmediately(remainingMs = 2_000L, remainingSeconds = 2))
        assertTrue(NextEpisodePolicy.shouldAdvanceImmediately(remainingMs = 1_000L, remainingSeconds = 1))
        assertTrue(NextEpisodePolicy.shouldAdvanceImmediately(remainingMs = 500L, remainingSeconds = 0))
    }

    @Test
    fun `shouldPromptStillWatching triggers after 3 consecutive autoplayed episodes`() {
        assertFalse(NextEpisodePolicy.shouldPromptStillWatching(0))
        assertFalse(NextEpisodePolicy.shouldPromptStillWatching(1))
        assertFalse(NextEpisodePolicy.shouldPromptStillWatching(2))
        assertTrue(NextEpisodePolicy.shouldPromptStillWatching(3))
        assertTrue(NextEpisodePolicy.shouldPromptStillWatching(4))
    }

    @Test
    fun `formatEpisodeBadge and formatCountdownLabel produce consistent UI strings`() {
        assertEquals("EPISÓDIO 5", NextEpisodePolicy.formatEpisodeBadge(5))
        assertEquals("PRÓXIMO EPISÓDIO", NextEpisodePolicy.formatEpisodeBadge(null))
        assertEquals("A SEGUIR • 8s", NextEpisodePolicy.formatCountdownLabel(8))
    }

    @Test
    fun `resolveNavigationControls hides drawer for movies or single item and dims disabled buttons`() {
        val ep1 = PlaylistItem("1", "Ep 1")
        val ep2 = PlaylistItem("2", "Ep 2")

        val movieState = NextEpisodePolicy.resolveNavigationControls(
            isMovie = true,
            playlistSize = 3,
            prevEpisode = null,
            nextEpisode = null
        )
        assertFalse(movieState.showEpisodesDrawerButton)
        assertFalse(movieState.canSkipPrevious)
        assertFalse(movieState.canSkipNext)
        assertEquals(0.35f, movieState.prevButtonAlpha, 0.001f)

        val seriesMidState = NextEpisodePolicy.resolveNavigationControls(
            isMovie = false,
            playlistSize = 12,
            prevEpisode = ep1,
            nextEpisode = ep2
        )
        assertTrue(seriesMidState.showEpisodesDrawerButton)
        assertTrue(seriesMidState.canSkipPrevious)
        assertTrue(seriesMidState.canSkipNext)
        assertEquals(1.0f, seriesMidState.prevButtonAlpha, 0.001f)
        assertEquals(1.0f, seriesMidState.nextButtonAlpha, 0.001f)
    }
}
