package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.ui.player.PlaybackPlaylistPolicy

class PlaybackPlaylistPolicyTest {
    @Test
    fun mergePageDeduplicatesAndKeepsNaturalEpisodeOrder() {
        val existing = listOf(
            PlaylistItem("ep10", "Show S01E10.mkv", "old-thumbnail"),
            PlaylistItem("ep1", "Show S01E01.mkv")
        )
        val nextPage = listOf(
            PlaylistItem("ep10", "Show S01E10.mkv", "fresh-thumbnail"),
            PlaylistItem("ep2", "Show S01E02.mkv")
        )

        val merged = PlaybackPlaylistPolicy.merge(existing, nextPage)

        assertEquals(listOf("ep1", "ep2", "ep10"), merged.map { it.fileId })
        assertEquals("fresh-thumbnail", merged.last().thumbnailLink)
    }

    @Test
    fun mergeSubtitlesDeduplicatesDriveItemsAcrossPages() {
        val first = SubtitleItem("sub1", "Show.E01.pt-BR.srt")
        val second = SubtitleItem("sub2", "Show.E02.pt-BR.srt")

        assertEquals(listOf(first, second), PlaybackPlaylistPolicy.mergeSubtitles(
            listOf(first), listOf(first, second)
        ))
    }
}
