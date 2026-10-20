package zechs.drive.stream.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.model.PlaylistItem

class SmartDownloadPolicyTest {

    private val ep1 = PlaylistItem(
        fileId = "file_ep1",
        title = "Sousou no Frieren - 01.mkv",
        thumbnailLink = "https://example.com/thumb1.jpg"
    )

    private val ep2 = PlaylistItem(
        fileId = "file_ep2",
        title = "Sousou no Frieren - 02.mkv",
        thumbnailLink = "https://example.com/thumb2.jpg"
    )

    @Test
    fun smartDownloadsDisabled_returnsNone() {
        val action = SmartDownloadPolicy.evaluate(
            isSmartDownloadsEnabled = false,
            isPlaybackFinished = true,
            currentEpisodeFileId = ep1.fileId,
            isCurrentEpisodeDownloaded = true,
            nextEpisode = ep2,
            isNextEpisodeDownloaded = false
        )
        assertTrue(action is SmartDownloadPolicy.Action.None)
    }

    @Test
    fun playbackNotFinished_returnsNone() {
        val action = SmartDownloadPolicy.evaluate(
            isSmartDownloadsEnabled = true,
            isPlaybackFinished = false,
            currentEpisodeFileId = ep1.fileId,
            isCurrentEpisodeDownloaded = true,
            nextEpisode = ep2,
            isNextEpisodeDownloaded = false
        )
        assertTrue(action is SmartDownloadPolicy.Action.None)
    }

    @Test
    fun currentEpisodeNotDownloaded_returnsNone() {
        val action = SmartDownloadPolicy.evaluate(
            isSmartDownloadsEnabled = true,
            isPlaybackFinished = true,
            currentEpisodeFileId = ep1.fileId,
            isCurrentEpisodeDownloaded = false,
            nextEpisode = ep2,
            isNextEpisodeDownloaded = false
        )
        assertTrue(action is SmartDownloadPolicy.Action.None)
    }

    @Test
    fun currentEpisodeDownloadedAndFinished_withNextNotDownloaded_executesBoth() {
        val action = SmartDownloadPolicy.evaluate(
            isSmartDownloadsEnabled = true,
            isPlaybackFinished = true,
            currentEpisodeFileId = ep1.fileId,
            isCurrentEpisodeDownloaded = true,
            nextEpisode = ep2,
            isNextEpisodeDownloaded = false
        )
        assertTrue(action is SmartDownloadPolicy.Action.Execute)
        val exec = action as SmartDownloadPolicy.Action.Execute
        assertEquals("file_ep1", exec.deleteFileId)
        assertEquals(ep2, exec.enqueueNextEpisode)
    }

    @Test
    fun currentEpisodeDownloadedAndFinished_withNextAlreadyDownloaded_onlyDeletesCurrent() {
        val action = SmartDownloadPolicy.evaluate(
            isSmartDownloadsEnabled = true,
            isPlaybackFinished = true,
            currentEpisodeFileId = ep1.fileId,
            isCurrentEpisodeDownloaded = true,
            nextEpisode = ep2,
            isNextEpisodeDownloaded = true
        )
        assertTrue(action is SmartDownloadPolicy.Action.Execute)
        val exec = action as SmartDownloadPolicy.Action.Execute
        assertEquals("file_ep1", exec.deleteFileId)
        assertEquals(null, exec.enqueueNextEpisode)
    }

    @Test
    fun currentEpisodeDownloadedAndFinished_withNoNextEpisode_onlyDeletesCurrent() {
        val action = SmartDownloadPolicy.evaluate(
            isSmartDownloadsEnabled = true,
            isPlaybackFinished = true,
            currentEpisodeFileId = ep1.fileId,
            isCurrentEpisodeDownloaded = true,
            nextEpisode = null,
            isNextEpisodeDownloaded = false
        )
        assertTrue(action is SmartDownloadPolicy.Action.Execute)
        val exec = action as SmartDownloadPolicy.Action.Execute
        assertEquals("file_ep1", exec.deleteFileId)
        assertEquals(null, exec.enqueueNextEpisode)
    }

    @Test
    fun currentEpisodeBlankId_andNoNext_returnsNone() {
        val action = SmartDownloadPolicy.evaluate(
            isSmartDownloadsEnabled = true,
            isPlaybackFinished = true,
            currentEpisodeFileId = "",
            isCurrentEpisodeDownloaded = true,
            nextEpisode = null,
            isNextEpisodeDownloaded = false
        )
        assertTrue(action is SmartDownloadPolicy.Action.None)
    }
}
