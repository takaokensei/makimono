package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.model.WatchList

class WatchListTest {

    @Test
    fun progressIsSafeForInvalidAndOutOfRangeDurations() {
        assertEquals(0, watch(total = 0L, watched = 1L).watchProgress())
        assertEquals(0, watch(total = 100L, watched = -1L).watchProgress())
        assertEquals(100, watch(total = 100L, watched = 200L).watchProgress())
    }

    @Test
    fun ninetyFivePercentIsFinished() {
        assertTrue(watch(total = 100L, watched = 95L).hasFinished())
        assertFalse(watch(total = 100L, watched = 94L).hasFinished())
    }

    private fun watch(total: Long, watched: Long) = WatchList(
        name = "Episode 01.mkv",
        videoId = "video-1",
        watchedDuration = watched,
        totalDuration = total
    )
}
