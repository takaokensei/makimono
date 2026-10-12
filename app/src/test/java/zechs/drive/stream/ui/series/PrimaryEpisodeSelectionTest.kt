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

    @Test
    fun `formatContinueCta shows remaining minutes for in-progress episode`() {
        val ep5 = episode(
            id = "ep5",
            name = "Show - 05.mkv",
            progress = 50,
            episodeNumber = 5,
            watchedDuration = 12 * 60_000L,
            totalDuration = 24 * 60_000L
        )
        assertEquals("Continuar E5 · faltam 12 min", PrimaryEpisodeSelection.formatContinueCta(ep5, isMovie = false))
    }

    @Test
    fun `formatContinueCta shows start label for unwatched episode and movie`() {
        val ep1 = episode(
            id = "ep1",
            name = "Show - 01.mkv",
            progress = 0,
            episodeNumber = 1
        )
        assertEquals("Assistir E1", PrimaryEpisodeSelection.formatContinueCta(ep1, isMovie = false))
        assertEquals("Assistir filme", PrimaryEpisodeSelection.formatContinueCta(ep1, isMovie = true))
    }

    @Test
    fun `formatContinueSubtitle formats remaining time MM SS for series and movies`() {
        val ep5 = episode(
            id = "ep5",
            name = "Show - 05.mkv",
            progress = 50,
            episodeNumber = 5,
            watchedDuration = 12 * 60_000L,
            totalDuration = 24 * 60_000L
        )
        assertEquals("Ep. 5 • 12:00 restante", PrimaryEpisodeSelection.formatContinueSubtitle(ep5, isMovie = false))
        assertEquals("Filme • 12:00 restante", PrimaryEpisodeSelection.formatContinueSubtitle(ep5, isMovie = true))
    }

    private fun episode(
        id: String,
        name: String,
        progress: Int = 0,
        episodeNumber: Int? = null,
        watchedDuration: Long = 0L,
        totalDuration: Long = 0L
    ) = SeriesEpisodeItem(
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
        episodeNumber = episodeNumber,
        durationText = "24 min",
        audioBadgeText = "",
        progressPercent = progress,
        watchedDuration = watchedDuration,
        totalDuration = totalDuration
    )
}
