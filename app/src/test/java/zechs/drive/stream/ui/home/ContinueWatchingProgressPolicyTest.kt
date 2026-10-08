package zechs.drive.stream.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueWatchingProgressPolicyTest {
    @Test
    fun unknownOrInvalidDurationDoesNotClaimTheEpisodeIsNearlyComplete() {
        assertEquals(
            ContinueWatchingProgressPolicy.Remaining.Unknown,
            ContinueWatchingProgressPolicy.remaining(watchedDurationMs = 12_000L, totalDurationMs = 0L)
        )
        assertEquals(
            ContinueWatchingProgressPolicy.Remaining.Unknown,
            ContinueWatchingProgressPolicy.remaining(watchedDurationMs = -1L, totalDurationMs = 60_000L)
        )
    }

    @Test
    fun remainingTimeUsesHumanReadableCeilingWithoutShowingZeroMinutes() {
        assertEquals(
            ContinueWatchingProgressPolicy.Remaining.LessThanMinute,
            ContinueWatchingProgressPolicy.remaining(watchedDurationMs = 59_500L, totalDurationMs = 60_000L)
        )
        assertEquals(
            ContinueWatchingProgressPolicy.Remaining.Minutes(1),
            ContinueWatchingProgressPolicy.remaining(watchedDurationMs = 60_000L, totalDurationMs = 120_000L)
        )
        assertEquals(
            ContinueWatchingProgressPolicy.Remaining.Minutes(2),
            ContinueWatchingProgressPolicy.remaining(watchedDurationMs = 0L, totalDurationMs = 60_001L)
        )
    }

    @Test
    fun completedEpisodeIsNotDescribedAsAlmostComplete() {
        assertEquals(
            ContinueWatchingProgressPolicy.Remaining.Complete,
            ContinueWatchingProgressPolicy.remaining(watchedDurationMs = 120_000L, totalDurationMs = 120_000L)
        )
        assertEquals(
            ContinueWatchingProgressPolicy.Remaining.Complete,
            ContinueWatchingProgressPolicy.remaining(watchedDurationMs = 130_000L, totalDurationMs = 120_000L)
        )
    }
}
