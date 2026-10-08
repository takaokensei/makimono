package zechs.drive.stream.ui.series

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.ShortcutDetails
import zechs.drive.stream.data.model.Starred
import zechs.drive.stream.ui.series.adapter.SeriesEpisodeItem

class PrimaryEpisodeSelectionTest {

    @Test
    fun `unwatched primary action skips extras and sorts story episodes`() {
        val selected = PrimaryEpisodeSelection.select(
            listOf(
                episode("opening", "Show - OP1.mkv"),
                episode("ep2", "Show - 02.mkv"),
                episode("ova", "Show OVA 01.mkv"),
                episode("ep1", "Show - 01.mkv")
            )
        )

        assertEquals("ep1", selected?.file?.id)
    }

    @Test
    fun `partially watched story episode wins over earlier unwatched episode and extra progress`() {
        val selected = PrimaryEpisodeSelection.select(
            listOf(
                episode("opening", "Show - OP1.mkv", progress = 35),
                episode("ep2", "Show - 02.mkv", progress = 0),
                episode("ep1", "Show - 01.mkv", progress = 72)
            )
        )

        assertEquals("ep1", selected?.file?.id)
    }

    @Test
    fun `all watched story episodes fall back to first story episode`() {
        val selected = PrimaryEpisodeSelection.select(
            listOf(
                episode("opening", "Show - OP1.mkv", progress = 0),
                episode("ep2", "Show - 02.mkv", progress = 100),
                episode("ep1", "Show - 01.mkv", progress = 100)
            )
        )

        assertEquals("ep1", selected?.file?.id)
    }

    @Test
    fun `extras are used only when there are no story episodes`() {
        val selected = PrimaryEpisodeSelection.select(
            listOf(
                episode("ending", "Show - ED2.mkv"),
                episode("opening", "Show - OP1.mkv")
            )
        )

        assertEquals("opening", selected?.file?.id)
    }

    @Test
    fun `empty list has no primary episode`() {
        assertNull(PrimaryEpisodeSelection.select(emptyList()))
    }

    private fun episode(id: String, name: String, progress: Int = 0) = SeriesEpisodeItem(
        file = DriveFile(
            id = id,
            name = name,
            size = null,
            mimeType = "video/mp4",
            iconLink = null,
            shortcutDetails = ShortcutDetails(),
            starred = Starred.UNSTARRED
        ),
        displayTitle = name,
        episodeNumber = null,
        durationText = "24 min",
        audioBadgeText = "",
        progressPercent = progress,
        watchedDuration = 0L,
        totalDuration = 0L
    )
}
