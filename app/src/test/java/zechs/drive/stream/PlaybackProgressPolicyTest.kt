package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.utils.PlaybackProgressPolicy

/**
 * FEAT-06: Comprehensive unit test suite for PlaybackProgressPolicy.
 * Tests 0%, 10%, 95%, completion window, autoplay advance, countdown, cancellation,
 * and duration resolution across ExoPlayer and MPV unified contract.
 */
class PlaybackProgressPolicyTest {

    @Test
    fun `calculateProgress handles zero or negative duration safely`() {
        assertEquals(0, PlaybackProgressPolicy.calculateProgress(0L, 0L))
        assertEquals(0, PlaybackProgressPolicy.calculateProgress(5000L, 0L))
        assertEquals(0, PlaybackProgressPolicy.calculateProgress(-1000L, 10000L))
        assertEquals(0, PlaybackProgressPolicy.calculateProgress(1000L, -10000L))
    }

    @Test
    fun `calculateProgress computes accurate percentages and clamps at 100`() {
        assertEquals(0, PlaybackProgressPolicy.calculateProgress(0L, 100000L))
        assertEquals(10, PlaybackProgressPolicy.calculateProgress(10000L, 100000L))
        assertEquals(50, PlaybackProgressPolicy.calculateProgress(50000L, 100000L))
        assertEquals(95, PlaybackProgressPolicy.calculateProgress(95000L, 100000L))
        assertEquals(100, PlaybackProgressPolicy.calculateProgress(100000L, 100000L))
        assertEquals(100, PlaybackProgressPolicy.calculateProgress(120000L, 100000L))
    }

    @Test
    fun `shouldSave only accepts meaningful playback at or above 10 percent`() {
        assertFalse(PlaybackProgressPolicy.shouldSave(0L, 100000L))
        assertFalse(PlaybackProgressPolicy.shouldSave(5000L, 100000L)) // 5%
        assertFalse(PlaybackProgressPolicy.shouldSave(9000L, 100000L)) // 9%
        assertTrue(PlaybackProgressPolicy.shouldSave(10000L, 100000L)) // 10%
        assertTrue(PlaybackProgressPolicy.shouldSave(80000L, 100000L)) // 80%
    }

    @Test
    fun `isFinished triggers at 95 percent`() {
        assertFalse(PlaybackProgressPolicy.isFinished(94000L, 100000L))
        assertTrue(PlaybackProgressPolicy.isFinished(95000L, 100000L))
        assertTrue(PlaybackProgressPolicy.isFinished(100000L, 100000L))
    }

    @Test
    fun `isFinished triggers within near-end window for long videos`() {
        // Video is 20 min (1_200_000 ms), 10 seconds remaining (99.1% but even at 94% with < 15s)
        val duration = 600_000L // 10 minutes
        // 14 seconds remaining: 586_000 ms (progress = 97%)
        assertTrue(PlaybackProgressPolicy.isFinished(586_000L, duration))

        // 20 seconds remaining (not <= 15s, and progress = 580_000 / 600_000 = 96.6% >= 95% so finished)
        assertTrue(PlaybackProgressPolicy.isFinished(580_000L, duration))

        // 30 seconds remaining on a 300_000ms video (progress = 270_000 / 300_000 = 90% -> false)
        assertFalse(PlaybackProgressPolicy.isFinished(270_000L, 300_000L))
    }

    @Test
    fun `evaluateAutoplay dismisses card when controls are locked or duration too short or no next episode`() {
        val decisionLocked = PlaybackProgressPolicy.evaluateAutoplay(
            positionMs = 95_000L,
            durationMs = 100_000L,
            hasNextEpisode = true,
            isCanceled = false,
            controlsLocked = true
        )
        assertEquals(PlaybackProgressPolicy.AutoplayDecision.DismissCard, decisionLocked)

        val decisionShort = PlaybackProgressPolicy.evaluateAutoplay(
            positionMs = 55_000L,
            durationMs = 59_000L, // <= 60s
            hasNextEpisode = true,
            isCanceled = false,
            controlsLocked = false
        )
        assertEquals(PlaybackProgressPolicy.AutoplayDecision.DismissCard, decisionShort)

        val decisionNoNext = PlaybackProgressPolicy.evaluateAutoplay(
            positionMs = 95_000L,
            durationMs = 100_000L,
            hasNextEpisode = false,
            isCanceled = false,
            controlsLocked = false
        )
        assertEquals(PlaybackProgressPolicy.AutoplayDecision.DismissCard, decisionNoNext)
    }

    @Test
    fun `evaluateAutoplay triggers PlayNext when remaining time is 1 second or less`() {
        val decisionEnd = PlaybackProgressPolicy.evaluateAutoplay(
            positionMs = 99_500L,
            durationMs = 100_000L, // 500ms remaining
            hasNextEpisode = true,
            isCanceled = false,
            controlsLocked = false
        )
        assertEquals(PlaybackProgressPolicy.AutoplayDecision.PlayNext, decisionEnd)

        val decisionOver = PlaybackProgressPolicy.evaluateAutoplay(
            positionMs = 101_000L,
            durationMs = 100_000L,
            hasNextEpisode = true,
            isCanceled = false,
            controlsLocked = false
        )
        assertEquals(PlaybackProgressPolicy.AutoplayDecision.PlayNext, decisionOver)
    }

    @Test
    fun `evaluateAutoplay respects user cancellation and suppresses PlayNext`() {
        val decision = PlaybackProgressPolicy.evaluateAutoplay(
            positionMs = 99_500L,
            durationMs = 100_000L,
            hasNextEpisode = true,
            isCanceled = true,
            controlsLocked = false
        )
        assertEquals(PlaybackProgressPolicy.AutoplayDecision.None, decision)
    }

    @Test
    fun `evaluateAutoplay returns ShowCountdown with accurate seconds within trigger window`() {
        // 8 seconds remaining: should return ShowCountdown(countdownSeconds = 8)
        val decision = PlaybackProgressPolicy.evaluateAutoplay(
            positionMs = 92_000L,
            durationMs = 100_000L,
            hasNextEpisode = true,
            isCanceled = false,
            controlsLocked = false
        )
        assertTrue(decision is PlaybackProgressPolicy.AutoplayDecision.ShowCountdown)
        val countdown = decision as PlaybackProgressPolicy.AutoplayDecision.ShowCountdown
        assertEquals(8, countdown.countdownSeconds)
        assertEquals(8000L, countdown.remainingMs)
    }

    @Test
    fun `evaluateAutoplay dismisses card when playback is outside trigger window`() {
        // 30 seconds remaining: too early to show countdown
        val decision = PlaybackProgressPolicy.evaluateAutoplay(
            positionMs = 70_000L,
            durationMs = 100_000L,
            hasNextEpisode = true,
            isCanceled = false,
            controlsLocked = false
        )
        assertEquals(PlaybackProgressPolicy.AutoplayDecision.DismissCard, decision)
    }

    @Test
    fun `resolveWatchedDuration prioritizes real duration over fallback`() {
        assertEquals(1_440_000L, PlaybackProgressPolicy.resolveWatchedDuration(1_440_000L))
        assertEquals(1_800_000L, PlaybackProgressPolicy.resolveWatchedDuration(1_800_000L))
        assertEquals(
            PlaybackProgressPolicy.DEFAULT_FALLBACK_DURATION_MS,
            PlaybackProgressPolicy.resolveWatchedDuration(null)
        )
        assertEquals(
            PlaybackProgressPolicy.DEFAULT_FALLBACK_DURATION_MS,
            PlaybackProgressPolicy.resolveWatchedDuration(0L)
        )
        assertEquals(
            PlaybackProgressPolicy.DEFAULT_FALLBACK_DURATION_MS,
            PlaybackProgressPolicy.resolveWatchedDuration(-1L)
        )
    }
}
