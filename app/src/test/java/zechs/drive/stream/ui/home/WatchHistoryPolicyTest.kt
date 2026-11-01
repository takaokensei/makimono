package zechs.drive.stream.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.data.model.WatchList

class WatchHistoryPolicyTest {
    @Test fun replayedOlderEntryComesBeforeNewerDatabaseRow() {
        val replayed = WatchList("BLEACH 01", "a", 10, 100, lastPlayedAt = 300, id = 1)
        val newerRow = WatchList("BLEACH 02", "b", 10, 100, lastPlayedAt = 200, id = 2)
        assertEquals(listOf("a", "b"), sortWatchHistory(listOf(newerRow, replayed)).map { it.videoId })
    }
    @Test fun identicalTimestampsUseStableDatabaseOrder() {
        val first = WatchList("BLEACH 01", "a", 10, 100, lastPlayedAt = 200, id = 1)
        val second = first.copy(videoId = "b", id = 2)
        assertEquals(listOf("b", "a"), sortWatchHistory(listOf(first, second)).map { it.videoId })
    }
    @Test fun filterKeepsPlaybackDateOrderAndDoesNotReclassifyCompletedItems() {
        val watching = WatchList("BLEACH 01", "a", 10, 100, lastPlayedAt = 300)
        val completed = watching.copy(videoId = "b", watchedDuration = 100, lastPlayedAt = 200)
        assertEquals(listOf("a"), filterWatchHistory(listOf(completed, watching), WatchHistoryFilter.IN_PROGRESS).map { it.videoId })
        assertEquals(listOf("b"), filterWatchHistory(listOf(watching, completed), WatchHistoryFilter.COMPLETED).map { it.videoId })
        assertEquals(listOf("a", "b"), filterWatchHistory(listOf(completed, watching), WatchHistoryFilter.ALL).map { it.videoId })
    }
}
