package zechs.drive.stream.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalculatePlaybackProgressUseCaseTest {

    private lateinit var useCase: CalculatePlaybackProgressUseCase

    @Before
    fun setUp() {
        useCase = CalculatePlaybackProgressUseCase()
    }

    @Test
    fun zeroOrNegativeTotalDurationReturnsUnwatched() {
        val result = useCase(watchedDurationMs = 5000L, totalDurationMs = 0L)
        assertEquals(0f, result.progressPercentage, 0.01f)
        assertTrue(result.isUnwatched)
        assertFalse(result.isCompleted)
    }

    @Test
    fun zeroWatchedDurationReturnsUnwatched() {
        val result = useCase(watchedDurationMs = 0L, totalDurationMs = 60000L)
        assertEquals(0f, result.progressPercentage, 0.01f)
        assertEquals(60000L, result.remainingMs)
        assertTrue(result.isUnwatched)
        assertFalse(result.isCompleted)
    }

    @Test
    fun calculatesPartialProgressCorrectly() {
        // 30 seconds out of 60 seconds = 50%
        val result = useCase(watchedDurationMs = 30000L, totalDurationMs = 60000L)
        assertEquals(50f, result.progressPercentage, 0.01f)
        assertEquals(30000L, result.remainingMs)
        assertFalse(result.isUnwatched)
        assertFalse(result.isCompleted)
    }

    @Test
    fun marksAsCompletedWhenMeetingThreshold() {
        // 92% watched (above default 90% threshold)
        val result = useCase(watchedDurationMs = 92000L, totalDurationMs = 100000L)
        assertEquals(92f, result.progressPercentage, 0.01f)
        assertEquals(8000L, result.remainingMs)
        assertTrue(result.isCompleted)
        assertFalse(result.isUnwatched)
    }

    @Test
    fun marksAsUnwatchedWhenBelowThreshold() {
        // 3% watched (below default 5% threshold)
        val result = useCase(watchedDurationMs = 3000L, totalDurationMs = 100000L)
        assertEquals(3f, result.progressPercentage, 0.01f)
        assertTrue(result.isUnwatched)
        assertFalse(result.isCompleted)
    }

    @Test
    fun clampsWatchedDurationWhenExceedingTotal() {
        val result = useCase(watchedDurationMs = 150000L, totalDurationMs = 100000L)
        assertEquals(100000L, result.watchedDurationMs)
        assertEquals(100f, result.progressPercentage, 0.01f)
        assertEquals(0L, result.remainingMs)
        assertTrue(result.isCompleted)
    }
}
